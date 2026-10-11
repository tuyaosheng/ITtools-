package com.ittools.classroom.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 班级绑定教师（原系统设置"绑定教师"/it_user.classbinding）：
 * 绑定后该班只对绑定教师可见；未绑定班级所有教师可见。
 */
@Service
public class ClassBindingService {
    private final NamedParameterJdbcTemplate jdbc;

    public ClassBindingService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT k.id AS \"classId\", sy.label || k.name AS \"className\", b.teacher_id AS \"teacherId\", t.name AS \"teacherName\" "
                        + "FROM klass k JOIN school_year sy ON sy.id = k.school_year_id "
                        + "LEFT JOIN class_teacher_binding b ON b.class_id = k.id LEFT JOIN users t ON t.id = b.teacher_id "
                        + "WHERE sy.active ORDER BY sy.year_code DESC, k.display_order, k.name", p());
    }

    @Transactional
    public void bind(Long classId, Long teacherId) {
        if (teacherId == null) {
            jdbc.update("DELETE FROM class_teacher_binding WHERE class_id = :c", p("c", classId));
            return;
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = :t AND role = 'TEACHER'", p("t", teacherId), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("教师不存在");
        jdbc.update("INSERT INTO class_teacher_binding(class_id, teacher_id) VALUES (:c, :t) "
                + "ON CONFLICT (class_id) DO UPDATE SET teacher_id = EXCLUDED.teacher_id", p("c", classId, "t", teacherId));
    }

    /** 教师可见（可开课/可管理）的班级：在读年级中未绑定或绑定给自己的班级。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> classesForTeacher(Long teacherId) {
        return jdbc.queryForList(
                "SELECT k.id, sy.label || k.name AS \"name\", sy.id AS \"yearId\", sy.label AS \"yearLabel\", "
                        + "(SELECT COUNT(*) FROM users u WHERE u.class_id = k.id AND u.role = 'STUDENT' AND NOT u.graduated) AS \"studentCount\", "
                        + "(b.teacher_id IS NOT NULL) AS \"bound\" "
                        + "FROM klass k JOIN school_year sy ON sy.id = k.school_year_id "
                        + "LEFT JOIN class_teacher_binding b ON b.class_id = k.id "
                        + "WHERE sy.active AND (b.teacher_id IS NULL OR b.teacher_id = :t) "
                        + "ORDER BY sy.year_code DESC, k.display_order, k.name", p("t", teacherId));
    }

    @Transactional(readOnly = true)
    public boolean teacherCanUseClass(Long teacherId, Long classId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM klass k LEFT JOIN class_teacher_binding b ON b.class_id = k.id "
                        + "WHERE k.id = :c AND (b.teacher_id IS NULL OR b.teacher_id = :t)", p("c", classId, "t", teacherId), Long.class);
        return n != null && n > 0;
    }
}
