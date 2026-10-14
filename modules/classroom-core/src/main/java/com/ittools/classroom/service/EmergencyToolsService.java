package com.ittools.classroom.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 应急工具（原 teachertools / unlockclass / coursemanage“撤销”）：处理非常规情况。
 * 全部强制下课、消除缺勤记录、撤销课堂（清除该课堂全部学习记录）、强制删除有使用记录的课程。
 */
@Service
public class EmergencyToolsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;

    public EmergencyToolsService(NamedParameterJdbcTemplate jdbc, LessonService lessons) {
        this.jdbc = jdbc;
        this.lessons = lessons;
    }

    /** 全部强制下课：所有进行中的课堂下课（与正常下课一样补记未进课学生为缺席）。 */
    @Transactional
    public int endAll() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM lesson WHERE status = 'ACTIVE'", p(), Long.class);
        for (Long id : ids) lessons.forceEnd(id);
        return ids.size();
    }

    /** 缺勤/请假记录查询（按班级，可选学期）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> absences(Long classId, Long termId) {
        return jdbc.queryForList("SELECT a.lesson_id AS \"lessonId\", a.student_id AS \"studentId\", u.student_no AS \"studentNo\", u.name, "
                + "c.name AS \"courseName\", l.started_at AS \"startedAt\", a.status, a.leave_reason AS \"leaveReason\", t.name AS \"teacherName\" "
                + "FROM lesson_attendance a JOIN lesson l ON l.id = a.lesson_id JOIN users u ON u.id = a.student_id "
                + "JOIN course c ON c.id = l.course_id JOIN users t ON t.id = l.teacher_id "
                + "WHERE l.class_id = :k AND a.status IN ('ABSENT','LEAVE')" + (termId == null ? "" : " AND l.term_id = :term")
                + " ORDER BY l.started_at DESC, u.student_no NULLS LAST", p("k", classId, "term", termId));
    }

    /** 消除缺勤记录：删除选中的缺席/请假记录，该节课不再计入学生的出勤统计。 */
    @Transactional
    public int clearAbsences(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("请选择要消除的记录");
        if (items.size() > 2000) throw new IllegalArgumentException("一次最多消除 2000 条");
        int n = 0;
        for (Map<String, Object> it : items) {
            Object l = it.get("lessonId"), s = it.get("studentId");
            if (!(l instanceof Number) || !(s instanceof Number)) continue;
            n += jdbc.update("DELETE FROM lesson_attendance WHERE lesson_id = :l AND student_id = :s AND status IN ('ABSENT','LEAVE')",
                    p("l", ((Number) l).longValue(), "s", ((Number) s).longValue()));
        }
        return n;
    }

    /** 撤销课堂：删除该课堂及其全部学习记录（考勤、作品、测验、调查、互动等）。教师只能撤销自己的课堂。 */
    @Transactional
    public void revokeLesson(Long teacherId, Long lessonId) {
        if (teacherId != null) lessons.requireTeacher(teacherId, lessonId);
        else lessons.get(lessonId);
        jdbc.update("DELETE FROM lesson WHERE id = :l", p("l", lessonId));
    }

    /** 强制删除有使用记录的课程：先删除该课程的全部课堂及记录，再删除课程。 */
    @Transactional
    public int forceDeleteCourse(Long courseId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = :c", p("c", courseId), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("课程不存在");
        int lessonsDeleted = jdbc.update("DELETE FROM lesson WHERE course_id = :c", p("c", courseId));
        jdbc.update("DELETE FROM course WHERE id = :c", p("c", courseId));
        return lessonsDeleted;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> coursesWithUsage(String keyword) {
        return jdbc.queryForList("SELECT c.id, c.name, u.name AS \"teacherName\", c.created_at AS \"createdAt\", "
                + "(SELECT COUNT(*) FROM lesson l WHERE l.course_id = c.id) AS \"lessonCount\" FROM course c JOIN users u ON u.id = c.teacher_id "
                + "WHERE (CAST(:kw AS TEXT) IS NULL OR c.name ILIKE '%' || CAST(:kw AS TEXT) || '%' OR u.name ILIKE '%' || CAST(:kw AS TEXT) || '%') ORDER BY c.created_at DESC LIMIT 200",
                p("kw", keyword == null || keyword.trim().isEmpty() ? null : keyword.trim()));
    }

    /** 学期修复（原 repairterm）：列出未划分学期的课堂。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lessonsWithoutTerm() {
        return jdbc.queryForList("SELECT l.id, c.name AS \"courseName\", k.name AS \"className\", t.name AS \"teacherName\", l.started_at AS \"startedAt\" "
                + "FROM lesson l JOIN course c ON c.id = l.course_id JOIN klass k ON k.id = l.class_id JOIN users t ON t.id = l.teacher_id "
                + "WHERE l.term_id IS NULL ORDER BY l.started_at DESC LIMIT 1000", p());
    }

    @Transactional
    public int assignTerm(List<Long> lessonIds, Long termId) {
        if (lessonIds == null || lessonIds.isEmpty()) throw new IllegalArgumentException("请选择课堂记录");
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM teaching_term WHERE id = :t", p("t", termId), Long.class);
        if (termId == null || n == null || n == 0) throw new IllegalArgumentException("请选择学期");
        return jdbc.update("UPDATE lesson SET term_id = :t WHERE id IN (:ids) AND term_id IS NULL", p("t", termId, "ids", lessonIds));
    }
}
