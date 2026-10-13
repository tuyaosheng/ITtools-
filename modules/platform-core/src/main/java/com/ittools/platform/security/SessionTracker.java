package com.ittools.platform.security;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线会话登记：同一学生账号再次登录时让旧会话下线（原平台“重复登录踢下线”）。
 * 被踢下线的会话编号保留 2 小时，旧电脑再次请求时返回明确的提示，而不是笼统的“未登录”。
 */
@Component
public class SessionTracker implements HttpSessionListener {
    private static final long KICK_MEMORY_MS = 2 * 3600_000L;
    public static final String USER_ATTR = "ittools.userId";

    private final Map<Long, Set<HttpSession>> byUser = new ConcurrentHashMap<>();
    private final Map<String, Long> kicked = new ConcurrentHashMap<>();

    public void register(Long userId, HttpSession session) {
        session.setAttribute(USER_ATTR, userId);
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /** 让该用户除 keep 以外的会话全部下线，返回被下线的会话（调用方可先读取其中的属性）。 */
    public List<HttpSession> others(Long userId, HttpSession keep) {
        List<HttpSession> out = new ArrayList<>();
        Set<HttpSession> set = byUser.get(userId);
        if (set == null) return out;
        for (HttpSession s : set) if (!s.getId().equals(keep.getId())) out.add(s);
        return out;
    }

    public void kick(HttpSession s) {
        kicked.put(s.getId(), System.currentTimeMillis());
        try {
            s.invalidate();
        } catch (IllegalStateException ignored) {
            // 已失效
        }
    }

    public boolean wasKicked(String sessionId) {
        long now = System.currentTimeMillis();
        kicked.values().removeIf(t -> now - t > KICK_MEMORY_MS);
        return sessionId != null && kicked.containsKey(sessionId);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession s = se.getSession();
        Object uid;
        try {
            uid = s.getAttribute(USER_ATTR);
        } catch (IllegalStateException e) {
            uid = null;
        }
        if (uid instanceof Long) {
            Set<HttpSession> set = byUser.get(uid);
            if (set != null) {
                set.removeIf(x -> x.getId().equals(s.getId()));
                if (set.isEmpty()) byUser.remove(uid);
            }
        } else {
            for (Set<HttpSession> set : byUser.values()) set.removeIf(x -> x.getId().equals(s.getId()));
        }
    }
}
