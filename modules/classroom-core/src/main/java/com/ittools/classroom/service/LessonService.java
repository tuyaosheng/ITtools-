package com.ittools.classroom.service;

import com.ittools.lab.service.LabQueryService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 开课/进课/考勤（原 it_TeRecord、it_sturecord；teacher/coursemanage.aspx、viewcourse*.aspx，
 * student/courselogin.aspx、coursesturecord.aspx 等）。
 * 同时是其他子项目（作业/测验/互动）校验"学生是否在本堂课""教师是否是本堂课教师"的入口。
 */
@Service
public class LessonService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ClassBindingService bindings;
    private final CourseService courses;
    private final TermService terms;
    private final LabQueryService lab;

    public LessonService(NamedParameterJdbcTemplate jdbc, ClassBindingService bindings, CourseService courses,
                         TermService terms, LabQueryService lab) {
        this.jdbc = jdbc;
        this.bindings = bindings;
        this.courses = courses;
        this.terms = terms;
        this.lab = lab;
    }

    private static final String LESSON_COLS =
            "l.id, l.course_id AS \"courseId\", c.name AS \"courseName\", l.class_id AS \"classId\", "
                    + "sy.label || k.name AS \"className\", l.teacher_id AS \"teacherId\", u.name AS \"teacherName\", "
                    + "l.status, l.started_at AS \"startedAt\", l.ended_at AS \"endedAt\", l.group_model_id AS \"groupModelId\", "
                    + "gm.name AS \"groupModelName\", l.summary, l.term_id AS \"termId\", l.disabled_features AS \"disabledFeatures\", l.mqtt_project AS \"mqttProject\", "
                    + "(SELECT COUNT(*) FROM lesson_attendance a WHERE a.lesson_id = l.id AND a.status = 'PRESENT') AS \"presentCount\", "
                    + "(SELECT COUNT(*) FROM lesson_attendance a WHERE a.lesson_id = l.id AND a.status = 'ABSENT') AS \"absentCount\", "
                    + "(SELECT COUNT(*) FROM lesson_attendance a WHERE a.lesson_id = l.id AND a.status = 'LEAVE') AS \"leaveCount\", "
                    + "(SELECT COUNT(*) FROM users s WHERE s.class_id = l.class_id AND s.role = 'STUDENT' AND NOT s.graduated) AS \"studentCount\" "
                    + "FROM lesson l JOIN course c ON c.id = l.course_id JOIN klass k ON k.id = l.class_id "
                    + "JOIN school_year sy ON sy.id = k.school_year_id JOIN users u ON u.id = l.teacher_id "
                    + "LEFT JOIN lab_group_model gm ON gm.id = l.group_model_id ";

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long lessonId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + LESSON_COLS + "WHERE l.id = :id", p("id", lessonId));
        if (l.isEmpty()) throw new IllegalArgumentException("课堂不存在");
        return l.get(0);
    }

    // ================= 教师 =================

    @Transactional
    public Map<String, Object> start(Long teacherId, Long courseId, Long classId, Long groupModelId) {
        courses.getReadable(teacherId, courseId);
        courses.requireOwner(teacherId, courseId);
        if (Boolean.TRUE.equals(courses.get(courseId).get("archived"))) {
            throw new IllegalArgumentException("这门课程已归档，请先在“我的课程”里取消归档再开课");
        }
        if (!bindings.teacherCanUseClass(teacherId, classId)) {
            throw new IllegalArgumentException("该班级已绑定其他教师，不能开课");
        }
        List<Map<String, Object>> active = jdbc.queryForList(
                "SELECT l.id, u.name AS teacher, c.name AS course FROM lesson l JOIN users u ON u.id = l.teacher_id "
                        + "JOIN course c ON c.id = l.course_id WHERE l.class_id = :k AND l.status = 'ACTIVE'", p("k", classId));
        if (!active.isEmpty()) {
            Map<String, Object> a = active.get(0);
            throw new IllegalArgumentException("该班级正在上课：" + a.get("teacher") + "《" + a.get("course") + "》，请先结束该课堂");
        }
        Long id = jdbc.queryForObject(
                "INSERT INTO lesson(course_id, teacher_id, class_id, term_id, group_model_id) "
                        + "SELECT :c, :t, :k, COALESCE(c.term_id, :cur), :g FROM course c WHERE c.id = :c RETURNING id",
                p("c", courseId, "t", teacherId, "k", classId, "cur", terms.currentTermId(), "g", groupModelId), Long.class);
        assignMqttProject(id, classId);
        return get(id);
    }

    /** 下课：未进课且未请假的学生记为缺席（原 Absent 统计）。 */
    @Transactional
    public Map<String, Object> end(Long teacherId, Long lessonId, String summary) {
        Map<String, Object> l = requireTeacher(teacherId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        endInternal(lessonId, summary);
        return get(lessonId);
    }

    private void endInternal(Long lessonId, String summary) {
        jdbc.update("INSERT INTO lesson_attendance(lesson_id, student_id, status, auto_absent) "
                + "SELECT :l, s.id, 'ABSENT', TRUE FROM users s JOIN lesson l ON l.class_id = s.class_id "
                + "WHERE l.id = :l AND s.role = 'STUDENT' AND NOT s.graduated "
                + "AND NOT EXISTS (SELECT 1 FROM lesson_attendance a WHERE a.lesson_id = :l AND a.student_id = s.id)", p("l", lessonId));
        jdbc.update("UPDATE lesson SET status = 'ENDED', ended_at = now(), summary = COALESCE(:s, summary) WHERE id = :l",
                p("l", lessonId, "s", summary));
    }

    /**
     * 重新开课：已下课的课堂还原到上课中（页面开放、分组、提交、成绩、MQTT 项目都保留）。
     * 撤回下课时自动记的缺席，恢复下课时自动停用的成绩开网规则。同一个班正在上别的课时不能重新开。
     */
    @Transactional
    public Map<String, Object> reopen(Long teacherId, Long lessonId) {
        Map<String, Object> l = requireTeacher(teacherId, lessonId);
        if ("ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂还在上课中");
        List<Map<String, Object>> active = jdbc.queryForList(
                "SELECT u.name AS teacher, c.name AS course FROM lesson x JOIN users u ON u.id = x.teacher_id JOIN course c ON c.id = x.course_id "
                        + "WHERE x.class_id = :k AND x.status = 'ACTIVE' AND x.id <> :l", p("k", l.get("classId"), "l", lessonId));
        if (!active.isEmpty()) {
            throw new IllegalArgumentException("该班级正在上课：" + active.get(0).get("teacher") + "《" + active.get(0).get("course") + "》，请先结束该课堂再重新开课");
        }
        jdbc.update("DELETE FROM lesson_attendance WHERE lesson_id = :l AND auto_absent", p("l", lessonId));
        jdbc.update("UPDATE lesson SET status = 'ACTIVE', ended_at = NULL WHERE id = :l", p("l", lessonId));
        jdbc.update("UPDATE lesson_net_gate SET enabled = TRUE, disabled_by_end = FALSE WHERE lesson_id = :l AND disabled_by_end", p("l", lessonId));
        return get(lessonId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherLessons(Long teacherId, String status, Long courseId) {
        StringBuilder sql = new StringBuilder("SELECT " + LESSON_COLS + "WHERE l.teacher_id = :t ");
        if (status != null) sql.append("AND l.status = :st ");
        if (courseId != null) sql.append("AND l.course_id = :c ");
        sql.append("ORDER BY l.started_at DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), p("t", teacherId, "st", status, "c", courseId));
    }

    public Map<String, Object> requireTeacher(Long teacherId, Long lessonId) {
        Map<String, Object> l = get(lessonId);
        if (!teacherId.equals(((Number) l.get("teacherId")).longValue())) {
            throw new IllegalArgumentException("不是你的课堂");
        }
        return l;
    }

    /** 课堂页面及其本堂开放状态（未设置时取课程页面的默认可见性）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lessonPages(Long lessonId) {
        return jdbc.queryForList(
                "SELECT p.id, p.seq, p.title, p.page_type AS \"pageType\", p.score, p.ref_id AS \"refId\", "
                        + "p.visible_roles AS \"visibleRoles\", COALESCE(s.is_open, p.student_visible) AS \"open\" "
                        + "FROM lesson l JOIN course_page p ON p.course_id = l.course_id "
                        + "LEFT JOIN lesson_page_state s ON s.lesson_id = l.id AND s.page_id = p.id "
                        + "WHERE l.id = :l ORDER BY p.seq, p.id", p("l", lessonId));
    }

    @Transactional
    public void setPageOpen(Long teacherId, Long lessonId, Long pageId, boolean open) {
        requireTeacher(teacherId, lessonId);
        jdbc.update("INSERT INTO lesson_page_state(lesson_id, page_id, is_open) VALUES (:l, :p, :o) "
                + "ON CONFLICT (lesson_id, page_id) DO UPDATE SET is_open = EXCLUDED.is_open", p("l", lessonId, "p", pageId, "o", open));
    }

    /** 教师可按课堂开关的互动功能：HELP 举手求助，FORUM 课堂论坛。 */
    public static final List<String> FEATURES = java.util.Arrays.asList("HELP", "FORUM");

    private static List<String> disabledOf(Object v) {
        List<String> out = new ArrayList<>();
        if (v != null) for (String s : String.valueOf(v).split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    /** enabled：功能名 → 是否开放；只改传入的功能。 */
    @Transactional
    public Map<String, Object> setFeatures(Long teacherId, Long lessonId, Map<String, Boolean> enabled) {
        Map<String, Object> l = requireTeacher(teacherId, lessonId);
        List<String> disabled = disabledOf(l.get("disabledFeatures"));
        for (Map.Entry<String, Boolean> e : enabled.entrySet()) {
            if (!FEATURES.contains(e.getKey())) throw new IllegalArgumentException("未知功能：" + e.getKey());
            disabled.remove(e.getKey());
            if (Boolean.FALSE.equals(e.getValue())) disabled.add(e.getKey());
        }
        jdbc.update("UPDATE lesson SET disabled_features = :d WHERE id = :l", p("d", String.join(",", disabled), "l", lessonId));
        return get(lessonId);
    }

    /** 课堂已关闭该功能时抛出异常（msg 为给学生的提示）。 */
    public void requireFeature(Long lessonId, String feature, String msg) {
        if (disabledOf(get(lessonId).get("disabledFeatures")).contains(feature)) throw new IllegalArgumentException(msg);
    }

    /** 学生所在班级正在进行的课堂（课程为 courseId）是否关闭了该功能。 */
    public boolean featureClosedForStudent(Long studentId, Long courseId, String feature) {
        List<String> ds = jdbc.queryForList("SELECT l.disabled_features FROM lesson l JOIN users s ON s.class_id = l.class_id "
                + "WHERE s.id = :s AND l.course_id = :c AND l.status = 'ACTIVE'", p("s", studentId, "c", courseId), String.class);
        for (String d : ds) if (disabledOf(d).contains(feature)) return true;
        return false;
    }

    /** 全班学生考勤（含未进课学生，状态 null 表示尚未进课）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> attendance(Long lessonId) {
        Map<Integer, Map<String, Object>> info = groupInfo(lessonId);
        List<Map<String, Object>> rows = attendanceRows(lessonId);
        for (Map<String, Object> r : rows) {
            int g = ((Number) r.get("groupNo")).intValue();
            Map<String, Object> e = info.get(g);
            r.put("groupLabel", g <= 0 ? null : e == null ? "第" + g + "组" : e.get("label"));
        }
        return rows;
    }

    private List<Map<String, Object>> attendanceRows(Long lessonId) {
        return jdbc.queryForList(
                "SELECT s.id AS \"studentId\", s.name, s.student_no AS \"studentNo\", a.status, a.pc_name AS \"pcName\", "
                        + "a.ip_address AS \"ipAddress\", a.joined_at AS \"joinedAt\", COALESCE(a.group_no, 0) AS \"groupNo\", "
                        + "a.score, a.comment, a.leave_reason AS \"leaveReason\", COALESCE(a.attention, FALSE) AS attention, s.avatar_path IS NOT NULL AS \"hasAvatar\" "
                        + "FROM lesson l JOIN users s ON s.class_id = l.class_id AND s.role = 'STUDENT' AND NOT s.graduated "
                        + "LEFT JOIN lesson_attendance a ON a.lesson_id = l.id AND a.student_id = s.id "
                        + "WHERE l.id = :l ORDER BY s.student_no NULLS LAST, s.name", p("l", lessonId));
    }

    /** 教师修改某学生本堂课记录：status/groupNo/score/comment（请假、调整分组、课堂评价）。 */
    @Transactional
    public void updateAttendance(Long teacherId, Long lessonId, Long studentId, Map<String, Object> f) {
        requireTeacher(teacherId, lessonId);
        ensureStudentOfLesson(lessonId, studentId);
        Object status = f.get("status");
        if (status != null && !"PRESENT".equals(status) && !"ABSENT".equals(status) && !"LEAVE".equals(status)) {
            throw new IllegalArgumentException("无效的考勤状态");
        }
        jdbc.update("INSERT INTO lesson_attendance(lesson_id, student_id, status) VALUES (:l, :s, COALESCE(:st, 'ABSENT')) "
                + "ON CONFLICT (lesson_id, student_id) DO NOTHING", p("l", lessonId, "s", studentId, "st", status));
        if (status != null) {
            jdbc.update("UPDATE lesson_attendance SET status = :st WHERE lesson_id = :l AND student_id = :s",
                    p("st", status, "l", lessonId, "s", studentId));
        }
        if (f.containsKey("groupNo")) {
            Object g = f.get("groupNo");
            jdbc.update("UPDATE lesson_attendance SET group_no = :g WHERE lesson_id = :l AND student_id = :s",
                    p("g", g == null ? 0 : ((Number) g).intValue(), "l", lessonId, "s", studentId));
        }
        if (f.containsKey("score")) {
            Object sc = f.get("score");
            jdbc.update("UPDATE lesson_attendance SET score = :v WHERE lesson_id = :l AND student_id = :s",
                    p("v", sc == null || "".equals(sc) ? null : ((Number) sc).intValue(), "l", lessonId, "s", studentId));
        }
        if (f.containsKey("comment")) {
            jdbc.update("UPDATE lesson_attendance SET comment = :v WHERE lesson_id = :l AND student_id = :s",
                    p("v", f.get("comment"), "l", lessonId, "s", studentId));
        }
        if (f.containsKey("leaveReason")) {
            Object r = f.get("leaveReason");
            String reason = r == null ? null : String.valueOf(r).trim();
            if (reason != null && reason.length() > 50) throw new IllegalArgumentException("请假原因不超过 50 字");
            jdbc.update("UPDATE lesson_attendance SET leave_reason = :v WHERE lesson_id = :l AND student_id = :s",
                    p("v", reason == null || reason.isEmpty() ? null : reason, "l", lessonId, "s", studentId));
        }
        if (f.containsKey("attention")) {
            jdbc.update("UPDATE lesson_attendance SET attention = :v WHERE lesson_id = :l AND student_id = :s",
                    p("v", Boolean.TRUE.equals(f.get("attention")), "l", lessonId, "s", studentId));
        }
    }

    /** 按分组模型重新分组（原 viewcourseloadgroup：按学生所坐机位载入分组）。 */
    @Transactional
    public List<Map<String, Object>> loadGroups(Long teacherId, Long lessonId, Long groupModelId) {
        requireTeacher(teacherId, lessonId);
        jdbc.update("UPDATE lesson SET group_model_id = :g WHERE id = :l", p("g", groupModelId, "l", lessonId));
        Map<String, Integer> map = lab.groupNoByPcName(groupModelId);
        for (Map<String, Object> a : jdbc.queryForList(
                "SELECT student_id, pc_name FROM lesson_attendance WHERE lesson_id = :l", p("l", lessonId))) {
            Integer g = a.get("pc_name") == null ? null : map.get(a.get("pc_name"));
            jdbc.update("UPDATE lesson_attendance SET group_no = :g WHERE lesson_id = :l AND student_id = :s",
                    p("g", g == null ? 0 : g, "l", lessonId, "s", a.get("student_id")));
        }
        return attendance(lessonId);
    }

    /** 座位图：班级机房座位 + 每个机位上当前登录的学生。 */
    @Transactional(readOnly = true)
    public Map<String, Object> seatMap(Long lessonId) {
        Map<String, Object> l = get(lessonId);
        Map<String, Object> room = lab.seatMapForClass(((Number) l.get("classId")).longValue());
        Map<String, Object> out = new HashMap<>();
        out.put("room", room);
        out.put("attendance", attendance(lessonId));
        return out;
    }

    // ================= 学生 =================

    /** 学生所在班级当前进行中的课堂（无则 null）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> currentLessonForStudent(Long studentId) {
        List<Map<String, Object>> l = jdbc.queryForList(
                "SELECT " + LESSON_COLS + "JOIN users st ON st.class_id = l.class_id WHERE st.id = :s AND l.status = 'ACTIVE'",
                p("s", studentId));
        if (l.isEmpty()) return null;
        Map<String, Object> m = new HashMap<>(l.get(0));
        List<Map<String, Object>> a = jdbc.queryForList(
                "SELECT status, group_no AS \"groupNo\", pc_name AS \"pcName\" FROM lesson_attendance WHERE lesson_id = :l AND student_id = :s",
                p("l", m.get("id"), "s", studentId));
        if (!a.isEmpty()) {
            Map<String, Object> me = new HashMap<>(a.get(0));
            int g = ((Number) me.get("groupNo")).intValue();
            me.put("groupLabel", groupLabel(((Number) m.get("id")).longValue(), g));
            m.put("myAttendance", me);
        } else {
            m.put("myAttendance", null);
        }
        return m;
    }

    /** 进课（签到）：记录机位/IP/时间，按分组模型确定小组。 */
    @Transactional
    public Map<String, Object> join(Long studentId, Long lessonId, String ip) {
        Map<String, Object> l = requireStudent(studentId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        Map<String, Object> ws = lab.workstationByIp(ip);
        String pc = (String) ws.get("pcName");
        Integer group = 0;
        // 加载历史分组时记下的预分组优先，其次按机位分组模型
        List<Integer> preset = jdbc.queryForList("SELECT group_no FROM lesson_group_preset WHERE lesson_id = :l AND student_id = :s",
                p("l", lessonId, "s", studentId), Integer.class);
        if (!preset.isEmpty()) {
            group = preset.get(0);
        } else if (l.get("groupModelId") != null && pc != null) {
            Integer g = lab.groupNoByPcName(((Number) l.get("groupModelId")).longValue()).get(pc);
            if (g != null) group = g;
        }
        jdbc.update("INSERT INTO lesson_attendance(lesson_id, student_id, status, pc_name, ip_address, joined_at, group_no) "
                        + "VALUES (:l, :s, 'PRESENT', :pc, :ip, now(), :g) "
                        + "ON CONFLICT (lesson_id, student_id) DO UPDATE SET status = 'PRESENT', pc_name = EXCLUDED.pc_name, "
                        + "ip_address = EXCLUDED.ip_address, joined_at = COALESCE(lesson_attendance.joined_at, EXCLUDED.joined_at), "
                        + "group_no = CASE WHEN lesson_attendance.group_no = 0 THEN EXCLUDED.group_no ELSE lesson_attendance.group_no END",
                p("l", lessonId, "s", studentId, "pc", pc, "ip", ip, "g", group));
        return currentLessonForStudent(studentId);
    }

    /** 学生可见的课堂页面（开放的）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentPages(Long studentId, Long lessonId) {
        requireStudent(studentId, lessonId);
        String role = roleOf(lessonId, studentId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> pg : lessonPages(lessonId)) {
            // 按小组角色显示的页面：只给对应角色的组看（门铃组看不到显示组的任务）
            if (Boolean.TRUE.equals(pg.get("open")) && visibleToRole(pg.get("visibleRoles"), role)) out.add(pg);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentPage(Long studentId, Long lessonId, Long pageId) {
        requireStudent(studentId, lessonId);
        String role = roleOf(lessonId, studentId);
        for (Map<String, Object> pg : lessonPages(lessonId)) {
            if (((Number) pg.get("id")).longValue() == pageId && Boolean.TRUE.equals(pg.get("open"))) {
                if (!visibleToRole(pg.get("visibleRoles"), role)) throw new IllegalArgumentException("这个页面不是给你们组的");
                return renderForStudent(lessonId, studentId, courses.page(pageId), null);
            }
        }
        throw new IllegalArgumentException("该页面未开放");
    }

    /** 学生打开页面（带上浏览器访问平台用的地址，{{服务器IP}} 替换成它）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> studentPage(Long studentId, Long lessonId, Long pageId, String serverHost) {
        Map<String, Object> pg = studentPage(studentId, lessonId, pageId);
        return renderPage(lessonId, pg, serverHost);
    }

    /**
     * 学生看的页面：先替换和本组有关的占位符——{{组名}}（如 门铃3组）、{{组号}}（3）、{{角色}}（门铃）、{{配对号}}（3）；
     * 没分组或组没有设置时，配对号退回组号、角色为空，这样主题写成 {{MQTT项目}}/Bell{{配对号}} 也不会断。
     */
    public Map<String, Object> renderForStudent(Long lessonId, Long studentId, Map<String, Object> page, String serverHost) {
        Object c = page.get("content");
        Map<String, Object> pg = page;
        if (c instanceof String && ((String) c).contains("{{")) {
            int g = groupNoOf(lessonId, studentId);
            Map<String, Object> e = g > 0 ? groupInfo(lessonId).get(g) : null;
            String label = e == null ? "（还没分组）" : (String) e.get("label");
            String role = e == null || e.get("role") == null ? "" : (String) e.get("role");
            String pair = e != null && e.get("pairNo") != null ? String.valueOf(e.get("pairNo")) : (g > 0 ? String.valueOf(g) : "0");
            String content = ((String) c).replace("{{组名}}", label).replace("{{组号}}", String.valueOf(g))
                    .replace("{{角色}}", role).replace("{{配对号}}", pair);
            pg = new HashMap<>(page);
            pg.put("content", content);
        }
        return renderPage(lessonId, pg, serverHost);
    }

    /** 老师在课堂控制台看页面：和学生看到的一样替换占位符。 */
    @Transactional(readOnly = true)
    public Map<String, Object> teacherPage(Long teacherId, Long lessonId, Long pageId, String serverHost) {
        requireTeacher(teacherId, lessonId);
        return renderPage(lessonId, requirePageOfLesson(lessonId, pageId), serverHost);
    }

    // ================= MQTT 项目名与页面占位符 =================

    private static final String CN_DIGITS = "〇一二三四五六七八九";

    /** 班级号：年级汉字（一~九）加班名里的数字，如“八(1)班”→ 81、“七（12）班”→ 712；认不出时用 C+班级编号。 */
    public static String classCode(String className, Long classId) {
        StringBuilder sb = new StringBuilder();
        if (className != null) {
            for (char ch : className.toCharArray()) {
                int i = CN_DIGITS.indexOf(ch);
                if (i > 0) { sb.append(i); break; }
            }
            for (char ch : className.toCharArray()) if (ch >= '0' && ch <= '9') sb.append(ch);
            if (sb.length() == 0) {
                for (char ch : className.toCharArray()) if (ch >= '０' && ch <= '９') sb.append((char) (ch - '０' + '0'));
            }
        }
        return sb.length() == 0 ? "C" + classId : sb.toString();
    }

    /** 本节课的 MQTT 项目名：B + 班级号 + _月日_时分，如 B81_1115_2029（八(1)班 11月15日 20:29 开课）；同一分钟重复开课加字母。 */
    private void assignMqttProject(Long lessonId, Long classId) {
        List<String> names = jdbc.queryForList("SELECT name FROM klass WHERE id = :k", p("k", classId), String.class);
        String base = "B" + classCode(names.isEmpty() ? null : names.get(0), classId) + "_"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd_HHmm"));
        String name = base;
        for (char c = 'A'; c <= 'Z'; c++) {
            Long used = jdbc.queryForObject("SELECT COUNT(*) FROM lesson WHERE mqtt_project = :p", p("p", name), Long.class);
            if (used == null || used == 0) break;
            name = base + c;
        }
        jdbc.update("UPDATE lesson SET mqtt_project = :p WHERE id = :l", p("p", name, "l", lessonId));
    }

    /**
     * 页面正文里的占位符：
     * {{MQTT项目}} 本节课的 MQTT 项目名；{{服务器IP}} 学生访问平台用的地址（就是内置 MQTT 服务器的地址）；
     * {{媒体:文件名}} 课程包 media/ 里的文件（操作视频等）的下载地址。
     */
    public Map<String, Object> renderPage(Long lessonId, Map<String, Object> page, String serverHost) {
        Object c = page.get("content");
        if (!(c instanceof String) || !((String) c).contains("{{")) return page;
        Map<String, Object> l = get(lessonId);
        String content = (String) c;
        Object proj = l.get("mqttProject");
        content = content.replace("{{MQTT项目}}", proj == null ? "（本节课没有 MQTT 项目，请老师重新开课）" : proj.toString());
        if (serverHost != null && !serverHost.isEmpty()) content = content.replace("{{服务器IP}}", serverHost);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[{][{]媒体:([^}]+)[}][}]").matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String url = "/api/classroom/courses/" + l.get("courseId") + "/media/" + encode(m.group(1).trim());
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(url));
        }
        m.appendTail(sb);
        Map<String, Object> out = new HashMap<>(page);
        out.put("content", sb.toString());
        return out;
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    /** 学习记录（原 coursesturecord / LearningRecord）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentRecords(Long studentId) {
        return jdbc.queryForList(
                "SELECT l.id AS \"lessonId\", c.name AS \"courseName\", u.name AS \"teacherName\", l.started_at AS \"startedAt\", "
                        + "l.status AS \"lessonStatus\", a.status, a.pc_name AS \"pcName\", a.joined_at AS \"joinedAt\", a.group_no AS \"groupNo\", "
                        + "a.score, a.comment FROM lesson_attendance a JOIN lesson l ON l.id = a.lesson_id "
                        + "JOIN course c ON c.id = l.course_id JOIN users u ON u.id = l.teacher_id "
                        + "WHERE a.student_id = :s ORDER BY l.started_at DESC", p("s", studentId));
    }

    /** 学生是否属于该课堂的班级（进行中或已结束均可，用于查看历史）。返回课堂信息。 */
    public Map<String, Object> requireStudent(Long studentId, Long lessonId) {
        Map<String, Object> l = get(lessonId);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = :s AND class_id = :k",
                p("s", studentId, "k", l.get("classId")), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("你不在该课堂的班级");
        return l;
    }

    /** 学生在该课堂的分组号（0 表示未分组）。 */
    @Transactional(readOnly = true)
    public int groupNoOf(Long lessonId, Long studentId) {
        List<Integer> l = jdbc.queryForList("SELECT group_no FROM lesson_attendance WHERE lesson_id = :l AND student_id = :s",
                p("l", lessonId, "s", studentId), Integer.class);
        return l.isEmpty() ? 0 : l.get(0);
    }

    // ================= 小组名、角色与配对 =================

    /**
     * 本节课各组的角色、配对号和显示名（只含有设置的组和 1..组数）：
     * name（老师手改）优先；否则有角色和配对号时为“门铃1组”，同一角色同一配对号有几个组时后面的加“·2”“·3”；否则“第N组”。
     */
    @Transactional(readOnly = true)
    public Map<Integer, Map<String, Object>> groupInfo(Long lessonId) {
        Map<Integer, Map<String, Object>> out = new java.util.TreeMap<>();
        Integer count = jdbc.queryForObject("SELECT GREATEST(l.group_count, COALESCE((SELECT MAX(group_no) FROM lesson_attendance a WHERE a.lesson_id = l.id), 0)) "
                + "FROM lesson l WHERE l.id = :l", p("l", lessonId), Integer.class);
        for (int g = 1; g <= (count == null ? 0 : count); g++) out.put(g, groupEntry(g, null, null, null));
        for (Map<String, Object> r : jdbc.queryForList("SELECT group_no, role, pair_no, name FROM lesson_group WHERE lesson_id = :l ORDER BY group_no",
                p("l", lessonId))) {
            int g = ((Number) r.get("group_no")).intValue();
            out.put(g, groupEntry(g, (String) r.get("role"), r.get("pair_no") == null ? null : ((Number) r.get("pair_no")).intValue(), (String) r.get("name")));
        }
        Map<String, Integer> seen = new HashMap<>();
        for (Map<String, Object> e : out.values()) {
            if (e.get("name") != null || e.get("role") == null || e.get("pairNo") == null) continue;
            String key = e.get("role") + "#" + e.get("pairNo");
            int n = seen.merge(key, 1, Integer::sum);
            e.put("label", e.get("role") + String.valueOf(e.get("pairNo")) + "组" + (n > 1 ? "·" + n : ""));
        }
        return out;
    }

    private static Map<String, Object> groupEntry(int g, String role, Integer pairNo, String name) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("groupNo", g);
        m.put("role", role == null || role.trim().isEmpty() ? null : role.trim());
        m.put("pairNo", pairNo);
        m.put("name", name == null || name.trim().isEmpty() ? null : name.trim());
        m.put("label", m.get("name") != null ? m.get("name") : "第" + g + "组");
        return m;
    }

    /** 组的显示名（0 = 未分组 → null）。 */
    public String groupLabel(Long lessonId, int groupNo) {
        if (groupNo <= 0) return null;
        Map<String, Object> e = groupInfo(lessonId).get(groupNo);
        return e == null ? "第" + groupNo + "组" : (String) e.get("label");
    }

    /** 学生所在组的角色（未分组或组没有角色 → null）。 */
    @Transactional(readOnly = true)
    public String roleOf(Long lessonId, Long studentId) {
        int g = groupNoOf(lessonId, studentId);
        if (g <= 0) return null;
        List<String> r = jdbc.queryForList("SELECT role FROM lesson_group WHERE lesson_id = :l AND group_no = :g AND role IS NOT NULL",
                p("l", lessonId, "g", g), String.class);
        return r.isEmpty() || r.get(0).trim().isEmpty() ? null : r.get(0).trim();
    }

    /** 页面对某角色是否可见：visibleRoles 为空 = 全班；否则学生组的角色必须在列表里（没有角色的看不到）。 */
    public static boolean visibleToRole(Object visibleRoles, String role) {
        if (visibleRoles == null || String.valueOf(visibleRoles).trim().isEmpty()) return true;
        if (role == null) return false;
        for (String s : String.valueOf(visibleRoles).split(",")) if (s.trim().equals(role)) return true;
        return false;
    }

    /** 学生能看到的课程页 id（只按角色过滤，不看本堂开没开）；成绩开网用它只算本组角色的页。 */
    @Transactional(readOnly = true)
    public boolean pageVisibleToStudent(Long lessonId, Long studentId, Long pageId) {
        List<String> r = jdbc.queryForList("SELECT COALESCE(visible_roles, '') FROM course_page WHERE id = :p", p("p", pageId), String.class);
        return r.isEmpty() || visibleToRole(r.get(0), roleOf(lessonId, studentId));
    }

    /** 自动评分规则里按学生替换的占位符：{{MQTT项目}} {{组号}} {{配对号}} {{角色}} {{组名}}（没分组时配对号退回组号）。 */
    @Transactional(readOnly = true)
    public Map<String, String> gradingVars(Long lessonId, Long studentId) {
        Map<String, String> v = new HashMap<>();
        Object proj = get(lessonId).get("mqttProject");
        v.put("MQTT项目", proj == null ? "" : proj.toString());
        int g = groupNoOf(lessonId, studentId);
        Map<String, Object> e = g > 0 ? groupInfo(lessonId).get(g) : null;
        v.put("组号", String.valueOf(g));
        v.put("配对号", e != null && e.get("pairNo") != null ? String.valueOf(e.get("pairNo")) : String.valueOf(g));
        v.put("角色", e == null || e.get("role") == null ? "" : (String) e.get("role"));
        v.put("组名", e == null ? "" : (String) e.get("label"));
        return v;
    }

    /** 页面是否属于该课堂的课程。 */
    public Map<String, Object> requirePageOfLesson(Long lessonId, Long pageId) {
        Map<String, Object> l = get(lessonId);
        Map<String, Object> pg = courses.page(pageId);
        if (!l.get("courseId").equals(pg.get("courseId"))) throw new IllegalArgumentException("页面不属于该课堂");
        return pg;
    }

    private void ensureStudentOfLesson(Long lessonId, Long studentId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users s JOIN lesson l ON l.class_id = s.class_id WHERE l.id = :l AND s.id = :s",
                p("l", lessonId, "s", studentId), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("学生不属于该课堂");
    }

    // ================= 管理员 =================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> activeLessons() {
        return jdbc.queryForList("SELECT " + LESSON_COLS + "WHERE l.status = 'ACTIVE' ORDER BY l.started_at DESC", p());
    }

    /** 管理员强制下课（原 unlockclass 解除班级当前课程）。 */
    @Transactional
    public void forceEnd(Long lessonId) {
        Map<String, Object> l = get(lessonId);
        if ("ACTIVE".equals(l.get("status"))) endInternal(lessonId, null);
    }

    /** 课程附件可读性：教师本人/共享；学生所在班级上过该课程。 */
    @Transactional(readOnly = true)
    public boolean canReadCourse(Long userId, String role, Long courseId) {
        if ("TEACHER".equals(role)) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = :c AND (teacher_id = :u OR shared)",
                    p("c", courseId, "u", userId), Long.class);
            return n != null && n > 0;
        }
        if ("STUDENT".equals(role)) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM lesson l JOIN users s ON s.class_id = l.class_id "
                    + "WHERE l.course_id = :c AND s.id = :u", p("c", courseId, "u", userId), Long.class);
            return n != null && n > 0;
        }
        return "ADMIN".equals(role);
    }
}
