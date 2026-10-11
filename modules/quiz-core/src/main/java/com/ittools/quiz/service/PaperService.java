package com.ittools.quiz.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ittools.platform.db.Sql.p;

/** 试卷组卷（原 it_testoperation + it_testoperlist）。 */
@Service
public class PaperService {
    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionService questions;

    public PaperService(NamedParameterJdbcTemplate jdbc, QuestionService questions) {
        this.jdbc = jdbc;
        this.questions = questions;
    }

    private static final String COLS = "pa.id, pa.name, pa.description, pa.shared, pa.teacher_id AS \"teacherId\", u.name AS \"teacherName\", "
            + "pa.created_at AS \"createdAt\", (SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = pa.id) AS \"questionCount\", "
            + "(SELECT COALESCE(SUM(pq.score), 0) FROM paper_question pq WHERE pq.paper_id = pa.id) AS \"totalScore\" ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long teacherId) {
        return jdbc.queryForList("SELECT " + COLS + "FROM paper pa JOIN users u ON u.id = pa.teacher_id "
                + "WHERE pa.teacher_id = :t OR pa.shared ORDER BY (pa.teacher_id = :t) DESC, pa.id DESC", p("t", teacherId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + COLS + "FROM paper pa JOIN users u ON u.id = pa.teacher_id WHERE pa.id = :id",
                p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("试卷不存在");
        Map<String, Object> m = l.get(0);
        m.put("questions", paperQuestions(id, true));
        return m;
    }

    /** 试卷题目（withAnswer=false 时去掉答案和解析，供学生作答）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> paperQuestions(Long paperId, boolean withAnswer) {
        List<Map<String, Object>> l = questions.decorate(jdbc.queryForList(
                "SELECT " + QuestionService.COLS + ", pq.seq, pq.score AS \"paperScore\" FROM paper_question pq "
                        + "JOIN question q ON q.id = pq.question_id JOIN users u ON u.id = q.teacher_id "
                        + "WHERE pq.paper_id = :p ORDER BY pq.seq", p("p", paperId)));
        for (Map<String, Object> q : l) {
            q.put("score", q.remove("paperScore"));
            if ("FILL".equals(q.get("qtype"))) {
                q.put("blankCount", ((List<?>) q.get("answer")).size());
            }
            if (!withAnswer) {
                q.remove("answer");
                q.remove("analysis");
            }
        }
        return l;
    }

    public void requireReadable(Long teacherId, Long paperId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM paper WHERE id = :id AND (teacher_id = :t OR shared)",
                p("id", paperId, "t", teacherId), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("试卷不存在或未共享");
    }

    @Transactional
    public Map<String, Object> save(Long teacherId, Long id, String name, String description, boolean shared,
                                    List<Map<String, Object>> items) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("试卷名称不能为空");
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO paper(teacher_id, name, description, shared) VALUES (:t, :n, :d, :s) RETURNING id",
                    p("t", teacherId, "n", name.trim(), "d", description, "s", shared), Long.class);
        } else {
            requireOwner(teacherId, id);
            jdbc.update("UPDATE paper SET name = :n, description = :d, shared = :s WHERE id = :id",
                    p("n", name.trim(), "d", description, "s", shared, "id", id));
            jdbc.update("DELETE FROM paper_question WHERE paper_id = :id", p("id", id));
        }
        Set<Long> seen = new HashSet<>();
        int seq = 1;
        for (Map<String, Object> it : items) {
            Long qid = ((Number) it.get("questionId")).longValue();
            if (!seen.add(qid)) continue;
            Long ok = jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE id = :q AND (teacher_id = :t OR shared)",
                    p("q", qid, "t", teacherId), Long.class);
            if (ok == null || ok == 0) throw new IllegalArgumentException("题目不可用：" + qid);
            Object sc = it.get("score");
            jdbc.update("INSERT INTO paper_question(paper_id, question_id, seq, score) "
                            + "SELECT :p, :q, :s, COALESCE(:sc, q.score) FROM question q WHERE q.id = :q",
                    p("p", id, "q", qid, "s", seq++, "sc", sc == null ? null : ((Number) sc).intValue()));
        }
        return get(id);
    }

    /** 随机组卷：从题库（本人+共享）按题型数量随机抽题追加到试卷。 */
    @Transactional
    public Map<String, Object> randomFill(Long teacherId, Long paperId, String category, Map<String, Integer> counts) {
        requireOwner(teacherId, paperId);
        Integer seq = jdbc.queryForObject("SELECT COALESCE(MAX(seq), 0) FROM paper_question WHERE paper_id = :p", p("p", paperId), Integer.class);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            List<Long> ids = jdbc.queryForList("SELECT id FROM question WHERE (teacher_id = :t OR shared) AND qtype = :qt "
                            + (category == null || category.isEmpty() ? "" : "AND category = :c ")
                            + "AND id NOT IN (SELECT question_id FROM paper_question WHERE paper_id = :p) ORDER BY random() LIMIT :n",
                    p("t", teacherId, "qt", e.getKey(), "c", category, "p", paperId, "n", e.getValue()), Long.class);
            for (Long qid : ids) {
                jdbc.update("INSERT INTO paper_question(paper_id, question_id, seq, score) SELECT :p, id, :s, score FROM question WHERE id = :q",
                        p("p", paperId, "s", ++seq, "q", qid));
            }
        }
        return get(paperId);
    }

    @Transactional
    public void delete(Long teacherId, Long id) {
        requireOwner(teacherId, id);
        Long used = jdbc.queryForObject("SELECT COUNT(*) FROM quiz_test WHERE paper_id = :id", p("id", id), Long.class);
        if (used != null && used > 0) throw new IllegalArgumentException("该试卷已发布过测验，不能删除");
        jdbc.update("DELETE FROM paper WHERE id = :id", p("id", id));
    }

    private void requireOwner(Long teacherId, Long id) {
        List<Long> l = jdbc.queryForList("SELECT teacher_id FROM paper WHERE id = :id", p("id", id), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("试卷不存在");
        if (!l.get(0).equals(teacherId)) throw new IllegalArgumentException("只能修改自己的试卷");
    }

    List<Long> emptyIfNull(List<Long> l) {
        return l == null ? Collections.<Long>emptyList() : l;
    }
}
