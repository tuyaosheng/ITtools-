package com.ittools.interact.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 掌控板编程的 SSE 推送（替代 mpython-lite 的 server.py /stream）。
 * 前端原样复用 mpython-lite，它用 EventSource 收消息，所以这里沿用 SSE 而不是平台的 STOMP。
 * 订阅者按 课堂+页面 分开；key 为 null 的是教师（什么都收），否则是某一组 / 某个人（只收发给自己的事件）。
 */
@Component
public class MpyHub {
    private static final class Sub {
        final SseEmitter emitter;
        final String key;

        Sub(SseEmitter emitter, String key) {
            this.emitter = emitter;
            this.key = key;
        }
    }

    private final Map<String, List<Sub>> subs = new ConcurrentHashMap<>();
    // 心跳：不然中间的代理 / 路由器看到连接长时间没数据会把它掐掉，掉线的连接也靠写失败清理
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mpy-sse-ping");
        t.setDaemon(true);
        return t;
    });

    public MpyHub() {
        pinger.scheduleAtFixedRate(this::pingAll, 15, 15, TimeUnit.SECONDS);
    }

    private static String room(Long lessonId, Long pageId) {
        return lessonId + ":" + pageId;
    }

    public SseEmitter subscribe(Long lessonId, Long pageId, String key) {
        SseEmitter em = new SseEmitter(0L);
        List<Sub> list = subs.computeIfAbsent(room(lessonId, pageId), k -> new CopyOnWriteArrayList<>());
        Sub sub = new Sub(em, key);
        list.add(sub);
        Runnable remove = () -> list.remove(sub);
        em.onCompletion(remove);
        em.onTimeout(remove);
        em.onError(e -> remove.run());
        try {
            em.send(SseEmitter.event().reconnectTime(2000).comment("hello"));
        } catch (IOException e) {
            list.remove(sub);
        }
        return em;
    }

    /** key 为 null：只发给教师；否则发给教师和这一组 / 这个人。 */
    public void broadcast(Long lessonId, Long pageId, String key, Map<String, Object> msg) {
        List<Sub> list = subs.get(room(lessonId, pageId));
        if (list == null) return;
        for (Sub s : list) {
            if (s.key != null && (key == null || !key.equals(s.key))) continue;
            try {
                s.emitter.send(SseEmitter.event().data(msg, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                list.remove(s);
            }
        }
    }

    private void pingAll() {
        for (List<Sub> list : subs.values()) {
            for (Sub s : list) {
                try {
                    s.emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    list.remove(s);
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        pinger.shutdownNow();
        for (List<Sub> list : subs.values()) for (Sub s : list) s.emitter.complete();
    }
}
