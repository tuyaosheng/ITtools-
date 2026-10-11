package com.ittools.interact.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 课堂实时事件推送。消息格式：{ "type": "...", "data": ... }。 */
@Component
public class LessonEvents {
    private final SimpMessagingTemplate broker;

    public LessonEvents(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    public void toClass(Long lessonId, String type, Object data) {
        broker.convertAndSend("/topic/lesson." + lessonId, msg(type, data));
    }

    public void toTeacher(Long lessonId, String type, Object data) {
        broker.convertAndSend("/topic/lesson." + lessonId + ".teacher", msg(type, data));
    }

    private static Map<String, Object> msg(String type, Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("data", data);
        return m;
    }
}
