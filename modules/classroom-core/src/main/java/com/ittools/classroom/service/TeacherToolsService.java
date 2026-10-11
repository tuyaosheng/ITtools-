package com.ittools.classroom.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 教师端工具：我的班级学生（原 stuinfoview / stupsw 重置学生密码 / teacheraddstu 添加学生）、
 * 学生课堂记录查询（stulog / QueryResults）、课表（lessonsheet）。
 */
@Service
public class TeacherToolsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ClassBindingService bindings;
    private final PasswordEncoder encoder;

    public TeacherToolsService(NamedParameterJdbcTemplate jdbc, ClassBindingService bindings, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.bindings = bindings;
        this.encoder = encoder;
    }

    private void requireClass(Long teacherId, Long classId) {
        if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权管理该班级");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> students(Long teacherId, Long classId) {
        requireClass(teacherId, classId);
        return jdbc.queryForList(
                "SELECT s.id, s.name, s.student_no AS \"studentNo\", s.xjh, s.enabled, "
                        + "(SELECT COUNT(*) FROM lesson_attendance a WHERE a.student_id = s.id AND a.status = 'PRESENT') AS \"presentCount\", "
                        + "(SELECT COUNT(*) FROM lesson_attendance a WHERE a.student_id = s.id AND a.status = 'ABSENT') AS \"absentCount\", "
                        + "(SELECT ROUND(AVG(a.score), 1) FROM lesson_attendance a WHERE a.student_id = s.id AND a.score IS NOT NULL) AS \"avgScore\" "
                        + "FROM users s WHERE s.class_id = :k AND s.role = 'STUDENT' AND NOT s.graduated "
                        + "ORDER BY s.student_no NULLS LAST, s.name", p("k", classId));
    }

    @Transactional
    public void resetStudentPassword(Long teacherId, Long studentId, String password) {
        if (password == null || password.length() < 4) throw new IllegalArgumentException("密码至少 4 位");
        requireClass(teacherId, classOf(studentId));
        jdbc.update("UPDATE users SET password_hash = :h WHERE id = :s", p("h", encoder.encode(password), "s", studentId));
    }

    @Transactional
    public Long addStudent(Long teacherId, Long classId, String name, String studentNo, String password) {
        requireClass(teacherId, classId);
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("姓名不能为空");
        Long dup = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE class_id = :k AND name = :n AND role = 'STUDENT'",
                p("k", classId, "n", name.trim()), Long.class);
        if (dup != null && dup > 0) throw new IllegalArgumentException("本班已有同名学生");
        String pw = password == null || password.isEmpty() ? "123456" : password;
        return jdbc.queryForObject(
                "INSERT INTO users(role, name, student_no, password_hash, class_id, enroll_year_id) "
                        + "SELECT 'STUDENT', :n, :no, :h, k.id, k.school_year_id FROM klass k WHERE k.id = :k RETURNING id",
                p("n", name.trim(), "no", studentNo, "h", encoder.encode(pw), "k", classId), Long.class);
    }

    /** 学生的全部课堂记录（教师查询）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentRecords(Long teacherId, Long studentId) {
        requireClass(teacherId, classOf(studentId));
        return jdbc.queryForList(
                "SELECT l.id AS \"lessonId\", c.name AS \"courseName\", u.name AS \"teacherName\", l.started_at AS \"startedAt\", "
                        + "a.status, a.pc_name AS \"pcName\", a.joined_at AS \"joinedAt\", a.score, a.comment "
                        + "FROM lesson_attendance a JOIN lesson l ON l.id = a.lesson_id JOIN course c ON c.id = l.course_id "
                        + "JOIN users u ON u.id = l.teacher_id WHERE a.student_id = :s ORDER BY l.started_at DESC", p("s", studentId));
    }

    private Long classOf(Long studentId) {
        List<Long> l = jdbc.queryForList("SELECT class_id FROM users WHERE id = :s AND role = 'STUDENT'", p("s", studentId), Long.class);
        if (l.isEmpty() || l.get(0) == null) throw new IllegalArgumentException("学生不存在");
        return l.get(0);
    }

    // ---- 课表 ----
    @Transactional(readOnly = true)
    public List<Map<String, Object>> timetable(Long teacherId) {
        return jdbc.queryForList("SELECT period, weekday, content FROM timetable_cell WHERE teacher_id = :t", p("t", teacherId));
    }

    @Transactional
    public List<Map<String, Object>> saveTimetable(Long teacherId, List<Map<String, Object>> cells) {
        jdbc.update("DELETE FROM timetable_cell WHERE teacher_id = :t", p("t", teacherId));
        for (Map<String, Object> c : cells) {
            Object content = c.get("content");
            if (content == null || String.valueOf(content).trim().isEmpty()) continue;
            jdbc.update("INSERT INTO timetable_cell(teacher_id, period, weekday, content) VALUES (:t, :p, :w, :c)",
                    p("t", teacherId, "p", ((Number) c.get("period")).intValue(), "w", ((Number) c.get("weekday")).intValue(),
                            "c", String.valueOf(content).trim()));
        }
        return timetable(teacherId);
    }
}
