package com.ittools.classroom.service;

import com.ittools.lab.service.LabQueryService;
import com.ittools.lab.service.NetworkControlService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 课堂控制台座位图上的开网 / 断网：只能操作本人课堂所在机房的电脑。 */
@Service
public class LessonNetworkService {
    private final LessonService lessons;
    private final LabQueryService lab;
    private final NetworkControlService network;

    public LessonNetworkService(LessonService lessons, LabQueryService lab, NetworkControlService network) {
        this.lessons = lessons;
        this.lab = lab;
        this.network = network;
    }

    /** 网络状态总览，只保留本课堂机房里的电脑。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> status(Long teacherId, Long lessonId) {
        Set<String> ips = roomIps(lessons.requireTeacher(teacherId, lessonId));
        Map<String, Object> out = network.overview();
        out.put("status", only((Map<String, Object>) out.get("status"), ips));
        out.put("last", only((Map<String, Object>) out.get("last"), ips));
        return out;
    }

    /** 批量开网 / 断网；下课后不能再操作，IP 必须是本课堂机房的。 */
    public Map<String, Object> batch(Long teacherId, Long lessonId, List<String> ips, boolean on) {
        Map<String, Object> l = lessons.requireTeacher(teacherId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        if (ips == null || ips.isEmpty()) throw new IllegalArgumentException("请先选择电脑");
        Set<String> room = roomIps(l);
        for (String ip : ips) {
            if (!room.contains(ip)) throw new IllegalArgumentException("不是本课堂机房的电脑（" + ip + "）");
        }
        return network.setIps(ips, on);
    }

    @SuppressWarnings("unchecked")
    private Set<String> roomIps(Map<String, Object> lesson) {
        Set<String> out = new LinkedHashSet<>();
        Map<String, Object> room = lab.seatMapForClass(((Number) lesson.get("classId")).longValue());
        if (room == null) return out;
        for (Map<String, Object> s : (List<Map<String, Object>>) room.get("seats")) {
            if (s.get("ipAddress") != null) out.add((String) s.get("ipAddress"));
        }
        return out;
    }

    private static <V> Map<String, V> only(Map<String, V> m, Set<String> ips) {
        Map<String, V> out = new HashMap<>();
        if (m != null) m.forEach((k, v) -> { if (ips.contains(k)) out.put(k, v); });
        return out;
    }
}
