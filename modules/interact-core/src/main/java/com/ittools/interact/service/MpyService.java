package com.ittools.interact.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.classroom.service.CourseService;
import com.ittools.classroom.service.LessonService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ittools.platform.db.Sql.p;

/**
 * 掌控板编程（MPYTHON 页面）：替代 mpython-lite 的 server.py。
 * 前端（web/public/mpython）原样复用，接口语义与 server.py 一致，只是按 课堂+页面 隔离、身份取登录会话：
 * 学生上传的组号一律以服务器算出的为准（按组时是课堂分组号，未分组或按人时是 "s"+学生 id）。
 * 页面配置 course_page.config：mode（GROUP|PERSON）、lesson（熟悉课|公开课|全部）、grow_fill、starters{关卡: {version, state}}。
 */
@Service
public class MpyService {
    public static final List<String> LESSON_SETS = Arrays.asList("熟悉课", "公开课", "全部");
    /** 各套课包含的关卡（与 web/public/mpython/mpl/app.js 的 LESSONS 一致；“全部”为加载的全部关卡，按出现顺序）。 */
    public static final Map<String, List<String>> LESSON_LEVELS = new HashMap<>();
    static {
        LESSON_LEVELS.put("熟悉课", Arrays.asList("kb0", "gh1", "gh2", "gh3", "gh4", "demo6"));
        LESSON_LEVELS.put("公开课", Arrays.asList("demo1", "demo2", "demo3", "demo4", "demo7"));
        LESSON_LEVELS.put("全部", Arrays.asList("kb0", "demo1", "demo2", "demo3", "demo4", "gh1", "gh2", "gh3", "gh4", "demo6", "demo7"));
    }
    private static final Pattern KIND = Pattern.compile("[a-z_]{1,20}");
    private static final Pattern DEMO_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern GROUP_KEY = Pattern.compile("s?\\d{1,12}");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_CODE = 200_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final CourseService courses;
    private final MpyHub hub;
    private final ObjectMapper json = new ObjectMapper();

    public MpyService(NamedParameterJdbcTemplate jdbc, LessonService lessons, CourseService courses, MpyHub hub) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.courses = courses;
        this.hub = hub;
    }

    // ================= 配置 =================

    private Map<String, Object> config(Long pageId) {
        List<String> l = jdbc.queryForList("SELECT config FROM course_page WHERE id = :id", p("id", pageId), String.class);
        if (l.isEmpty()) throw new IllegalArgumentException("页面不存在");
        return readMap(l.get(0));
    }

    private void saveConfig(Long pageId, Map<String, Object> cfg) {
        jdbc.update("UPDATE course_page SET config = :c WHERE id = :id", p("c", write(cfg), "id", pageId));
    }

    private static String lessonSet(Map<String, Object> cfg) {
        Object v = cfg.get("lesson");
        return v != null && LESSON_SETS.contains(String.valueOf(v)) ? String.valueOf(v) : "公开课";
    }

    private static boolean personMode(Map<String, Object> cfg) {
        return "PERSON".equals(cfg.get("mode"));
    }

    private void requireType(Map<String, Object> pg) {
        if (!"MPYTHON".equals(pg.get("pageType"))) throw new IllegalArgumentException("页面类型不匹配");
    }

    /** 教师访问页面：有课堂时要求是自己的课堂；没有课堂（课程编辑里改初始积木）时要求能读该课程。 */
    private void requireTeacherPage(Long teacherId, Long lessonId, Long pageId, boolean write) {
        Map<String, Object> pg;
        if (lessonId != null) {
            lessons.requireTeacher(teacherId, lessonId);
            pg = lessons.requirePageOfLesson(lessonId, pageId);
        } else {
            pg = courses.page(pageId);
        }
        Long courseId = ((Number) pg.get("courseId")).longValue();
        if (write) courses.requireOwner(teacherId, courseId);
        else courses.getReadable(teacherId, courseId);
        requireType(pg);
    }

    private void requireStudentPage(Long studentId, Long lessonId, Long pageId) {
        requireType(lessons.studentPage(studentId, lessonId, pageId));
    }

    /** 学生在这个页面上属于哪个“组”：按组且已分组时是组号，否则是 "s" + 学生 id。 */
    public String keyOf(Long lessonId, Long pageId, Long studentId) {
        if (!personMode(config(pageId))) {
            int g = lessons.groupNoOf(lessonId, studentId);
            if (g > 0) return String.valueOf(g);
        }
        return "s" + studentId;
    }

    private String studentName(Long id) {
        List<String> l = jdbc.queryForList("SELECT name FROM users WHERE id = :id", p("id", id), String.class);
        return l.isEmpty() ? "学生" + id : l.get(0);
    }

    private Map<String, Object> baseConfig(Map<String, Object> cfg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("wifi_ssid", "");
        out.put("wifi_pwd", "");
        out.put("groups", 30);
        out.put("board_host", "");
        out.put("lesson", lessonSet(cfg));
        out.put("grow_fill", Boolean.TRUE.equals(cfg.get("grow_fill")));
        out.put("mode", personMode(cfg) ? "PERSON" : "GROUP");
        Map<String, Object> versions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : starters(cfg).entrySet()) {
            if (e.getValue() instanceof Map) versions.put(e.getKey(), ((Map<?, ?>) e.getValue()).get("version"));
        }
        out.put("starterVersions", versions);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> teacherConfig(Long teacherId, Long lessonId, Long pageId) {
        requireTeacherPage(teacherId, lessonId, pageId, false);
        return baseConfig(config(pageId));
    }

    /** 学生：页面配置 + 我是哪一组、我们组在服务器上的进度（换电脑、清了浏览器也能接着做）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> studentConfig(Long studentId, Long lessonId, Long pageId) {
        requireStudentPage(studentId, lessonId, pageId);
        Map<String, Object> out = baseConfig(config(pageId));
        String key = keyOf(lessonId, pageId, studentId);
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("key", key);
        me.put("label", key.startsWith("s") ? studentName(studentId) : null);
        me.put("state", loadStates(lessonId, pageId, key).get(key));
        out.put("me", me);
        return out;
    }

    // ================= 状态 =================

    private Map<String, Map<String, Object>> loadStates(Long lessonId, Long pageId, String onlyKey) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        String sql = "SELECT * FROM mpy_state WHERE lesson_id = :l AND page_id = :p" + (onlyKey != null ? " AND group_key = :k" : "");
        for (Map<String, Object> r : jdbc.queryForList(sql, p("l", lessonId, "p", pageId, "k", onlyKey))) {
            out.put((String) r.get("group_key"), stateView(r));
        }
        return out;
    }

    private Map<String, Object> stateView(Map<String, Object> r) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("code", r.get("code"));
        s.put("filled", readList(r.get("filled")));
        Object up = r.get("updated_at");
        s.put("updated", up instanceof Timestamp ? ((Timestamp) up).toLocalDateTime().format(HMS) : null);
        s.put("events", readList(r.get("events")));
        s.put("lamp", r.get("lamp"));
        s.put("runs", r.get("runs"));
        s.put("level", r.get("level"));
        s.put("levelNo", r.get("level_no"));
        s.put("passed", readList(r.get("passed")));
        s.put("ws", r.get("workspace") == null ? null : readAny((String) r.get("workspace")));
        return s;
    }

    /** 教师看板：所有组（或所有人）的进度，以及卡片顺序与名字。 */
    @Transactional(readOnly = true)
    public Map<String, Object> teacherState(Long teacherId, Long lessonId, Long pageId) {
        requireTeacherPage(teacherId, lessonId, pageId, false);
        Map<String, Object> l = lessons.get(lessonId);
        Map<String, Map<String, Object>> states = loadStates(lessonId, pageId, null);
        Map<String, String> labels = new LinkedHashMap<>();   // key -> 名字（组号的 key 不给名字，看板显示“N 组”）
        Set<String> keys = new LinkedHashSet<>();
        if (personMode(config(pageId))) {
            for (Map<String, Object> s : jdbc.queryForList("SELECT id, name FROM users WHERE class_id = :k AND role = 'STUDENT' "
                    + "AND NOT graduated ORDER BY student_no NULLS LAST, name", p("k", l.get("classId")))) {
                String k = "s" + s.get("id");
                keys.add(k);
                labels.put(k, (String) s.get("name"));
            }
        } else {
            keys.addAll(jdbc.queryForList("SELECT DISTINCT group_no FROM lesson_attendance WHERE lesson_id = :l AND group_no > 0 "
                    + "ORDER BY group_no", p("l", lessonId), Integer.class).stream().map(String::valueOf).collect(Collectors.toList()));
            for (Map<String, Object> s : jdbc.queryForList("SELECT u.id, u.name FROM lesson_attendance a JOIN users u ON u.id = a.student_id "
                    + "WHERE a.lesson_id = :l AND a.group_no = 0 AND a.joined_at IS NOT NULL ORDER BY u.student_no NULLS LAST, u.name", p("l", lessonId))) {
                String k = "s" + s.get("id");
                keys.add(k);
                labels.put(k, (String) s.get("name"));
            }
        }
        for (String k : states.keySet()) {
            if (keys.add(k) && k.startsWith("s")) labels.put(k, studentName(Long.valueOf(k.substring(1))));
        }
        List<Map<String, Object>> keyList = new ArrayList<>();
        for (String k : keys) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k);
            m.put("label", labels.get(k));
            keyList.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groups", keys.size());
        out.put("keys", keyList);
        out.put("state", states);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentState(Long studentId, Long lessonId, Long pageId) {
        requireStudentPage(studentId, lessonId, pageId);
        String key = keyOf(lessonId, pageId, studentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groups", 1);
        out.put("state", loadStates(lessonId, pageId, key));
        return out;
    }

    // ================= 学生上传程序 =================

    /** 对应 server.py 的 POST /api/code：每改一次积木传一次；代码真的变了或不是普通编辑时才记日志。 */
    @Transactional
    public Map<String, Object> saveCode(Long studentId, Long lessonId, Long pageId, Map<String, Object> body) {
        Map<String, Object> l = lessons.requireStudent(studentId, lessonId);
        requireStudentPage(studentId, lessonId, pageId);
        if (!"ACTIVE".equals(l.get("status"))) return result(false, "课堂已结束");
        String key = keyOf(lessonId, pageId, studentId);
        String code = str(body.get("code"));
        if (code.length() > MAX_CODE) return result(false, "程序太长");
        String action = str(body.get("action"));
        if (action.isEmpty()) action = "edit";
        if (action.length() > 20) action = action.substring(0, 20);
        String level = str(body.get("level"));
        if (level.length() > 40) level = level.substring(0, 40);
        Object filled = body.get("filled") instanceof List ? body.get("filled") : Collections.emptyList();
        Object ws = body.get("ws");
        Integer levelNo = body.get("levelNo") instanceof Number ? ((Number) body.get("levelNo")).intValue() : null;

        Map<String, Object> old = lockState(lessonId, pageId, key);
        boolean changed = old == null || !code.equals(old.get("code")) || !level.equals(str(old.get("level")));
        // 通关记录只增不减：换电脑、清浏览器后本机的通关表是空的，不能把服务器上已有的冲掉
        Set<String> passed = new LinkedHashSet<>();
        if (old != null) for (Object o : readList(old.get("passed"))) passed.add(String.valueOf(o));
        if (body.get("passed") instanceof List) {
            for (Object o : (List<?>) body.get("passed")) {
                String id = String.valueOf(o);
                if (DEMO_ID.matcher(id).matches() && passed.size() < 50) passed.add(id);
            }
        }
        int runs = old == null ? 0 : ((Number) old.get("runs")).intValue();
        if ("run".equals(action) || "save".equals(action)) runs++;
        jdbc.update("INSERT INTO mpy_state(lesson_id, page_id, group_key, code, filled, workspace, level, level_no, passed, runs, updated_at, updated_by) "
                        + "VALUES (:l, :p, :k, :code, :filled, :ws, :level, :lno, :passed, :runs, now(), :s) "
                        + "ON CONFLICT (lesson_id, page_id, group_key) DO UPDATE SET code = EXCLUDED.code, filled = EXCLUDED.filled, "
                        + "workspace = COALESCE(EXCLUDED.workspace, mpy_state.workspace), level = COALESCE(EXCLUDED.level, mpy_state.level), "
                        + "level_no = COALESCE(EXCLUDED.level_no, mpy_state.level_no), passed = EXCLUDED.passed, runs = EXCLUDED.runs, "
                        + "updated_at = now(), updated_by = EXCLUDED.updated_by",
                p("l", lessonId, "p", pageId, "k", key, "code", code, "filled", write(filled), "ws", ws == null ? null : write(ws),
                        "level", level.isEmpty() ? null : level, "lno", levelNo, "passed", write(new ArrayList<>(passed)), "runs", runs, "s", studentId));
        if (changed || !"edit".equals(action)) {
            int done = 0, total = 0;
            for (Object f : (List<?>) filled) {
                total++;
                if (f instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) f).get("ok"))) done++;
            }
            jdbc.update("INSERT INTO mpy_log(lesson_id, page_id, group_key, student_id, kind, action, progress, level, code) "
                            + "VALUES (:l, :p, :k, :s, 'CODE', :a, :pr, :level, :code)",
                    p("l", lessonId, "p", pageId, "k", key, "s", studentId, "a", action, "pr", done + "/" + total,
                            "level", level.isEmpty() ? null : level, "code", code));
        }
        Map<String, Object> st = loadStates(lessonId, pageId, key).get(key);
        Map<String, Object> msg = new LinkedHashMap<>(st);
        msg.put("kind", "code");
        msg.put("group", key);
        msg.put("action", action);
        if (key.startsWith("s")) msg.put("label", studentName(studentId));
        hub.broadcast(lessonId, pageId, null, msg);
        return result(true, null);
    }

    private Map<String, Object> lockState(Long lessonId, Long pageId, String key) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT * FROM mpy_state WHERE lesson_id = :l AND page_id = :p AND group_key = :k FOR UPDATE",
                p("l", lessonId, "p", pageId, "k", key));
        return l.isEmpty() ? null : l.get(0);
    }

    // ================= 板子事件 =================

    /** 学生页转发板子经 USB 说的话（src=usb）。 */
    @Transactional
    public Map<String, Object> studentEvent(Long studentId, Long lessonId, Long pageId, String type) {
        Map<String, Object> l = lessons.requireStudent(studentId, lessonId);
        requireStudentPage(studentId, lessonId, pageId);
        if (!"ACTIVE".equals(l.get("status"))) return result(false, "课堂已结束");
        return event(lessonId, pageId, keyOf(lessonId, pageId, studentId), studentId, type, "usb");
    }

    /** 教师在看板上替某组模拟（板子坏了的组也能过关）。 */
    @Transactional
    public Map<String, Object> teacherEvent(Long teacherId, Long lessonId, Long pageId, String group, String type) {
        requireTeacherPage(teacherId, lessonId, pageId, false);
        if (group == null || !GROUP_KEY.matcher(group).matches()) throw new IllegalArgumentException("组号无效");
        return event(lessonId, pageId, group, null, type, "sim");
    }

    private Map<String, Object> event(Long lessonId, Long pageId, String key, Long studentId, String type, String source) {
        if (type == null || !KIND.matcher(type).matches()) throw new IllegalArgumentException("need group and type");
        String t = LocalDateTime.now().format(HMS);
        Map<String, Object> old = lockState(lessonId, pageId, key);
        List<Object> events = old == null ? new ArrayList<>() : new ArrayList<>(readList(old.get("events")));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("t", t);
        e.put("type", type);
        e.put("src", source);
        events.add(e);
        if (events.size() > 30) events = new ArrayList<>(events.subList(events.size() - 30, events.size()));
        jdbc.update("INSERT INTO mpy_state(lesson_id, page_id, group_key, events, lamp) VALUES (:l, :p, :k, :ev, :lamp) "
                        + "ON CONFLICT (lesson_id, page_id, group_key) DO UPDATE SET events = EXCLUDED.events, lamp = EXCLUDED.lamp",
                p("l", lessonId, "p", pageId, "k", key, "ev", write(events), "lamp", type));
        jdbc.update("INSERT INTO mpy_log(lesson_id, page_id, group_key, student_id, kind, action, source) VALUES (:l, :p, :k, :s, 'EVENT', :a, :src)",
                p("l", lessonId, "p", pageId, "k", key, "s", studentId, "a", type, "src", source));
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("kind", "event");
        msg.put("group", key);
        msg.put("type", type);
        msg.put("t", t);
        msg.put("src", source);
        hub.broadcast(lessonId, pageId, key, msg);
        return result(true, null);
    }

    // ================= 推送 =================

    public SseEmitter studentStream(Long studentId, Long lessonId, Long pageId) {
        requireStudentPage(studentId, lessonId, pageId);
        return hub.subscribe(lessonId, pageId, keyOf(lessonId, pageId, studentId));
    }

    public SseEmitter teacherStream(Long teacherId, Long lessonId, Long pageId) {
        requireTeacherPage(teacherId, lessonId, pageId, false);
        return hub.subscribe(lessonId, pageId, null);
    }

    // ================= 初始积木与课堂设置（教师） =================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> starters(Map<String, Object> cfg) {
        Object o = cfg.get("starters");
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @Transactional(readOnly = true)
    public Object starterForStudent(Long studentId, Long lessonId, Long pageId, String demo) {
        requireStudentPage(studentId, lessonId, pageId);
        return starter(pageId, demo);
    }

    @Transactional(readOnly = true)
    public Object starterForTeacher(Long teacherId, Long lessonId, Long pageId, String demo) {
        requireTeacherPage(teacherId, lessonId, pageId, false);
        return starter(pageId, demo);
    }

    private Object starter(Long pageId, String demo) {
        Object s = demo == null ? null : starters(config(pageId)).get(demo);
        return s != null ? s : new LinkedHashMap<>();
    }

    /** 存 / 删某一关的初始积木（state 为 null 时恢复默认）。初始积木跟着课程走，复制课程、导出课程包都带着。 */
    @Transactional
    public Map<String, Object> saveStarter(Long teacherId, Long lessonId, Long pageId, Map<String, Object> body) {
        requireTeacherPage(teacherId, lessonId, pageId, true);
        String demo = str(body.get("demo"));
        if (!DEMO_ID.matcher(demo).matches()) return result(false, "bad demo id");
        Map<String, Object> cfg = config(pageId);
        Map<String, Object> st = starters(cfg);
        Map<String, Object> out = result(true, null);
        if (body.get("state") == null) {
            st.remove(demo);
            out.put("version", null);
        } else {
            String version = LocalDateTime.now().withNano(0).toString();
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("version", version);
            v.put("state", body.get("state"));
            st.put(demo, v);
            out.put("version", version);
        }
        cfg.put("starters", st);
        saveConfig(pageId, cfg);
        return out;
    }

    /** 课堂设置：这节课上哪套关卡、按组还是按人、温室填空模式。 */
    @Transactional
    public Map<String, Object> saveSettings(Long teacherId, Long lessonId, Long pageId, Map<String, Object> body) {
        requireTeacherPage(teacherId, lessonId, pageId, true);
        Map<String, Object> cfg = config(pageId);
        if (body.containsKey("grow_fill")) cfg.put("grow_fill", Boolean.TRUE.equals(body.get("grow_fill")));
        if (body.get("lesson") != null) {
            String v = String.valueOf(body.get("lesson"));
            if (!LESSON_SETS.contains(v)) return result(false, "关卡设置无效");
            cfg.put("lesson", v);
        }
        if (body.get("mode") != null) {
            String v = String.valueOf(body.get("mode"));
            if (!"GROUP".equals(v) && !"PERSON".equals(v)) return result(false, "分组方式无效");
            cfg.put("mode", v);
        }
        saveConfig(pageId, cfg);
        Map<String, Object> out = result(true, null);
        out.putAll(baseConfig(cfg));
        return out;
    }

    // ================= 成绩 =================

    /**
     * 本堂课每位学生在该页面的得分：通关数 / 本页关卡数 × 满分（页面分值为 0 时满分取关卡数，即一关一分）。
     * 按组时同组同分。没人动过时返回空表。
     */
    @Transactional(readOnly = true)
    public Map<Long, Double> lessonScores(Long lessonId, Long pageId, int pageScore) {
        Map<String, Object> cfg = config(pageId);
        List<String> levels = LESSON_LEVELS.get(lessonSet(cfg));
        Map<String, Set<String>> passedByKey = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT group_key, passed FROM mpy_state WHERE lesson_id = :l AND page_id = :p",
                p("l", lessonId, "p", pageId))) {
            Set<String> s = new LinkedHashSet<>();
            for (Object o : readList(r.get("passed"))) if (levels.contains(String.valueOf(o))) s.add(String.valueOf(o));
            passedByKey.put((String) r.get("group_key"), s);
        }
        Map<Long, Double> out = new LinkedHashMap<>();
        if (passedByKey.isEmpty()) return out;
        double full = fullScore(pageId, pageScore);
        boolean person = personMode(cfg);
        for (Map<String, Object> r : jdbc.queryForList("SELECT student_id, group_no FROM lesson_attendance WHERE lesson_id = :l",
                p("l", lessonId))) {
            Long sid = ((Number) r.get("student_id")).longValue();
            int g = ((Number) r.get("group_no")).intValue();
            String key = !person && g > 0 ? String.valueOf(g) : "s" + sid;
            Set<String> s = passedByKey.get(key);
            if (s != null) out.put(sid, Math.round(s.size() * full / levels.size() * 10) / 10.0);
        }
        return out;
    }

    public double fullScore(Long pageId, int pageScore) {
        return pageScore > 0 ? pageScore : LESSON_LEVELS.get(lessonSet(config(pageId))).size();
    }

    // ================= 工具 =================

    private static Map<String, Object> result(boolean ok, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        if (error != null) m.put("error", error);
        return m;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private Map<String, Object> readMap(String s) {
        if (s == null || s.isEmpty()) return new LinkedHashMap<>();
        try {
            return json.readValue(s, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private List<Object> readList(Object s) {
        if (!(s instanceof String) || ((String) s).isEmpty()) return new ArrayList<>();
        try {
            return json.readValue((String) s, new TypeReference<ArrayList<Object>>() { });
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private Object readAny(String s) {
        try {
            return json.readValue(s, Object.class);
        } catch (IOException e) {
            return null;
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
