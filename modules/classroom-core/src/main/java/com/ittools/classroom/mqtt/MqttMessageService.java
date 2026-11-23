package com.ittools.classroom.mqtt;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 内置 MQTT 服务器收到的消息：主题写成“项目名/设备名”（与 SIoT 相同），项目名对应开课时生成的 lesson.mqtt_project。
 * 消息按课堂保存，下一个班开课是新的项目名，看不到上一个班的消息，所以不用下课清理。
 */
@Service
public class MqttMessageService {
    /** 每台设备在页面上最多显示的消息条数 */
    private static final int PER_DEVICE = 30;

    private final NamedParameterJdbcTemplate jdbc;

    public MqttMessageService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 主题拆成项目名和设备名；不是“项目/设备”两级（没有斜杠、某一级为空）的返回 null，和 SIoT 一样不保存。 */
    public static String[] split(String topic) {
        if (topic == null) return null;
        int i = topic.indexOf('/');
        if (i <= 0 || i == topic.length() - 1) return null;
        return new String[]{topic.substring(0, i), topic.substring(i + 1)};
    }

    /** 保存一条消息，返回所属课堂（对不上任何课堂时为 null）。 */
    @Transactional
    public Long save(String topic, String payload, String clientIp) {
        String[] pd = split(topic);
        if (pd == null) return null;
        List<Long> l = jdbc.queryForList("SELECT id FROM lesson WHERE mqtt_project = :p ORDER BY started_at DESC LIMIT 1",
                p("p", pd[0]), Long.class);
        Long lessonId = l.isEmpty() ? null : l.get(0);
        String body = payload == null ? "" : payload;
        if (body.length() > 2000) body = body.substring(0, 2000);
        jdbc.update("INSERT INTO mqtt_message(lesson_id, project, device, payload, client_ip) VALUES (:l, :p, :d, :m, :ip)",
                p("l", lessonId, "p", pd[0], "d", pd[1].length() > 200 ? pd[1].substring(0, 200) : pd[1], "m", body, "ip", clientIp));
        return lessonId;
    }

    /** 课堂的设备列表（每台设备最近的消息，新的在前），设备按名字排序。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> devices(Long lessonId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT device, payload, client_ip AS \"clientIp\", created_at AS \"createdAt\" FROM ("
                        + "SELECT m.*, ROW_NUMBER() OVER (PARTITION BY device ORDER BY created_at DESC, id DESC) AS rn "
                        + "FROM mqtt_message m WHERE lesson_id = :l) x WHERE rn <= :n ORDER BY device, created_at DESC, id DESC",
                p("l", lessonId, "n", PER_DEVICE));
        List<Map<String, Object>> counts = jdbc.queryForList(
                "SELECT device, COUNT(*) AS cnt, MAX(created_at) AS last FROM mqtt_message WHERE lesson_id = :l GROUP BY device",
                p("l", lessonId));
        Map<String, Map<String, Object>> byDevice = new LinkedHashMap<>();
        for (Map<String, Object> c : counts) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("device", c.get("device"));
            d.put("count", c.get("cnt"));
            d.put("lastAt", c.get("last"));
            d.put("messages", new ArrayList<Map<String, Object>>());
            byDevice.put((String) c.get("device"), d);
        }
        for (Map<String, Object> r : rows) {
            Map<String, Object> d = byDevice.get((String) r.get("device"));
            if (d == null) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> msgs = (List<Map<String, Object>>) d.get("messages");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("payload", r.get("payload"));
            m.put("createdAt", r.get("createdAt"));
            m.put("clientIp", r.get("clientIp"));
            msgs.add(m);
        }
        List<Map<String, Object>> out = new ArrayList<>(byDevice.values());
        out.sort((a, b) -> String.valueOf(a.get("device")).compareTo(String.valueOf(b.get("device"))));
        return out;
    }

    @Transactional
    public int clear(Long lessonId) {
        return jdbc.update("DELETE FROM mqtt_message WHERE lesson_id = :l", p("l", lessonId));
    }

    /** 每天清理：对不上课堂的消息留 3 天，其余留 180 天。 */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        jdbc.update("DELETE FROM mqtt_message WHERE lesson_id IS NULL AND created_at < now() - interval '3 days'", p());
        jdbc.update("DELETE FROM mqtt_message WHERE created_at < now() - interval '180 days'", p());
    }
}
