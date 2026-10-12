package com.ittools.quiz.service;

import com.ittools.classroom.service.ClassBindingService;
import com.ittools.classroom.service.LessonService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 测验发布、作答、自动批改、统计（原 Examination / coursetest / coursetestscore / QueryResults / viewcourseAnalysis）。
 */
@Service
public class TestService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PaperService papers;
    private final QuestionService questions;
    private final LessonService lessons;
    private final ClassBindingService bindings;

    public TestService(NamedParameterJdbcTemplate jdbc, PaperService papers, QuestionService questions,
                       LessonService lessons, ClassBindingService bindings) {
        this.jdbc = jdbc;
        this.papers = papers;
        this.questions = questions;
        this.lessons = lessons;
        this.bindings = bindings;
    }

    private static final String TEST_COLS =
            "t.id, t.paper_id AS \"paperId\", t.title, t.status, t.show_answer AS \"showAnswer\", t.time_limit AS \"timeLimit\", "
                    + "t.class_id AS \"classId\", sy.label || k.name AS \"className\", t.lesson_id AS \"lessonId\", t.page_id AS \"pageId\", "
                    + "t.teacher_id AS \"teacherId\", u.name AS \"teacherName\", t.created_at AS \"createdAt\", t.closed_at AS \"closedAt\", "
                    + "(SELECT COALESCE(SUM(score), 0) FROM paper_question pq WHERE pq.paper_id = t.paper_id) AS \"totalScore\", "
                    + "(SELECT COUNT(*) FROM quiz_attempt a WHERE a.test_id = t.id AND a.status = 'SUBMITTED') AS \"submittedCount\", "
                    + "(SELECT ROUND(AVG(a.score), 1) FROM quiz_attempt a WHERE a.test_id = t.id AND a.status = 'SUBMITTED') AS \"avgScore\", "
                    + "(SELECT COUNT(*) FROM users s WHERE s.class_id = t.class_id AND s.role = 'STUDENT' AND NOT s.graduated) AS \"studentCount\" "
                    + "FROM quiz_test t JOIN klass k ON k.id = t.class_id JOIN school_year sy ON sy.id = k.school_year_id "
                    + "JOIN users u ON u.id = t.teacher_id ";

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long testId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + TEST_COLS + "WHERE t.id = :id", p("id", testId));
        if (l.isEmpty()) throw new IllegalArgumentException("测验不存在");
        return l.get(0);
    }

    // ================= 教师 =================

    @Transactional
    public Map<String, Object> publish(Long teacherId, Long paperId, Long classId, String title, boolean showAnswer, Integer timeLimit) {
        papers.requireReadable(teacherId, paperId);
        if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权向该班级发布");
        Map<String, Object> paper = papers.get(paperId);
        if (((Number) paper.get("questionCount")).intValue() == 0) throw new IllegalArgumentException("试卷没有题目");
        Long id = jdbc.queryForObject("INSERT INTO quiz_test(paper_id, teacher_id, class_id, title, show_answer, time_limit) "
                        + "VALUES (:p, :t, :k, :ti, :sa, :tl) RETURNING id",
                p("p", paperId, "t", teacherId, "k", classId, "ti", title == null || title.trim().isEmpty() ? paper.get("name") : title.trim(),
                        "sa", showAnswer, "tl", timeLimit), Long.class);
        return get(id);
    }

    /** 课堂测验页对应的测验：不存在时按页面关联试卷自动创建。 */
    @Transactional
    public Map<String, Object> lessonPageTest(Long lessonId, Long pageId) {
        List<Long> l = jdbc.queryForList("SELECT id FROM quiz_test WHERE lesson_id = :l AND page_id = :p", p("l", lessonId, "p", pageId), Long.class);
        if (!l.isEmpty()) return get(l.get(0));
        Map<String, Object> page = lessons.requirePageOfLesson(lessonId, pageId);
        if (!"QUIZ".equals(page.get("pageType"))) throw new IllegalArgumentException("该页面不是测验页");
        if (page.get("refId") == null) throw new IllegalArgumentException("老师还没有为该测验页关联试卷");
        Map<String, Object> lesson = lessons.get(lessonId);
        Long id = jdbc.queryForObject("INSERT INTO quiz_test(paper_id, teacher_id, class_id, lesson_id, page_id, title) "
                        + "VALUES (:p, :t, :k, :l, :pg, :ti) RETURNING id",
                p("p", page.get("refId"), "t", lesson.get("teacherId"), "k", lesson.get("classId"), "l", lessonId, "pg", pageId,
                        "ti", page.get("title")), Long.class);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherTests(Long teacherId) {
        return jdbc.queryForList("SELECT " + TEST_COLS + "WHERE t.teacher_id = :t ORDER BY t.id DESC", p("t", teacherId));
    }

    public Map<String, Object> requireTeacher(Long teacherId, Long testId) {
        Map<String, Object> t = get(testId);
        if (!teacherId.equals(((Number) t.get("teacherId")).longValue())) throw new IllegalArgumentException("不是你发布的测验");
        return t;
    }

    /** 开始/停止回收（原 flg3 回收控制）。停止时未交卷的进行中答卷自动交卷。 */
    @Transactional
    public Map<String, Object> setStatus(Long teacherId, Long testId, String status, Boolean showAnswer) {
        requireTeacher(teacherId, testId);
        if (!"OPEN".equals(status) && !"CLOSED".equals(status)) throw new IllegalArgumentException("无效状态");
        jdbc.update("UPDATE quiz_test SET status = :s, closed_at = CASE WHEN :s = 'CLOSED' THEN now() ELSE NULL END, "
                + "show_answer = COALESCE(:sa, show_answer) WHERE id = :id", p("s", status, "sa", showAnswer, "id", testId));
        if ("CLOSED".equals(status)) {
            for (Long aid : jdbc.queryForList("SELECT id FROM quiz_attempt WHERE test_id = :t AND status = 'IN_PROGRESS'",
                    p("t", testId), Long.class)) {
                finalizeAttempt(aid);
            }
        }
        return get(testId);
    }

    @Transactional
    public void delete(Long teacherId, Long testId) {
        Map<String, Object> t = requireTeacher(teacherId, testId);
        if (t.get("lessonId") != null) throw new IllegalArgumentException("课堂测验随课堂保留，不能删除");
        jdbc.update("DELETE FROM quiz_test WHERE id = :id", p("id", testId));
    }

    /** 成绩单：全班学生（含未交）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> results(Long testId) {
        return jdbc.queryForList(
                "SELECT s.id AS \"studentId\", s.name, s.student_no AS \"studentNo\", a.id AS \"attemptId\", a.status, "
                        + "a.score, a.started_at AS \"startedAt\", a.submitted_at AS \"submittedAt\", "
                        + "(SELECT COUNT(*) FROM quiz_answer qa WHERE qa.attempt_id = a.id AND qa.correct) AS \"correctCount\", "
                        + "(SELECT COUNT(*) FROM quiz_answer qa WHERE qa.attempt_id = a.id AND NOT qa.correct) AS \"wrongCount\" "
                        + "FROM quiz_test t JOIN users s ON s.class_id = t.class_id AND s.role = 'STUDENT' AND NOT s.graduated "
                        + "LEFT JOIN quiz_attempt a ON a.test_id = t.id AND a.student_id = s.id "
                        + "WHERE t.id = :t ORDER BY s.student_no NULLS LAST, s.name", p("t", testId));
    }

    /** 逐题分析（原 viewcourseAnalysis：对/错/未交人数、选项/答案分布）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> analysis(Long testId) {
        Map<String, Object> t = get(testId);
        List<Map<String, Object>> qs = papers.paperQuestions(((Number) t.get("paperId")).longValue(), true);
        for (Map<String, Object> q : qs) {
            Long qid = ((Number) q.get("id")).longValue();
            Map<String, Object> agg = jdbc.queryForMap(
                    "SELECT COUNT(*) FILTER (WHERE qa.correct) AS right_n, COUNT(*) FILTER (WHERE NOT qa.correct) AS wrong_n, "
                            + "COALESCE(ROUND(AVG(qa.score), 2), 0) AS avg_score "
                            + "FROM quiz_answer qa JOIN quiz_attempt a ON a.id = qa.attempt_id "
                            + "WHERE a.test_id = :t AND a.status = 'SUBMITTED' AND qa.question_id = :q", p("t", testId, "q", qid));
            q.put("rightCount", agg.get("right_n"));
            q.put("wrongCount", agg.get("wrong_n"));
            q.put("avgScore", agg.get("avg_score"));
            List<Map<String, Object>> dist = jdbc.queryForList(
                    "SELECT COALESCE(qa.answer, '') AS answer, COUNT(*) AS n FROM quiz_answer qa JOIN quiz_attempt a ON a.id = qa.attempt_id "
                            + "WHERE a.test_id = :t AND a.status = 'SUBMITTED' AND qa.question_id = :q GROUP BY qa.answer ORDER BY n DESC LIMIT 12",
                    p("t", testId, "q", qid));
            q.put("distribution", dist);
        }
        return qs;
    }

    /** 教师查看某学生答卷。 */
    @Transactional(readOnly = true)
    public Map<String, Object> attemptDetail(Long attemptId) {
        Map<String, Object> a = jdbc.queryForMap(
                "SELECT a.id, a.test_id AS \"testId\", a.student_id AS \"studentId\", s.name AS \"studentName\", a.status, a.score, "
                        + "a.started_at AS \"startedAt\", a.submitted_at AS \"submittedAt\" FROM quiz_attempt a JOIN users s ON s.id = a.student_id "
                        + "WHERE a.id = :id", p("id", attemptId));
        Map<String, Object> t = get(((Number) a.get("testId")).longValue());
        List<Map<String, Object>> qs = papers.paperQuestions(((Number) t.get("paperId")).longValue(), true);
        Map<Long, Map<String, Object>> ans = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT question_id, answer, correct, score, adjusted FROM quiz_answer WHERE attempt_id = :a",
                p("a", attemptId))) {
            ans.put(((Number) r.get("question_id")).longValue(), r);
        }
        for (Map<String, Object> q : qs) {
            Map<String, Object> r = ans.get(((Number) q.get("id")).longValue());
            Object given = r == null ? null : r.get("answer");
            q.put("given", "FILL".equals(q.get("qtype")) && given != null ? questions.fromJsonList((String) given) : given);
            q.put("correct", r == null ? null : r.get("correct"));
            q.put("gotScore", r == null ? null : r.get("score"));
            q.put("adjusted", r != null && Boolean.TRUE.equals(r.get("adjusted")));
        }
        a.put("test", t);
        a.put("questions", qs);
        return a;
    }

    /** 教师人工改分（如填空题同义答案）。 */
    @Transactional
    public Map<String, Object> adjust(Long teacherId, Long attemptId, Long questionId, double score) {
        Long testId = jdbc.queryForObject("SELECT test_id FROM quiz_attempt WHERE id = :a", p("a", attemptId), Long.class);
        Map<String, Object> t = requireTeacher(teacherId, testId);
        Integer full = jdbc.queryForObject("SELECT score FROM paper_question WHERE paper_id = :p AND question_id = :q",
                p("p", t.get("paperId"), "q", questionId), Integer.class);
        if (full == null) throw new IllegalArgumentException("题目不在试卷中");
        if (score < 0 || score > full) throw new IllegalArgumentException("分数须在 0-" + full + " 之间");
        jdbc.update("INSERT INTO quiz_answer(attempt_id, question_id, answer, correct, score, adjusted) VALUES (:a, :q, NULL, :c, :s, TRUE) "
                        + "ON CONFLICT (attempt_id, question_id) DO UPDATE SET score = EXCLUDED.score, correct = EXCLUDED.correct, adjusted = TRUE",
                p("a", attemptId, "q", questionId, "s", score, "c", score >= full));
        jdbc.update("UPDATE quiz_attempt SET score = (SELECT COALESCE(SUM(score), 0) FROM quiz_answer WHERE attempt_id = :a) WHERE id = :a",
                p("a", attemptId));
        return attemptDetail(attemptId);
    }

    /** 成绩导出（.xlsx）。 */
    @Transactional(readOnly = true)
    public byte[] export(Long testId) {
        Map<String, Object> t = get(testId);
        List<Map<String, Object>> rs = results(testId);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet s = wb.createSheet("成绩");
            org.apache.poi.ss.usermodel.Row title = s.createRow(0);
            title.createCell(0).setCellValue(t.get("title") + " · " + t.get("className") + " · 满分 " + t.get("totalScore"));
            String[] head = {"学号", "姓名", "状态", "得分", "答对题数", "答错题数", "交卷时间"};
            org.apache.poi.ss.usermodel.Row h = s.createRow(1);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            int r = 2;
            for (Map<String, Object> x : rs) {
                org.apache.poi.ss.usermodel.Row row = s.createRow(r++);
                row.createCell(0).setCellValue(x.get("studentNo") == null ? "" : String.valueOf(x.get("studentNo")));
                row.createCell(1).setCellValue(String.valueOf(x.get("name")));
                row.createCell(2).setCellValue("SUBMITTED".equals(x.get("status")) ? "已交" : ("IN_PROGRESS".equals(x.get("status")) ? "作答中" : "未交"));
                if (x.get("score") != null) row.createCell(3).setCellValue(((Number) x.get("score")).doubleValue());
                row.createCell(4).setCellValue(((Number) x.get("correctCount")).doubleValue());
                row.createCell(5).setCellValue(((Number) x.get("wrongCount")).doubleValue());
                row.createCell(6).setCellValue(x.get("submittedAt") == null ? "" : String.valueOf(x.get("submittedAt")));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ================= 学生 =================

    /** 学生可见测验：本班发布的独立测验（课堂测验在课堂内作答）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentTests(Long studentId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + TEST_COLS + "JOIN users st ON st.class_id = t.class_id "
                + "WHERE st.id = :s AND t.lesson_id IS NULL ORDER BY t.id DESC", p("s", studentId));
        for (Map<String, Object> t : l) {
            List<Map<String, Object>> a = jdbc.queryForList("SELECT status, score FROM quiz_attempt WHERE test_id = :t AND student_id = :s",
                    p("t", t.get("id"), "s", studentId));
            t.put("myStatus", a.isEmpty() ? null : a.get(0).get("status"));
            t.put("myScore", a.isEmpty() ? null : a.get(0).get("score"));
        }
        return l;
    }

    private Map<String, Object> requireStudentTest(Long studentId, Long testId) {
        Map<String, Object> t = get(testId);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = :s AND class_id = :k", p("s", studentId, "k", t.get("classId")), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("该测验不是发给你所在班级的");
        return t;
    }

    /** 学生打开测验：返回试题（未交卷不含答案），自动开始作答。 */
    @Transactional
    public Map<String, Object> studentOpen(Long studentId, Long testId) {
        Map<String, Object> t = requireStudentTest(studentId, testId);
        List<Map<String, Object>> a = jdbc.queryForList("SELECT id, status, score, started_at FROM quiz_attempt WHERE test_id = :t AND student_id = :s",
                p("t", testId, "s", studentId));
        if (a.isEmpty() && "OPEN".equals(t.get("status"))) {
            jdbc.update("INSERT INTO quiz_attempt(test_id, student_id) VALUES (:t, :s)", p("t", testId, "s", studentId));
            a = jdbc.queryForList("SELECT id, status, score, started_at FROM quiz_attempt WHERE test_id = :t AND student_id = :s",
                    p("t", testId, "s", studentId));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("test", t);
        Map<String, Object> attempt = a.isEmpty() ? null : a.get(0);
        // 超时自动交卷
        if (attempt != null && "IN_PROGRESS".equals(attempt.get("status")) && t.get("timeLimit") != null) {
            java.time.OffsetDateTime started = toOdt(attempt.get("started_at"));
            if (started != null && started.plusMinutes(((Number) t.get("timeLimit")).longValue()).isBefore(java.time.OffsetDateTime.now())) {
                finalizeAttempt(((Number) attempt.get("id")).longValue());
                attempt = jdbc.queryForMap("SELECT id, status, score, started_at FROM quiz_attempt WHERE id = :id", p("id", attempt.get("id")));
            }
        }
        out.put("attempt", attempt);
        boolean done = attempt != null && "SUBMITTED".equals(attempt.get("status"));
        boolean reveal = done && "CLOSED".equals(t.get("status")) && Boolean.TRUE.equals(t.get("showAnswer"));
        List<Map<String, Object>> qs = papers.paperQuestions(((Number) t.get("paperId")).longValue(), reveal);
        if (attempt != null) {
            Map<Long, Map<String, Object>> ans = new HashMap<>();
            for (Map<String, Object> r : jdbc.queryForList("SELECT question_id, answer, correct, score FROM quiz_answer WHERE attempt_id = :a",
                    p("a", attempt.get("id")))) {
                ans.put(((Number) r.get("question_id")).longValue(), r);
            }
            for (Map<String, Object> q : qs) {
                Map<String, Object> r = ans.get(((Number) q.get("id")).longValue());
                Object given = r == null ? null : r.get("answer");
                q.put("given", "FILL".equals(q.get("qtype")) && given != null ? questions.fromJsonList((String) given) : given);
                if (done) {
                    q.put("correct", r == null ? Boolean.FALSE : r.get("correct"));
                    q.put("gotScore", r == null ? 0 : r.get("score"));
                }
            }
        }
        out.put("questions", qs);
        out.put("answerRevealed", reveal);
        return out;
    }

    /** 保存答案（可多次保存），submit=true 时交卷并批改。 */
    @Transactional
    public Map<String, Object> studentSave(Long studentId, Long testId, List<Map<String, Object>> answers, boolean submit) {
        Map<String, Object> t = requireStudentTest(studentId, testId);
        if (!"OPEN".equals(t.get("status"))) throw new IllegalArgumentException("测验已停止回收");
        List<Map<String, Object>> a = jdbc.queryForList("SELECT id, status FROM quiz_attempt WHERE test_id = :t AND student_id = :s",
                p("t", testId, "s", studentId));
        if (a.isEmpty()) throw new IllegalArgumentException("请先打开测验");
        if ("SUBMITTED".equals(a.get(0).get("status"))) throw new IllegalArgumentException("你已交卷");
        Long attemptId = ((Number) a.get(0).get("id")).longValue();
        for (Map<String, Object> ans : answers) {
            Object v = ans.get("answer");
            String stored = v instanceof List ? questions.toJson(v) : (v == null ? null : String.valueOf(v));
            jdbc.update("INSERT INTO quiz_answer(attempt_id, question_id, answer) VALUES (:a, :q, :v) "
                            + "ON CONFLICT (attempt_id, question_id) DO UPDATE SET answer = EXCLUDED.answer",
                    p("a", attemptId, "q", ((Number) ans.get("questionId")).longValue(), "v", stored));
        }
        if (submit) finalizeAttempt(attemptId);
        return studentOpen(studentId, testId);
    }

    /** 交卷并按标准答案批改。 */
    private void finalizeAttempt(Long attemptId) {
        Long testId = jdbc.queryForObject("SELECT test_id FROM quiz_attempt WHERE id = :a", p("a", attemptId), Long.class);
        Map<String, Object> t = get(testId);
        double total = 0;
        for (Map<String, Object> q : jdbc.queryForList(
                "SELECT q.id, q.qtype, q.answer, pq.score FROM paper_question pq JOIN question q ON q.id = pq.question_id WHERE pq.paper_id = :p",
                p("p", t.get("paperId")))) {
            List<String> given = jdbc.queryForList("SELECT answer FROM quiz_answer WHERE attempt_id = :a AND question_id = :q",
                    p("a", attemptId, "q", q.get("id")), String.class);
            double full = ((Number) q.get("score")).doubleValue();
            double got = questions.score((String) q.get("qtype"), (String) q.get("answer"), given.isEmpty() ? null : given.get(0), full);
            total += got;
            jdbc.update("INSERT INTO quiz_answer(attempt_id, question_id, answer, correct, score) VALUES (:a, :q, NULL, :c, :s) "
                            + "ON CONFLICT (attempt_id, question_id) DO UPDATE SET correct = EXCLUDED.correct, score = EXCLUDED.score",
                    p("a", attemptId, "q", q.get("id"), "c", got >= full && full > 0, "s", got));
        }
        jdbc.update("UPDATE quiz_attempt SET status = 'SUBMITTED', submitted_at = now(), score = :s WHERE id = :a", p("s", total, "a", attemptId));
    }

    /** 错题本：学生在已交测验中答错的题（含答案与解析，仅限已停止回收的测验）+ 刷题答错的题。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> wrongBook(Long studentId) {
        List<Map<String, Object>> l = questions.decorate(jdbc.queryForList(
                "SELECT DISTINCT ON (q.id) " + QuestionService.COLS + ", qa.answer AS \"given\", t.title AS \"source\" "
                        + "FROM quiz_answer qa JOIN quiz_attempt a ON a.id = qa.attempt_id JOIN quiz_test t ON t.id = a.test_id "
                        + "JOIN question q ON q.id = qa.question_id JOIN users u ON u.id = q.teacher_id "
                        + "WHERE a.student_id = :s AND a.status = 'SUBMITTED' AND t.status = 'CLOSED' AND qa.correct = FALSE "
                        + "ORDER BY q.id DESC", p("s", studentId)));
        for (Map<String, Object> q : l) {
            if ("FILL".equals(q.get("qtype")) && q.get("given") != null) q.put("given", questions.fromJsonList((String) q.get("given")));
        }
        return l;
    }

    // ================= 刷题练习 =================

    /** 随机取一道共享题（可按分类/题型），不含答案。 */
    @Transactional(readOnly = true)
    public Map<String, Object> practiceNext(String category, String qtype) {
        List<Map<String, Object>> l = questions.decorate(jdbc.queryForList(
                "SELECT " + QuestionService.COLS + "FROM question q JOIN users u ON u.id = q.teacher_id WHERE q.shared AND " + QuestionService.USABLE
                        + (category == null || category.isEmpty() ? "" : "AND q.category = :c ")
                        + (qtype == null || qtype.isEmpty() ? "" : "AND q.qtype = :qt ")
                        + "ORDER BY random() LIMIT 1", p("c", category, "qt", qtype)));
        if (l.isEmpty()) return null;
        Map<String, Object> q = l.get(0);
        if ("FILL".equals(q.get("qtype"))) q.put("blankCount", ((List<?>) q.get("answer")).size());
        q.remove("answer");
        q.remove("analysis");
        return q;
    }

    @Transactional
    public Map<String, Object> practiceAnswer(Long studentId, Long questionId, Object answer) {
        Map<String, Object> q = questions.get(questionId);
        if (!Boolean.TRUE.equals(q.get("shared"))) throw new IllegalArgumentException("该题不在练习题库中");
        String std = jdbc.queryForObject("SELECT answer FROM question WHERE id = :id", p("id", questionId), String.class);
        String given = answer instanceof List ? questions.toJson(answer) : (answer == null ? null : String.valueOf(answer));
        boolean ok = questions.score((String) q.get("qtype"), std, given, 1) >= 1;
        jdbc.update("INSERT INTO practice_log(student_id, question_id, correct) VALUES (:s, :q, :c)", p("s", studentId, "q", questionId, "c", ok));
        Map<String, Object> out = new HashMap<>();
        out.put("correct", ok);
        out.put("answer", q.get("answer"));
        out.put("analysis", q.get("analysis"));
        out.put("stats", practiceStats(studentId));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> practiceStats(Long studentId) {
        return jdbc.queryForMap("SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE correct) AS \"correct\", "
                + "COUNT(*) FILTER (WHERE answered_at::date = CURRENT_DATE) AS today FROM practice_log WHERE student_id = :s", p("s", studentId));
    }

    @Transactional(readOnly = true)
    public List<String> practiceCategories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM question WHERE shared AND deleted_at IS NULL AND NOT needs_answer AND category IS NOT NULL AND category <> '' ORDER BY category",
                p(), String.class);
    }

    private static java.time.OffsetDateTime toOdt(Object o) {
        if (o instanceof java.time.OffsetDateTime) return (java.time.OffsetDateTime) o;
        if (o instanceof java.sql.Timestamp) return ((java.sql.Timestamp) o).toInstant().atOffset(java.time.ZoneOffset.UTC);
        return null;
    }

    List<Map<String, Object>> copy(List<Map<String, Object>> l) {
        return new ArrayList<>(l);
    }
}
