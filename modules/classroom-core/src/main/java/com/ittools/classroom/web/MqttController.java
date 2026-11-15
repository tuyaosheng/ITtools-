package com.ittools.classroom.web;

import com.ittools.classroom.mqtt.MqttBroker;
import com.ittools.classroom.mqtt.MqttMessageService;
import com.ittools.classroom.service.LessonService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/** 内置 MQTT 服务器：本节课的项目名、各设备收到的消息（物联网消息页、课堂控制台），老师清空、从网页发消息，管理员看运行状态。 */
@RestController
public class MqttController {
    private final LessonService lessons;
    private final MqttMessageService messages;
    private final MqttBroker broker;

    public MqttController(LessonService lessons, MqttMessageService messages, MqttBroker broker) {
        this.lessons = lessons;
        this.messages = messages;
        this.broker = broker;
    }

    private Map<String, Object> view(Map<String, Object> lesson, Long lessonId, String host) {
        Map<String, Object> st = broker.status();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("project", lesson.get("mqttProject"));
        m.put("server", com.ittools.classroom.util.ServerAddress.forBoards(host));
        m.put("port", st.get("port"));
        m.put("username", st.get("username"));
        m.put("password", st.get("password"));
        m.put("running", st.get("running"));
        m.put("devices", lesson.get("mqttProject") == null ? java.util.Collections.emptyList() : messages.devices(lessonId));
        return m;
    }

    @GetMapping("/api/student/lessons/{lessonId}/mqtt")
    public ApiResponse<Map<String, Object>> student(@PathVariable Long lessonId, HttpServletRequest req) {
        Map<String, Object> l = lessons.requireStudent(CurrentUser.id(), lessonId);
        Map<String, Object> m = view(l, lessonId, req.getServerName());
        int g = lessons.groupNoOf(lessonId, CurrentUser.id());
        m.put("groupNo", g);
        m.put("myDevice", g > 0 ? String.format("G%02d", g) : null);
        return ApiResponse.ok(m);
    }

    @GetMapping("/api/teacher/lessons/{lessonId}/mqtt")
    public ApiResponse<Map<String, Object>> teacher(@PathVariable Long lessonId, HttpServletRequest req) {
        Map<String, Object> l = lessons.requireTeacher(CurrentUser.id(), lessonId);
        Map<String, Object> m = view(l, lessonId, req.getServerName());
        m.put("error", broker.status().get("error"));
        m.put("clients", broker.status().get("clients"));
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/api/teacher/lessons/{lessonId}/mqtt")
    public ApiResponse<Integer> clear(@PathVariable Long lessonId) {
        lessons.requireTeacher(CurrentUser.id(), lessonId);
        return ApiResponse.ok(messages.clear(lessonId));
    }

    /** 老师从网页往本节课项目下的某个设备发一条消息（测试订阅用）。 */
    @PostMapping("/api/teacher/lessons/{lessonId}/mqtt/publish")
    public ApiResponse<Void> publish(@PathVariable Long lessonId, @RequestBody Map<String, Object> body) {
        Map<String, Object> l = lessons.requireTeacher(CurrentUser.id(), lessonId);
        if (l.get("mqttProject") == null) throw new IllegalArgumentException("本节课没有 MQTT 项目");
        Body b = Body.of(body);
        String device = b.reqStr("device", "设备名").trim();
        if (device.isEmpty() || device.contains("#") || device.contains("+")) throw new IllegalArgumentException("设备名不能为空，也不能带 # 或 +");
        broker.publishFromWeb(l.get("mqttProject") + "/" + device, b.str("payload"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/mqtt/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(broker.status());
    }
}
