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
 * 在线会话登记：
 * - “新登录挤掉旧的”模式：同一学生账号再次登录时让旧会话下线（原平台“重复登录踢下线”）；
 * - “先登录的占用”模式：账号在另一台电脑（不同 IP）在线时拒绝新登录，同一台电脑可以重新登录；
 * - 老师“强制下线”：让某个学生的全部会话下线。
 * 被下线的会话编号保留 2 小时（连同原因），旧电脑再次请求时返回明确的提示，而不是笼统的“未登录”。
 */
@Component
public class SessionTracker implements HttpSessionListener {
    private static final long KICK_MEMORY_MS = 2 * 3600_000L;
    public static final String USER_ATTR = "ittools.userId";
    public static final String IP_ATTR = "ittools.loginIp";
    public static final String MSG_RELOGIN = "你的账号已在其他电脑登录，本机已下线";
    public static final String MSG_TEACHER = "老师已让你的账号下线，请重新登录";

    private final Map<Long, Set<HttpSession>> byUser = new ConcurrentHashMap<>();
    private final Map<String, Kicked> kicked = new ConcurrentHashMap<>();

    private static final class Kicked {
        final long at;
        final String reason;

        Kicked(long at, String reason) {
            this.at = at;
            this.reason = reason;
        }
    }

    public void register(Long userId, HttpSession session) {
        register(userId, session, null);
    }

    public void register(Long userId, HttpSession session, String ip) {
        session.setAttribute(USER_ATTR, userId);
        if (ip != null) session.setAttribute(IP_ATTR, ip);
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /** 该用户除 keep 以外的会话（keep 为 null 时返回全部），调用方可先读取其中的属性再下线。 */
    public List<HttpSession> others(Long userId, HttpSession keep) {
        List<HttpSession> out = new ArrayList<>();
        Set<HttpSession> set = byUser.get(userId);
        if (set == null) return out;
        for (HttpSession s : set) if (keep == null || !s.getId().equals(keep.getId())) out.add(s);
        return out;
    }

    /** 该用户仍然有效的会话的登录 IP（会话已失效的跳过；没记 IP 的记为空串）。 */
    public List<String> liveIps(Long userId) {
        List<String> out = new ArrayList<>();
        for (HttpSession s : others(userId, null)) {
            try {
                Object ip = s.getAttribute(IP_ATTR);
                out.add(ip == null ? "" : ip.toString());
            } catch (IllegalStateException ignored) {
                // 已失效，监听器稍后会移除
            }
        }
        return out;
    }

    /** 当前在线的登录 IP（多个时取第一个），不在线返回 null。 */
    public String onlineIp(Long userId) {
        List<String> ips = liveIps(userId);
        return ips.isEmpty() ? null : ips.get(0);
    }

    public void kick(HttpSession s) {
        kick(s, MSG_RELOGIN);
    }

    public void kick(HttpSession s, String reason) {
        kicked.put(s.getId(), new Kicked(System.currentTimeMillis(), reason));
        try {
            s.invalidate();
        } catch (IllegalStateException ignored) {
            // 已失效
        }
    }

    /** 让该用户的全部会话下线，返回下线的会话数。 */
    public int kickUser(Long userId, String reason) {
        int n = 0;
        for (HttpSession s : others(userId, null)) {
            kick(s, reason);
            n++;
        }
        return n;
    }

    public boolean wasKicked(String sessionId) {
        return kickReason(sessionId) != null;
    }

    /** 被下线会话的原因；不是被下线的返回 null。 */
    public String kickReason(String sessionId) {
        long now = System.currentTimeMillis();
        kicked.values().removeIf(k -> now - k.at > KICK_MEMORY_MS);
        if (sessionId == null) return null;
        Kicked k = kicked.get(sessionId);
        return k == null ? null : k.reason;
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
