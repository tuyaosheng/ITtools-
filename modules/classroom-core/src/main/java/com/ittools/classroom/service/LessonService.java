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
                    + "gm.name AS \"groupModelName\", l.summary, l.term_id AS \"termId\", "
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
        jdbc.update("INSERT INTO lesson_attendance(lesson_id, student_id, status) "
                + "SELECT :l, s.id, 'ABSENT' FROM users s JOIN lesson l ON l.class_id = s.class_id "
                + "WHERE l.id = :l AND s.role = 'STUDENT' AND NOT s.graduated "
                + "AND NOT EXISTS (SELECT 1 FROM lesson_attendance a WHERE a.lesson_id = :l AND a.student_id = s.id)", p("l", lessonId));
        jdbc.update("UPDATE lesson SET status = 'ENDED', ended_at = now(), summary = COALESCE(:s, summary) WHERE id = :l",
                p("l", lessonId, "s", summary));
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
                        + "COALESCE(s.is_open, p.student_visible) AS \"open\" "
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

    /** 全班学生考勤（含未进课学生，状态 null 表示尚未进课）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> attendance(Long lessonId) {
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
        m.put("myAttendance", a.isEmpty() ? null : a.get(0));
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
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> pg : lessonPages(lessonId)) {
            if (Boolean.TRUE.equals(pg.get("open"))) out.add(pg);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentPage(Long studentId, Long lessonId, Long pageId) {
        requireStudent(studentId, lessonId);
        for (Map<String, Object> pg : lessonPages(lessonId)) {
            if (((Number) pg.get("id")).longValue() == pageId && Boolean.TRUE.equals(pg.get("open"))) {
                return courses.page(pageId);
            }
        }
        throw new IllegalArgumentException("该页面未开放");
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
