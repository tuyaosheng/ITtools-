package com.ittools.interact.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 师生答疑（原 stuquestion 学生提问 / answer 教师答复）。
 * 学生向某位老师提问；老师答复后学生收到系统消息，并可把问答公开给全校同学参考。
 */
@Service
public class QaService {
    private final NamedParameterJdbcTemplate jdbc;
    private final MessageService messages;

    public QaService(NamedParameterJdbcTemplate jdbc, MessageService messages) {
        this.jdbc = jdbc;
        this.messages = messages;
    }

    /**
     * 学生可提问的老师：本班绑定的老师与给本班上过课的老师优先列出（mine=true），其后是其他老师。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> teachersFor(Long studentId) {
        return jdbc.queryForList("SELECT t.id, t.name, (EXISTS (SELECT 1 FROM class_teacher_binding b WHERE b.teacher_id = t.id AND b.class_id = s.class_id) "
                + "OR EXISTS (SELECT 1 FROM lesson l WHERE l.teacher_id = t.id AND l.class_id = s.class_id)) AS mine "
                + "FROM users t CROSS JOIN users s WHERE s.id = :s AND t.role = 'TEACHER' AND t.enabled ORDER BY 3 DESC, t.name", p("s", studentId));
    }

    private static final String COLS = "q.id, q.title, q.content, q.answer, q.answered_at AS \"answeredAt\", q.is_public AS \"isPublic\", "
            + "q.created_at AS \"createdAt\", q.teacher_id AS \"teacherId\", t.name AS \"teacherName\" ";

    @Transactional
    public Map<String, Object> ask(Long studentId, Long teacherId, String title, String content) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("请填写问题标题");
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("请描述你的问题");
        if (title.length() > 200 || content.length() > 2000) throw new IllegalArgumentException("标题不超过 200 字，内容不超过 2000 字");
        Long ok = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = :t AND role = 'TEACHER' AND enabled", p("t", teacherId), Long.class);
        if (ok == null || ok == 0) throw new IllegalArgumentException("请选择要提问的老师");
        Long id = jdbc.queryForObject("INSERT INTO qa_question(student_id, teacher_id, title, content) VALUES (:s, :t, :ti, :c) RETURNING id",
                p("s", studentId, "t", teacherId, "ti", title.trim(), "c", content.trim()), Long.class);
        return jdbc.queryForMap("SELECT " + COLS + "FROM qa_question q JOIN users t ON t.id = q.teacher_id WHERE q.id = :id", p("id", id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(Long studentId) {
        return jdbc.queryForList("SELECT " + COLS + "FROM qa_question q JOIN users t ON t.id = q.teacher_id WHERE q.student_id = :s "
                + "ORDER BY q.id DESC", p("s", studentId));
    }

    /** 公开的问答（不显示提问者姓名，只显示班级）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicList(String keyword) {
        boolean kw = keyword != null && !keyword.trim().isEmpty();
        return jdbc.queryForList("SELECT " + COLS + ", sy.label || k.name AS \"className\" FROM qa_question q JOIN users t ON t.id = q.teacher_id "
                + "JOIN users s ON s.id = q.student_id LEFT JOIN klass k ON k.id = s.class_id LEFT JOIN school_year sy ON sy.id = k.school_year_id "
                + "WHERE q.is_public AND q.answer IS NOT NULL " + (kw ? "AND (q.title ILIKE :kw OR q.content ILIKE :kw) " : "")
                + "ORDER BY q.answered_at DESC LIMIT 200", p("kw", kw ? "%" + keyword.trim() + "%" : null));
    }

    @Transactional
    public void deleteMine(Long studentId, Long id) {
        if (jdbc.update("DELETE FROM qa_question WHERE id = :id AND student_id = :s", p("id", id, "s", studentId)) == 0) {
            throw new IllegalArgumentException("问题不存在");
        }
    }

    // ================= 教师 =================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherList(Long teacherId, boolean pendingOnly) {
        return jdbc.queryForList("SELECT " + COLS + ", s.name AS \"studentName\", sy.label || k.name AS \"className\" "
                + "FROM qa_question q JOIN users t ON t.id = q.teacher_id JOIN users s ON s.id = q.student_id "
                + "LEFT JOIN klass k ON k.id = s.class_id LEFT JOIN school_year sy ON sy.id = k.school_year_id "
                + "WHERE q.teacher_id = :t " + (pendingOnly ? "AND q.answer IS NULL " : "")
                + "ORDER BY (q.answer IS NULL) DESC, q.id DESC LIMIT 300", p("t", teacherId));
    }

    @Transactional
    public void answer(Long teacherId, Long id, String answer, boolean isPublic) {
        if (answer == null || answer.trim().isEmpty()) throw new IllegalArgumentException("请填写答复内容");
        if (answer.length() > 4000) throw new IllegalArgumentException("答复不超过 4000 字");
        List<Map<String, Object>> l = jdbc.queryForList("SELECT student_id, title, answer FROM qa_question WHERE id = :id AND teacher_id = :t",
                p("id", id, "t", teacherId));
        if (l.isEmpty()) throw new IllegalArgumentException("问题不存在");
        Map<String, Object> q = l.get(0);
        jdbc.update("UPDATE qa_question SET answer = :a, is_public = :p, answered_at = now() WHERE id = :id",
                p("a", answer.trim(), "p", isPublic, "id", id));
        String verb = q.get("answer") == null ? "回复" : "更新了答复";
        messages.notify(teacherId, ((Number) q.get("student_id")).longValue(), "老师" + verb + "你的问题：" + q.get("title"),
                "可在「师生答疑」中查看完整答复。");
    }

    @Transactional
    public void teacherDelete(Long teacherId, Long id) {
        if (jdbc.update("DELETE FROM qa_question WHERE id = :id AND teacher_id = :t", p("id", id, "t", teacherId)) == 0) {
            throw new IllegalArgumentException("问题不存在");
        }
    }

    @Transactional(readOnly = true)
    public int pendingCount(Long teacherId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM qa_question WHERE teacher_id = :t AND answer IS NULL", p("t", teacherId), Integer.class);
        return n == null ? 0 : n;
    }
}
