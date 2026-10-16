package com.ittools.quiz.service;

import com.ittools.classroom.service.ClassBindingService;
import com.ittools.classroom.service.LessonService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ittools.platform.db.Sql.p;

/**
 * 测验与课后作业：发布、作答、自动批改、结分、发还重改、填空归类批改、统计
 * （原 Examination / homework / Conversion / homeworkscore / coursetest / coursetestmark / coursetestscore / viewcourseAnalysis）。
 * <ul>
 *   <li>kind：TEST 测验（含课堂测验）/ HOMEWORK 课后作业</li>
 *   <li>mode：CONTROL 控制模式（待机→开考→结束，由教师发指令）/ AUTO 自动模式 / INSTANT 提交即批改</li>
 *   <li>status：STANDBY 待机 / OPEN 回收中 / CLOSED 停止回收；finalized_at 结分后锁定成绩</li>
 * </ul>
 */
@Service
public class TestService {
    public static final List<String> MODES = Arrays.asList("CONTROL", "AUTO", "INSTANT");
    public static final List<String> KINDS = Arrays.asList("TEST", "HOMEWORK");

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
            "t.id, t.paper_id AS \"paperId\", t.title, t.kind, t.mode, t.status, t.show_answer AS \"showAnswer\", t.time_limit AS \"timeLimit\", "
                    + "t.class_id AS \"classId\", sy.label || k.name AS \"className\", k.school_year_id AS \"yearId\", t.lesson_id AS \"lessonId\", t.page_id AS \"pageId\", "
                    + "t.teacher_id AS \"teacherId\", u.name AS \"teacherName\", t.created_at AS \"createdAt\", t.closed_at AS \"closedAt\", "
                    + "t.finalized_at AS \"finalizedAt\", t.batch_key AS \"batchKey\", "
                    + "t.shuffle_questions AS \"shuffleQuestions\", t.shuffle_options AS \"shuffleOptions\", "
                    + "(SELECT COALESCE(SUM(score), 0) FROM paper_question pq WHERE pq.paper_id = t.paper_id) AS \"totalScore\", "
                    + "(SELECT COUNT(*) FROM quiz_attempt a WHERE a.test_id = t.id AND a.status = 'SUBMITTED') AS \"submittedCount\", "
                    + "(SELECT COUNT(*) FROM quiz_attempt a WHERE a.test_id = t.id AND a.status = 'RETURNED') AS \"returnedCount\", "
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

    private static boolean finalized(Map<String, Object> t) {
        return t.get("finalizedAt") != null;
    }

    private static void requireNotFinalized(Map<String, Object> t) {
        if (finalized(t)) throw new IllegalArgumentException("已结分，成绩不能再修改");
    }

    // ================= 教师：发布与控制 =================

    /** 发布给一个或多个班级（同一批次共享 batch_key，用于年级统计）。控制模式发布后处于待机状态。 */
    @Transactional
    public List<Map<String, Object>> publish(Long teacherId, Long paperId, List<Long> classIds, String title, boolean showAnswer,
                                             Integer timeLimit, String kind, String mode) {
        return publish(teacherId, paperId, classIds, title, showAnswer, timeLimit, kind, mode, false, false);
    }

    @Transactional
    public List<Map<String, Object>> publish(Long teacherId, Long paperId, List<Long> classIds, String title, boolean showAnswer,
                                             Integer timeLimit, String kind, String mode, boolean shuffleQuestions, boolean shuffleOptions) {
        papers.requireReadable(teacherId, paperId);
        if (classIds.isEmpty()) throw new IllegalArgumentException("请选择班级");
        String k = kind == null ? "TEST" : kind;
        String m = mode == null ? "AUTO" : mode;
        if (!KINDS.contains(k)) throw new IllegalArgumentException("无效类型");
        if (!MODES.contains(m)) throw new IllegalArgumentException("无效考试方式");
        if ("HOMEWORK".equals(k) && "CONTROL".equals(m)) throw new IllegalArgumentException("课后作业不能使用控制模式");
        Map<String, Object> paper = papers.get(paperId);
        if (((Number) paper.get("questionCount")).intValue() == 0) throw new IllegalArgumentException("试卷没有题目");
        for (Long classId : classIds) {
            if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权向该班级发布");
        }
        String batch = classIds.size() > 1 ? UUID.randomUUID().toString() : null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long classId : classIds) {
            Long id = jdbc.queryForObject("INSERT INTO quiz_test(paper_id, teacher_id, class_id, title, show_answer, time_limit, kind, mode, status, batch_key, "
                            + "shuffle_questions, shuffle_options) VALUES (:p, :t, :k, :ti, :sa, :tl, :kind, :mode, :st, :b, :sq, :so) RETURNING id",
                    p("p", paperId, "t", teacherId, "k", classId, "ti", title == null || title.trim().isEmpty() ? paper.get("name") : title.trim(),
                            "sa", showAnswer, "tl", timeLimit, "kind", k, "mode", m, "st", "CONTROL".equals(m) ? "STANDBY" : "OPEN", "b", batch,
                            "sq", shuffleQuestions, "so", shuffleOptions), Long.class);
            out.add(get(id));
        }
        return out;
    }

    /** 课堂测验页的考试设置。 */
    @Transactional(readOnly = true)
    public Map<String, Object> pageConfig(Long pageId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT mode, time_limit AS \"timeLimit\", show_answer AS \"showAnswer\", "
                        + "shuffle_questions AS \"shuffleQuestions\", shuffle_options AS \"shuffleOptions\" FROM quiz_page_config WHERE page_id = :p",
                p("p", pageId));
        if (!l.isEmpty()) return l.get(0);
        Map<String, Object> d = new HashMap<>();
        d.put("mode", "CONTROL");
        d.put("timeLimit", null);
        d.put("showAnswer", true);
        d.put("shuffleQuestions", false);
        d.put("shuffleOptions", false);
        return d;
    }

    @Transactional
    public Map<String, Object> savePageConfig(Long pageId, String mode, Integer timeLimit, boolean showAnswer) {
        return savePageConfig(pageId, mode, timeLimit, showAnswer, false, false);
    }

    @Transactional
    public Map<String, Object> savePageConfig(Long pageId, String mode, Integer timeLimit, boolean showAnswer, boolean shuffleQuestions, boolean shuffleOptions) {
        if (!MODES.contains(mode)) throw new IllegalArgumentException("无效考试方式");
        jdbc.update("INSERT INTO quiz_page_config(page_id, mode, time_limit, show_answer, shuffle_questions, shuffle_options) VALUES (:p, :m, :tl, :sa, :sq, :so) "
                        + "ON CONFLICT (page_id) DO UPDATE SET mode = EXCLUDED.mode, time_limit = EXCLUDED.time_limit, show_answer = EXCLUDED.show_answer, "
                        + "shuffle_questions = EXCLUDED.shuffle_questions, shuffle_options = EXCLUDED.shuffle_options",
                p("p", pageId, "m", mode, "tl", timeLimit == null || timeLimit <= 0 ? null : timeLimit, "sa", showAnswer,
                        "sq", shuffleQuestions, "so", shuffleOptions));
        return pageConfig(pageId);
    }

    /** 课堂测验页对应的测验：不存在时按页面关联试卷和考试设置自动创建（控制模式下处于待机）。 */
    @Transactional
    public Map<String, Object> lessonPageTest(Long lessonId, Long pageId) {
        List<Long> l = jdbc.queryForList("SELECT id FROM quiz_test WHERE lesson_id = :l AND page_id = :p", p("l", lessonId, "p", pageId), Long.class);
        if (!l.isEmpty()) return get(l.get(0));
        Map<String, Object> page = lessons.requirePageOfLesson(lessonId, pageId);
        if (!"QUIZ".equals(page.get("pageType"))) throw new IllegalArgumentException("该页面不是测验页");
        if (page.get("refId") == null) throw new IllegalArgumentException("老师还没有为该测验页关联试卷");
        Map<String, Object> lesson = lessons.get(lessonId);
        Map<String, Object> cfg = pageConfig(pageId);
        String mode = (String) cfg.get("mode");
        Long id = jdbc.queryForObject("INSERT INTO quiz_test(paper_id, teacher_id, class_id, lesson_id, page_id, title, kind, mode, status, time_limit, show_answer, "
                        + "shuffle_questions, shuffle_options) VALUES (:p, :t, :k, :l, :pg, :ti, 'TEST', :m, :st, :tl, :sa, :sq, :so) RETURNING id",
                p("p", page.get("refId"), "t", lesson.get("teacherId"), "k", lesson.get("classId"), "l", lessonId, "pg", pageId,
                        "ti", page.get("title"), "m", mode, "st", "CONTROL".equals(mode) ? "STANDBY" : "OPEN",
                        "tl", cfg.get("timeLimit"), "sa", cfg.get("showAnswer"),
                        "sq", Boolean.TRUE.equals(cfg.get("shuffleQuestions")), "so", Boolean.TRUE.equals(cfg.get("shuffleOptions"))), Long.class);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherTests(Long teacherId, String kind) {
        return jdbc.queryForList("SELECT " + TEST_COLS + "WHERE t.teacher_id = :t " + (kind == null || kind.isEmpty() ? "" : "AND t.kind = :k ")
                + "ORDER BY t.id DESC", p("t", teacherId, "k", kind));
    }

    public Map<String, Object> requireTeacher(Long teacherId, Long testId) {
        Map<String, Object> t = get(testId);
        if (!teacherId.equals(((Number) t.get("teacherId")).longValue())) throw new IllegalArgumentException("不是你发布的测验");
        return t;
    }

    /**
     * 状态控制（原 flg3 回收控制 / 课堂测验的待机、开考、结束指令）。
     * 停止回收时进行中的答卷自动交卷；结分后只能修改“是否公布答案”。
     */
    @Transactional
    public Map<String, Object> setStatus(Long teacherId, Long testId, String status, Boolean showAnswer) {
        Map<String, Object> t = requireTeacher(teacherId, testId);
        if (!"STANDBY".equals(status) && !"OPEN".equals(status) && !"CLOSED".equals(status)) throw new IllegalArgumentException("无效状态");
        if (finalized(t) && !status.equals(t.get("status"))) throw new IllegalArgumentException("已结分，不能再开放");
        if ("STANDBY".equals(status) && !"CONTROL".equals(t.get("mode"))) throw new IllegalArgumentException("只有控制模式有待机状态");
        jdbc.update("UPDATE quiz_test SET status = :s, closed_at = CASE WHEN :s = 'CLOSED' THEN COALESCE(closed_at, now()) ELSE NULL END, "
                + "show_answer = COALESCE(:sa, show_answer) WHERE id = :id", p("s", status, "sa", showAnswer, "id", testId));
        if ("CLOSED".equals(status)) {
            for (Long aid : jdbc.queryForList("SELECT id FROM quiz_attempt WHERE test_id = :t AND status = 'IN_PROGRESS'",
                    p("t", testId), Long.class)) {
                finalizeAttempt(aid);
            }
        }
        return get(testId);
    }

    /** 结分（原 Conversion“结分后将不允许修改”）：须先停止回收；被发还但未重交的答卷按现有答案交卷。 */
    @Transactional
    public Map<String, Object> finalizeTest(Long teacherId, Long testId) {
        Map<String, Object> t = requireTeacher(teacherId, testId);
        requireNotFinalized(t);
        if (!"CLOSED".equals(t.get("status"))) throw new IllegalArgumentException("请先停止回收再结分");
        for (Long aid : jdbc.queryForList("SELECT id FROM quiz_attempt WHERE test_id = :t AND status IN ('IN_PROGRESS', 'RETURNED')",
                p("t", testId), Long.class)) {
            finalizeAttempt(aid);
        }
        jdbc.update("UPDATE quiz_test SET finalized_at = now() WHERE id = :id", p("id", testId));
        return get(testId);
    }

    /** 发还重改（原 homeworkscore“发还重改”）：学生可修改后重新提交，停止回收后也可以。 */
    @Transactional
    public void returnAttempt(Long teacherId, Long attemptId, String note) {
        Long testId = jdbc.queryForObject("SELECT test_id FROM quiz_attempt WHERE id = :a", p("a", attemptId), Long.class);
        requireNotFinalized(requireTeacher(teacherId, testId));
        if (note != null && note.length() > 200) throw new IllegalArgumentException("发还说明不超过 200 字");
        if (jdbc.update("UPDATE quiz_attempt SET status = 'RETURNED', returned_note = :n WHERE id = :a AND status = 'SUBMITTED'",
                p("n", note, "a", attemptId)) == 0) {
            throw new IllegalArgumentException("只能发还已交的答卷");
        }
    }

    @Transactional
    public void delete(Long teacherId, Long testId) {
        Map<String, Object> t = requireTeacher(teacherId, testId);
        if (t.get("lessonId") != null) throw new IllegalArgumentException("课堂测验随课堂保留，不能删除");
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempt WHERE test_id = :t AND status <> 'IN_PROGRESS'", p("t", testId), Long.class);
        if ("HOMEWORK".equals(t.get("kind")) && n != null && n > 0) throw new IllegalArgumentException("已有学生提交的课后作业不能删除");
        jdbc.update("DELETE FROM quiz_test WHERE id = :id", p("id", testId));
    }

    /** 成绩单：全班学生（含未交）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> results(Long testId) {
        return jdbc.queryForList(
                "SELECT s.id AS \"studentId\", s.name, s.student_no AS \"studentNo\", a.id AS \"attemptId\", a.status, "
                        + "a.score, a.started_at AS \"startedAt\", a.submitted_at AS \"submittedAt\", a.returned_note AS \"returnedNote\", "
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

    /** 年级统计（原 homeworkscore 班级统计 / 年级统计）：同一批次发布的各班对比。 */
    @Transactional(readOnly = true)
    public Map<String, Object> gradeStats(Long testId) {
        Map<String, Object> t = get(testId);
        List<Long> ids = t.get("batchKey") == null ? Arrays.asList(testId)
                : jdbc.queryForList("SELECT id FROM quiz_test WHERE batch_key = :b ORDER BY id", p("b", t.get("batchKey")), Long.class);
        double full = ((Number) t.get("totalScore")).doubleValue();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> x = get(id);
            Map<String, Object> agg = jdbc.queryForMap("SELECT COUNT(*) AS n, ROUND(AVG(score), 1) AS avg, MAX(score) AS max, MIN(score) AS min, "
                            + "COUNT(*) FILTER (WHERE score >= :pass) AS pass_n, COUNT(*) FILTER (WHERE score >= :good) AS good_n "
                            + "FROM quiz_attempt WHERE test_id = :t AND status = 'SUBMITTED'",
                    p("t", id, "pass", full * 0.6, "good", full * 0.85));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("testId", id);
            r.put("className", x.get("className"));
            r.put("studentCount", x.get("studentCount"));
            r.put("submitted", agg.get("n"));
            r.put("avg", agg.get("avg"));
            r.put("max", agg.get("max"));
            r.put("min", agg.get("min"));
            long n = ((Number) agg.get("n")).longValue();
            r.put("passRate", n == 0 ? null : Math.round(((Number) agg.get("pass_n")).doubleValue() * 1000 / n) / 10.0);
            r.put("goodRate", n == 0 ? null : Math.round(((Number) agg.get("good_n")).doubleValue() * 1000 / n) / 10.0);
            rows.add(r);
        }
        Map<String, Object> all = jdbc.queryForMap("SELECT COUNT(*) AS n, ROUND(AVG(score), 1) AS avg, MAX(score) AS max, MIN(score) AS min, "
                        + "COUNT(*) FILTER (WHERE score >= :pass) AS pass_n, COUNT(*) FILTER (WHERE score >= :good) AS good_n "
                        + "FROM quiz_attempt WHERE test_id IN (:ids) AND status = 'SUBMITTED'",
                p("ids", ids, "pass", full * 0.6, "good", full * 0.85));
        long n = ((Number) all.get("n")).longValue();
        all.put("passRate", n == 0 ? null : Math.round(((Number) all.get("pass_n")).doubleValue() * 1000 / n) / 10.0);
        all.put("goodRate", n == 0 ? null : Math.round(((Number) all.get("good_n")).doubleValue() * 1000 / n) / 10.0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalScore", full);
        out.put("classes", rows);
        out.put("overall", all);
        return out;
    }

    // ================= 批改 =================

    /** 教师查看某学生答卷。 */
    @Transactional(readOnly = true)
    public Map<String, Object> attemptDetail(Long attemptId) {
        Map<String, Object> a = jdbc.queryForMap(
                "SELECT a.id, a.test_id AS \"testId\", a.student_id AS \"studentId\", s.name AS \"studentName\", a.status, a.score, "
                        + "a.started_at AS \"startedAt\", a.submitted_at AS \"submittedAt\", a.returned_note AS \"returnedNote\" "
                        + "FROM quiz_attempt a JOIN users s ON s.id = a.student_id WHERE a.id = :id", p("id", attemptId));
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
        requireNotFinalized(t);
        double full = fullScore(t, questionId);
        if (score < 0 || score > full) throw new IllegalArgumentException("分数须在 0-" + (int) full + " 之间");
        jdbc.update("INSERT INTO quiz_answer(attempt_id, question_id, answer, correct, score, adjusted) VALUES (:a, :q, NULL, :c, :s, TRUE) "
                        + "ON CONFLICT (attempt_id, question_id) DO UPDATE SET score = EXCLUDED.score, correct = EXCLUDED.correct, adjusted = TRUE",
                p("a", attemptId, "q", questionId, "s", score, "c", score >= full));
        recompute(attemptId);
        return attemptDetail(attemptId);
    }

    private double fullScore(Map<String, Object> t, Long questionId) {
        Integer full = jdbc.queryForObject("SELECT score FROM paper_question WHERE paper_id = :p AND question_id = :q",
                p("p", t.get("paperId"), "q", questionId), Integer.class);
        if (full == null) throw new IllegalArgumentException("题目不在试卷中");
        return full;
    }

    private void recompute(Long attemptId) {
        jdbc.update("UPDATE quiz_attempt SET score = (SELECT COALESCE(SUM(score), 0) FROM quiz_answer WHERE attempt_id = :a) WHERE id = :a",
                p("a", attemptId));
    }

    /**
     * 填空题按答案归类（原 Conversion“部分填空采用答案归类手工批改”）：
     * 同一题相同答案的学生归为一组，并给出当前得分。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> fillGroups(Long teacherId, Long testId) {
        Map<String, Object> t = requireTeacher(teacherId, testId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> q : papers.paperQuestions(((Number) t.get("paperId")).longValue(), true)) {
            if (!"FILL".equals(q.get("qtype"))) continue;
            List<Map<String, Object>> groups = jdbc.queryForList(
                    "SELECT qa.answer, COUNT(*) AS count, MIN(qa.score) AS \"minScore\", MAX(qa.score) AS \"maxScore\", BOOL_OR(qa.adjusted) AS adjusted, "
                            + "STRING_AGG(s.name, '、' ORDER BY s.student_no) AS students "
                            + "FROM quiz_answer qa JOIN quiz_attempt a ON a.id = qa.attempt_id JOIN users s ON s.id = a.student_id "
                            + "WHERE a.test_id = :t AND a.status = 'SUBMITTED' AND qa.question_id = :q "
                            + "GROUP BY qa.answer ORDER BY BOOL_OR(qa.correct) NULLS FIRST, COUNT(*) DESC",
                    p("t", testId, "q", q.get("id")));
            for (Map<String, Object> g : groups) {
                String a = (String) g.get("answer");
                g.put("blanks", a == null ? new ArrayList<>() : questions.fromJsonList(a));
            }
            q.put("groups", groups);
            out.add(q);
        }
        return out;
    }

    /** 给某个答案组统一打分，作用于本测验中所有给出相同答案的答卷。 */
    @Transactional
    public int gradeFillGroup(Long teacherId, Long testId, Long questionId, String answer, double score) {
        Map<String, Object> t = requireTeacher(teacherId, testId);
        requireNotFinalized(t);
        double full = fullScore(t, questionId);
        if (score < 0 || score > full) throw new IllegalArgumentException("分数须在 0-" + (int) full + " 之间");
        List<Long> attempts = jdbc.queryForList("SELECT qa.attempt_id FROM quiz_answer qa JOIN quiz_attempt a ON a.id = qa.attempt_id "
                        + "WHERE a.test_id = :t AND a.status = 'SUBMITTED' AND qa.question_id = :q AND qa.answer IS NOT DISTINCT FROM :ans",
                p("t", testId, "q", questionId, "ans", answer), Long.class);
        for (Long aid : attempts) {
            jdbc.update("UPDATE quiz_answer SET score = :s, correct = :c, adjusted = TRUE WHERE attempt_id = :a AND question_id = :q",
                    p("s", score, "c", score >= full, "a", aid, "q", questionId));
            recompute(aid);
        }
        return attempts.size();
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
            String[] head = {"班内学号", "姓名", "状态", "得分", "答对题数", "答错题数", "交卷时间"};
            org.apache.poi.ss.usermodel.Row h = s.createRow(1);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            int r = 2;
            for (Map<String, Object> x : rs) {
                org.apache.poi.ss.usermodel.Row row = s.createRow(r++);
                row.createCell(0).setCellValue(x.get("studentNo") == null ? "" : String.valueOf(x.get("studentNo")));
                row.createCell(1).setCellValue(String.valueOf(x.get("name")));
                row.createCell(2).setCellValue(statusLabel((String) x.get("status")));
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

    private static String statusLabel(String s) {
        if ("SUBMITTED".equals(s)) return "已交";
        if ("RETURNED".equals(s)) return "发还重改";
        if ("IN_PROGRESS".equals(s)) return "作答中";
        return "未交";
    }

    // ================= 学生 =================

    /** 学生可见的独立测验 / 课后作业（课堂测验在课堂内作答）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentTests(Long studentId, String kind) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + TEST_COLS + "JOIN users st ON st.class_id = t.class_id "
                + "WHERE st.id = :s AND t.lesson_id IS NULL " + (kind == null || kind.isEmpty() ? "" : "AND t.kind = :k ")
                + "ORDER BY t.id DESC", p("s", studentId, "k", kind));
        for (Map<String, Object> t : l) {
            List<Map<String, Object>> a = jdbc.queryForList("SELECT status, score FROM quiz_attempt WHERE test_id = :t AND student_id = :s",
                    p("t", t.get("id"), "s", studentId));
            String status = a.isEmpty() ? null : (String) a.get(0).get("status");
            t.put("myStatus", status);
            t.put("myScore", !a.isEmpty() && scoreVisible(t, status) ? a.get(0).get("score") : null);
        }
        return l;
    }

    /** 测验交卷即可看分；课后作业要等教师结分（填空可能还要人工批改）。 */
    private static boolean scoreVisible(Map<String, Object> t, String attemptStatus) {
        if (!"SUBMITTED".equals(attemptStatus)) return false;
        return "HOMEWORK".equals(t.get("kind")) ? finalized(t) : true;
    }

    /** 公布答案：测验在停止回收后（提交即批改模式交卷后）；课后作业在结分后。都需教师开启“公布答案”。 */
    private static boolean answerVisible(Map<String, Object> t, String attemptStatus) {
        if (!"SUBMITTED".equals(attemptStatus) || !Boolean.TRUE.equals(t.get("showAnswer"))) return false;
        if ("HOMEWORK".equals(t.get("kind"))) return finalized(t);
        return "CLOSED".equals(t.get("status")) || "INSTANT".equals(t.get("mode"));
    }

    private Map<String, Object> requireStudentTest(Long studentId, Long testId) {
        Map<String, Object> t = get(testId);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = :s AND class_id = :k", p("s", studentId, "k", t.get("classId")), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("该测验不是发给你所在班级的");
        return t;
    }

    /** 学生打开测验：返回试题（是否含答案视公布规则），回收中时自动开始作答。 */
    @Transactional
    public Map<String, Object> studentOpen(Long studentId, Long testId) {
        Map<String, Object> t = requireStudentTest(studentId, testId);
        List<Map<String, Object>> a = attemptOf(testId, studentId);
        if (a.isEmpty() && "OPEN".equals(t.get("status"))) {
            jdbc.update("INSERT INTO quiz_attempt(test_id, student_id) VALUES (:t, :s)", p("t", testId, "s", studentId));
            a = attemptOf(testId, studentId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("test", t);
        Map<String, Object> attempt = a.isEmpty() ? null : a.get(0);
        // 超时自动交卷
        if (attempt != null && "IN_PROGRESS".equals(attempt.get("status")) && t.get("timeLimit") != null) {
            java.time.OffsetDateTime started = toOdt(attempt.get("started_at"));
            if (started != null && started.plusMinutes(((Number) t.get("timeLimit")).longValue()).isBefore(java.time.OffsetDateTime.now())) {
                finalizeAttempt(((Number) attempt.get("id")).longValue());
                attempt = attemptOf(testId, studentId).get(0);
            }
        }
        String status = attempt == null ? null : (String) attempt.get("status");
        boolean showScore = scoreVisible(t, status);
        boolean reveal = answerVisible(t, status);
        if (attempt != null && !showScore) {
            attempt = new LinkedHashMap<>(attempt);
            attempt.put("score", null);
        }
        out.put("attempt", attempt);
        out.put("canEdit", canEdit(t, status));
        out.put("scoreVisible", showScore);
        List<Map<String, Object>> qs = "STANDBY".equals(t.get("status")) && status == null ? new ArrayList<>()
                : papers.paperQuestions(((Number) t.get("paperId")).longValue(), reveal);
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
                if (showScore) {
                    q.put("correct", r == null ? Boolean.FALSE : r.get("correct"));
                    q.put("gotScore", r == null ? 0 : r.get("score"));
                }
            }
        }
        out.put("questions", shuffleFor(t, studentId, qs));
        out.put("answerRevealed", reveal);
        return out;
    }

    private static boolean choice(Object qtype) {
        return "SINGLE".equals(qtype) || "MULTI".equals(qtype);
    }

    /** 每人乱序：重排题目、选项，并把答案/已答换成显示字母。 */
    private List<Map<String, Object>> shuffleFor(Map<String, Object> t, Long studentId, List<Map<String, Object>> qs) {
        long testId = ((Number) t.get("id")).longValue();
        if (Boolean.TRUE.equals(t.get("shuffleOptions"))) {
            for (Map<String, Object> q : qs) {
                List<?> opts = (List<?>) q.get("options");
                if (!choice(q.get("qtype")) || opts == null || opts.size() < 2) continue;
                int[] perm = Shuffle.perm(opts.size(), testId, studentId, ((Number) q.get("id")).longValue());
                q.put("options", Shuffle.apply(opts, perm));
                if (q.get("answer") instanceof String) q.put("answer", Shuffle.toShown((String) q.get("answer"), perm));
                if (q.get("given") instanceof String) q.put("given", Shuffle.toShown((String) q.get("given"), perm));
            }
        }
        return Boolean.TRUE.equals(t.get("shuffleQuestions")) ? Shuffle.order(qs, testId, studentId) : qs;
    }

    /** 学生提交的是显示字母：换回原选项字母再保存。 */
    private Object toOriginalAnswer(Map<String, Object> t, Long studentId, Map<String, Object> q, Object v) {
        if (!Boolean.TRUE.equals(t.get("shuffleOptions")) || !(v instanceof String) || q == null || !choice(q.get("qtype"))) return v;
        List<?> opts = (List<?>) q.get("options");
        if (opts == null || opts.size() < 2) return v;
        return Shuffle.toOriginal((String) v, Shuffle.perm(opts.size(), ((Number) t.get("id")).longValue(), studentId, ((Number) q.get("id")).longValue()));
    }

    private List<Map<String, Object>> attemptOf(Long testId, Long studentId) {
        return jdbc.queryForList("SELECT id, status, score, started_at, returned_note AS \"returnedNote\" FROM quiz_attempt WHERE test_id = :t AND student_id = :s",
                p("t", testId, "s", studentId));
    }

    private static boolean canEdit(Map<String, Object> t, String attemptStatus) {
        if (finalized(t)) return false;
        if ("RETURNED".equals(attemptStatus)) return true;
        return "OPEN".equals(t.get("status")) && "IN_PROGRESS".equals(attemptStatus);
    }

    /** 保存答案（可多次保存），submit=true 时交卷并批改。被发还的答卷在结分前随时可改。 */
    @Transactional
    public Map<String, Object> studentSave(Long studentId, Long testId, List<Map<String, Object>> answers, boolean submit) {
        Map<String, Object> t = requireStudentTest(studentId, testId);
        List<Map<String, Object>> a = attemptOf(testId, studentId);
        if (a.isEmpty()) throw new IllegalArgumentException("STANDBY".equals(t.get("status")) ? "请等待老师开考" : "请先打开测验");
        String status = (String) a.get(0).get("status");
        if (!canEdit(t, status)) {
            if ("SUBMITTED".equals(status)) throw new IllegalArgumentException("你已交卷");
            if ("STANDBY".equals(t.get("status"))) throw new IllegalArgumentException("老师已暂停作答，请等待开考指令");
            throw new IllegalArgumentException(finalized(t) ? "已结分，不能修改" : "测验已停止回收");
        }
        Long attemptId = ((Number) a.get(0).get("id")).longValue();
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        if (Boolean.TRUE.equals(t.get("shuffleOptions"))) {
            for (Map<String, Object> q : papers.paperQuestions(((Number) t.get("paperId")).longValue(), false)) byId.put(((Number) q.get("id")).longValue(), q);
        }
        for (Map<String, Object> ans : answers) {
            Object v = toOriginalAnswer(t, studentId, byId.get(((Number) ans.get("questionId")).longValue()), ans.get("answer"));
            String stored = v instanceof List ? questions.toJson(v) : (v == null ? null : String.valueOf(v));
            jdbc.update("INSERT INTO quiz_answer(attempt_id, question_id, answer) VALUES (:a, :q, :v) "
                            // 答案改了才取消教师的人工改分；没改的保留（发还重改时其余题的人工分不丢）
                            + "ON CONFLICT (attempt_id, question_id) DO UPDATE SET answer = EXCLUDED.answer, "
                            + "adjusted = quiz_answer.adjusted AND quiz_answer.answer IS NOT DISTINCT FROM EXCLUDED.answer",
                    p("a", attemptId, "q", ((Number) ans.get("questionId")).longValue(), "v", stored));
        }
        if (submit) finalizeAttempt(attemptId);
        return studentOpen(studentId, testId);
    }

    /** 交卷并按标准答案批改（教师已人工改过的题保留人工分数）。 */
    private void finalizeAttempt(Long attemptId) {
        Long testId = jdbc.queryForObject("SELECT test_id FROM quiz_attempt WHERE id = :a", p("a", attemptId), Long.class);
        Map<String, Object> t = get(testId);
        for (Map<String, Object> q : jdbc.queryForList(
                "SELECT q.id, q.qtype, q.answer, pq.score FROM paper_question pq JOIN question q ON q.id = pq.question_id WHERE pq.paper_id = :p",
                p("p", t.get("paperId")))) {
            List<Map<String, Object>> given = jdbc.queryForList("SELECT answer, adjusted FROM quiz_answer WHERE attempt_id = :a AND question_id = :q",
                    p("a", attemptId, "q", q.get("id")));
            if (!given.isEmpty() && Boolean.TRUE.equals(given.get(0).get("adjusted"))) continue;
            double full = ((Number) q.get("score")).doubleValue();
            double got = questions.score((String) q.get("qtype"), (String) q.get("answer"), given.isEmpty() ? null : (String) given.get(0).get("answer"), full);
            jdbc.update("INSERT INTO quiz_answer(attempt_id, question_id, answer, correct, score) VALUES (:a, :q, NULL, :c, :s) "
                            + "ON CONFLICT (attempt_id, question_id) DO UPDATE SET correct = EXCLUDED.correct, score = EXCLUDED.score",
                    p("a", attemptId, "q", q.get("id"), "c", got >= full && full > 0, "s", got));
        }
        jdbc.update("UPDATE quiz_attempt SET status = 'SUBMITTED', submitted_at = now(), returned_note = NULL, "
                + "score = (SELECT COALESCE(SUM(score), 0) FROM quiz_answer WHERE attempt_id = :a) WHERE id = :a", p("a", attemptId));
    }

    // ================= 错题本（原 Leakfilled / Leakfilledcourse） =================

    /**
     * 错题：已公布答案的测验/作业中答错的题。kind = HOMEWORK（课后作业错误汇总）/ TEST（课堂测试错误汇总）/ 空 = 全部；
     * 默认不含已标记“我已掌握”的题。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> wrongBook(Long studentId, String kind, boolean includeMastered) {
        List<Map<String, Object>> l = questions.decorate(jdbc.queryForList(
                "SELECT DISTINCT ON (q.id) " + QuestionService.COLS + ", qa.answer AS \"given\", t.title AS \"source\", t.kind AS \"sourceKind\", "
                        + "(m.question_id IS NOT NULL) AS mastered "
                        + "FROM quiz_answer qa JOIN quiz_attempt a ON a.id = qa.attempt_id JOIN quiz_test t ON t.id = a.test_id "
                        + "JOIN question q ON q.id = qa.question_id JOIN users u ON u.id = q.teacher_id "
                        + "LEFT JOIN wrong_mastered m ON m.student_id = a.student_id AND m.question_id = q.id "
                        + "WHERE a.student_id = :s AND a.status = 'SUBMITTED' AND qa.correct = FALSE AND t.show_answer "
                        + "AND ((t.kind = 'TEST' AND (t.status = 'CLOSED' OR t.mode = 'INSTANT')) OR (t.kind = 'HOMEWORK' AND t.finalized_at IS NOT NULL)) "
                        + (kind == null || kind.isEmpty() ? "" : "AND t.kind = :k ")
                        + (includeMastered ? "" : "AND m.question_id IS NULL ")
                        + "ORDER BY q.id DESC, a.submitted_at DESC", p("s", studentId, "k", kind)));
        for (Map<String, Object> q : l) {
            if ("FILL".equals(q.get("qtype")) && q.get("given") != null) q.put("given", questions.fromJsonList((String) q.get("given")));
            if ("FILL".equals(q.get("qtype"))) q.put("blankCount", ((List<?>) q.get("answer")).size());
        }
        return l;
    }

    @Transactional
    public void setMastered(Long studentId, Long questionId, boolean mastered) {
        if (mastered) {
            jdbc.update("INSERT INTO wrong_mastered(student_id, question_id) VALUES (:s, :q) ON CONFLICT DO NOTHING", p("s", studentId, "q", questionId));
        } else {
            jdbc.update("DELETE FROM wrong_mastered WHERE student_id = :s AND question_id = :q", p("s", studentId, "q", questionId));
        }
    }

    // ================= 自由练习（逐题，共享题库） =================

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
}
