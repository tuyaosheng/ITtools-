package com.ittools.quiz.service;

import com.ittools.classroom.service.ClassBindingService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 课后刷题（原 testlist / exercises）：指定刷题题库、抽题生成练习卷、刷题记录；
 * 以及教师端的学生刷题情况、题库错误率报表、题库查重。
 */
@Service
public class PracticeService {
    private static final int MAX_DRAW = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionService questions;
    private final PaperService papers;
    private final ClassBindingService bindings;

    public PracticeService(NamedParameterJdbcTemplate jdbc, QuestionService questions, PaperService papers, ClassBindingService bindings) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.papers = papers;
        this.bindings = bindings;
    }

    // ================= 教师：指定刷题题库 =================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sets(Long teacherId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT s.id, s.name, s.paper_id AS \"paperId\", p.name AS \"paperName\", s.enabled, s.created_at AS \"createdAt\", "
                + "(SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = s.paper_id) AS \"questionCount\", "
                + "(SELECT COUNT(*) FROM practice_session ps WHERE ps.set_id = s.id AND ps.submitted_at IS NOT NULL) AS \"sessionCount\" "
                + "FROM practice_set s JOIN paper p ON p.id = s.paper_id WHERE s.teacher_id = :t ORDER BY s.id DESC", p("t", teacherId));
        for (Map<String, Object> s : l) {
            s.put("classIds", jdbc.queryForList("SELECT class_id FROM practice_set_class WHERE set_id = :s", p("s", s.get("id")), Long.class));
        }
        return l;
    }

    @Transactional
    public Map<String, Object> saveSet(Long teacherId, Long id, String name, Long paperId, List<Long> classIds, boolean enabled) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("请填写题库名称");
        if (paperId == null) throw new IllegalArgumentException("请选择试卷");
        papers.requireReadable(teacherId, paperId);
        if (classIds.isEmpty()) throw new IllegalArgumentException("请选择班级");
        for (Long k : classIds) if (!bindings.teacherCanUseClass(teacherId, k)) throw new IllegalArgumentException("无权向该班级开放");
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO practice_set(teacher_id, name, paper_id, enabled) VALUES (:t, :n, :p, :e) RETURNING id",
                    p("t", teacherId, "n", name.trim(), "p", paperId, "e", enabled), Long.class);
        } else {
            if (jdbc.update("UPDATE practice_set SET name = :n, paper_id = :p, enabled = :e WHERE id = :id AND teacher_id = :t",
                    p("n", name.trim(), "p", paperId, "e", enabled, "id", id, "t", teacherId)) == 0) {
                throw new IllegalArgumentException("刷题题库不存在");
            }
            jdbc.update("DELETE FROM practice_set_class WHERE set_id = :s", p("s", id));
        }
        for (Long k : classIds) jdbc.update("INSERT INTO practice_set_class(set_id, class_id) VALUES (:s, :k)", p("s", id, "k", k));
        for (Map<String, Object> s : sets(teacherId)) if (((Number) s.get("id")).longValue() == id) return s;
        throw new IllegalStateException("保存失败");
    }

    @Transactional
    public void deleteSet(Long teacherId, Long id) {
        jdbc.update("DELETE FROM practice_set WHERE id = :id AND teacher_id = :t", p("id", id, "t", teacherId));
    }

    /** 把一次测验/作业设为该班的课后刷题题库（原“自动将课堂里的测试设置为课后指定刷题题库”）。 */
    @Transactional
    public Map<String, Object> setFromTest(Long teacherId, Long testId) {
        Map<String, Object> t = jdbc.queryForMap("SELECT teacher_id, paper_id, class_id, title FROM quiz_test WHERE id = :id", p("id", testId));
        if (!teacherId.equals(((Number) t.get("teacher_id")).longValue())) throw new IllegalArgumentException("不是你发布的测验");
        List<Long> classes = new ArrayList<>();
        classes.add(((Number) t.get("class_id")).longValue());
        return saveSet(teacherId, null, t.get("title") + "（刷题）", ((Number) t.get("paper_id")).longValue(), classes, true);
    }

    // ================= 学生：抽题刷题 =================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> availableSets(Long studentId) {
        return jdbc.queryForList("SELECT s.id, s.name, u.name AS \"teacherName\", "
                + "(SELECT COUNT(*) FROM paper_question pq JOIN question q ON q.id = pq.question_id WHERE pq.paper_id = s.paper_id AND " + QuestionService.USABLE + ") AS \"questionCount\" "
                + "FROM practice_set s JOIN practice_set_class sc ON sc.set_id = s.id JOIN users st ON st.class_id = sc.class_id "
                + "JOIN users u ON u.id = s.teacher_id WHERE st.id = :s AND s.enabled ORDER BY s.id DESC", p("s", studentId));
    }

    /**
     * 抽题生成练习卷。source：SET 指定题库 / RECORD 学习记录（我做过的测验、作业题，错题和未掌握的优先）/ POOL 共享题库。
     */
    @Transactional
    public Map<String, Object> start(Long studentId, String source, Long setId, int count, String category) {
        if (count < 1 || count > MAX_DRAW) throw new IllegalArgumentException("每次抽题 1~" + MAX_DRAW + " 道");
        List<Long> ids;
        String title;
        if ("SET".equals(source)) {
            Map<String, Object> set = null;
            for (Map<String, Object> s : availableSets(studentId)) if (((Number) s.get("id")).longValue() == (setId == null ? -1 : setId)) set = s;
            if (set == null) throw new IllegalArgumentException("该刷题题库不存在或未对你开放");
            ids = jdbc.queryForList("SELECT q.id FROM practice_set s JOIN paper_question pq ON pq.paper_id = s.paper_id JOIN question q ON q.id = pq.question_id "
                    + "WHERE s.id = :set AND " + QuestionService.USABLE + "ORDER BY random() LIMIT :n", p("set", setId, "n", count), Long.class);
            title = "指定题库：" + set.get("name");
        } else if ("RECORD".equals(source)) {
            ids = jdbc.queryForList("SELECT x.id FROM (SELECT q.id, BOOL_OR(NOT qa.correct) AS wrong FROM quiz_answer qa "
                    + "JOIN quiz_attempt a ON a.id = qa.attempt_id JOIN question q ON q.id = qa.question_id "
                    + "WHERE a.student_id = :s AND a.status = 'SUBMITTED' AND " + QuestionService.USABLE
                    + "AND NOT EXISTS (SELECT 1 FROM wrong_mastered m WHERE m.student_id = :s AND m.question_id = q.id) GROUP BY q.id) x "
                    + "ORDER BY x.wrong DESC, random() LIMIT :n", p("s", studentId, "n", count), Long.class);
            title = "学习记录刷题";
            setId = null;
        } else if ("POOL".equals(source)) {
            boolean c = category != null && !category.isEmpty();
            ids = jdbc.queryForList("SELECT q.id FROM question q WHERE q.shared AND " + QuestionService.USABLE + (c ? "AND q.category = :c " : "")
                    + "ORDER BY random() LIMIT :n", p("c", category, "n", count), Long.class);
            title = "题库刷题" + (c ? "：" + category : "");
            setId = null;
        } else {
            throw new IllegalArgumentException("无效刷题方式");
        }
        if (ids.isEmpty()) throw new IllegalArgumentException("RECORD".equals(source) ? "你还没有做过测验或作业，暂无可刷的题" : "题库中暂无可用题目");
        Long sid = jdbc.queryForObject("INSERT INTO practice_session(student_id, source, set_id, title, total) VALUES (:s, :src, :set, :t, :n) RETURNING id",
                p("s", studentId, "src", source, "set", setId, "t", title + "（" + ids.size() + " 题）", "n", ids.size()), Long.class);
        int seq = 1;
        for (Long q : ids) jdbc.update("INSERT INTO practice_session_item(session_id, question_id, seq) VALUES (:s, :q, :n)", p("s", sid, "q", q, "n", seq++));
        return session(studentId, sid);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> session(Long studentId, Long sessionId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT id, source, title, created_at AS \"createdAt\", submitted_at AS \"submittedAt\", correct, total "
                + "FROM practice_session WHERE id = :id AND student_id = :s", p("id", sessionId, "s", studentId));
        if (l.isEmpty()) throw new IllegalArgumentException("刷题记录不存在");
        Map<String, Object> s = l.get(0);
        boolean done = s.get("submittedAt") != null;
        List<Map<String, Object>> qs = questions.decorate(jdbc.queryForList("SELECT " + QuestionService.COLS + ", i.answer AS \"given\", i.correct AS \"correctFlag\" "
                + "FROM practice_session_item i JOIN question q ON q.id = i.question_id JOIN users u ON u.id = q.teacher_id "
                + "WHERE i.session_id = :id ORDER BY i.seq", p("id", sessionId)));
        for (Map<String, Object> q : qs) {
            if ("FILL".equals(q.get("qtype"))) {
                q.put("blankCount", ((List<?>) q.get("answer")).size());
                if (q.get("given") != null) q.put("given", questions.fromJsonList((String) q.get("given")));
            }
            Object c = q.remove("correctFlag");
            if (done) {
                q.put("correct", c);
            } else {
                q.remove("answer");
                q.remove("analysis");
            }
        }
        s.put("questions", qs);
        return s;
    }

    /** 交卷：自动批改，同时计入刷题统计（practice_log）。 */
    @Transactional
    public Map<String, Object> submit(Long studentId, Long sessionId, List<Map<String, Object>> answers) {
        Map<String, Object> s = session(studentId, sessionId);
        if (s.get("submittedAt") != null) throw new IllegalArgumentException("这份练习已交卷");
        Map<Long, Object> given = new HashMap<>();
        for (Map<String, Object> a : answers) given.put(((Number) a.get("questionId")).longValue(), a.get("answer"));
        int correct = 0;
        for (Map<String, Object> q : jdbc.queryForList("SELECT q.id, q.qtype, q.answer FROM practice_session_item i JOIN question q ON q.id = i.question_id "
                + "WHERE i.session_id = :id", p("id", sessionId))) {
            Long qid = ((Number) q.get("id")).longValue();
            Object v = given.get(qid);
            String stored = v instanceof List ? questions.toJson(v) : (v == null ? null : String.valueOf(v));
            boolean ok = questions.score((String) q.get("qtype"), (String) q.get("answer"), stored, 1) >= 1;
            if (ok) correct++;
            jdbc.update("UPDATE practice_session_item SET answer = :a, correct = :c WHERE session_id = :s AND question_id = :q",
                    p("a", stored, "c", ok, "s", sessionId, "q", qid));
            jdbc.update("INSERT INTO practice_log(student_id, question_id, correct) VALUES (:st, :q, :c)", p("st", studentId, "q", qid, "c", ok));
        }
        jdbc.update("UPDATE practice_session SET submitted_at = now(), correct = :c WHERE id = :id", p("c", correct, "id", sessionId));
        return session(studentId, sessionId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mySessions(Long studentId) {
        return jdbc.queryForList("SELECT id, source, title, created_at AS \"createdAt\", submitted_at AS \"submittedAt\", correct, total "
                + "FROM practice_session WHERE student_id = :s ORDER BY id DESC LIMIT 100", p("s", studentId));
    }

    // ================= 教师：报表 =================

    /** 学生刷题情况（原“学生刷题情况查询”）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> classReport(Long teacherId, Long classId) {
        if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权查看该班级");
        return jdbc.queryForList("SELECT s.id AS \"studentId\", s.student_no AS \"studentNo\", s.name, "
                + "COUNT(l.id) AS answered, COUNT(l.id) FILTER (WHERE l.correct) AS correct, "
                + "COUNT(l.id) FILTER (WHERE l.answered_at > now() - INTERVAL '7 days') AS \"lastWeek\", MAX(l.answered_at) AS \"lastAt\", "
                + "(SELECT COUNT(*) FROM practice_session ps WHERE ps.student_id = s.id AND ps.submitted_at IS NOT NULL) AS sessions "
                + "FROM users s LEFT JOIN practice_log l ON l.student_id = s.id "
                + "WHERE s.class_id = :k AND s.role = 'STUDENT' AND NOT s.graduated GROUP BY s.id ORDER BY s.student_no NULLS LAST, s.name", p("k", classId));
    }

    /** 题库错误率报表：本人题目在测验、作业、刷题中的作答次数与错误率（作答不少于 minCount 次）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> errorRates(Long teacherId, int minCount) {
        List<Map<String, Object>> l = questions.decorate(jdbc.queryForList("SELECT " + QuestionService.COLS + ", x.n AS \"answerCount\", x.wrong AS \"wrongCount\", "
                + "ROUND(x.wrong * 100.0 / x.n, 1) AS \"errorRate\" FROM (SELECT question_id, COUNT(*) AS n, COUNT(*) FILTER (WHERE NOT ok) AS wrong FROM ("
                + "SELECT qa.question_id, qa.correct AS ok FROM quiz_answer qa JOIN quiz_attempt a ON a.id = qa.attempt_id WHERE a.status = 'SUBMITTED' AND qa.correct IS NOT NULL "
                + "UNION ALL SELECT question_id, correct FROM practice_log) y GROUP BY question_id) x "
                + "JOIN question q ON q.id = x.question_id JOIN users u ON u.id = q.teacher_id "
                + "WHERE q.teacher_id = :t AND q.deleted_at IS NULL AND x.n >= :m ORDER BY \"errorRate\" DESC, x.n DESC LIMIT 200",
                p("t", teacherId, "m", Math.max(1, minCount))));
        return l;
    }

    /** 题库查重（文字题）：题型相同、去掉标签/空白/标点后题干相同的视为重复。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> duplicates(Long teacherId) {
        String norm = "regexp_replace(lower(regexp_replace(q.stem, '<[^>]+>', '', 'g')), '[[:space:][:punct:]，。；：？！、（）“”‘’《》【】]', '', 'g')";
        List<Map<String, Object>> rows = questions.decorate(jdbc.queryForList("SELECT " + QuestionService.COLS + ", " + norm + " AS norm "
                + "FROM question q JOIN users u ON u.id = q.teacher_id WHERE q.teacher_id = :t AND q.deleted_at IS NULL AND q.image_path IS NULL "
                + "AND (q.qtype, " + norm + ") IN (SELECT q.qtype, " + norm + " FROM question q WHERE q.teacher_id = :t AND q.deleted_at IS NULL "
                + "AND q.image_path IS NULL AND " + norm + " <> '' GROUP BY 1, 2 HAVING COUNT(*) > 1) ORDER BY norm, q.id", p("t", teacherId)));
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        for (Map<String, Object> q : rows) {
            String key = q.get("qtype") + "|" + q.remove("norm");
            groups.computeIfAbsent(key, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("qtype", q.get("qtype"));
                g.put("questions", new ArrayList<Map<String, Object>>());
                return g;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> qs = (List<Map<String, Object>>) groups.get(key).get("questions");
            qs.add(q);
        }
        return new ArrayList<>(groups.values());
    }
}
