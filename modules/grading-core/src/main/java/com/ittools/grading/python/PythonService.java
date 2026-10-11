package com.ittools.grading.python;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.classroom.service.LessonService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ittools.platform.db.Sql.p;

/**
 * Python 操作题（原 coursepy / coursepy1 / pythonlist / editpy）。
 * 学生在浏览器端（Brython）自由运行调试；提交评分时由 {@link PythonSandbox} 在服务端逐个阅卷点运行，
 * 输出与期望输出比对（忽略行尾空白与末尾空行）后按点给分。
 */
@Service
public class PythonService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PythonSandbox sandbox;
    private final LessonService lessons;
    private final ObjectMapper json = new ObjectMapper();

    public PythonService(NamedParameterJdbcTemplate jdbc, PythonSandbox sandbox, LessonService lessons) {
        this.jdbc = jdbc;
        this.sandbox = sandbox;
        this.lessons = lessons;
    }

    // ================= 题库 =================

    private static final String PROBLEM_COLS =
            "pr.id, pr.teacher_id AS \"teacherId\", u.name AS \"teacherName\", pr.title, pr.description, "
                    + "pr.starter_code AS \"starterCode\", pr.reference_code AS \"referenceCode\", pr.time_limit_ms AS \"timeLimitMs\", "
                    + "pr.shared, pr.practice, pr.version, pr.created_at AS \"createdAt\", pr.updated_at AS \"updatedAt\", "
                    + "(SELECT COUNT(*) FROM py_testcase c WHERE c.problem_id = pr.id) AS \"caseCount\", "
                    + "(SELECT COALESCE(SUM(c.score), 0) FROM py_testcase c WHERE c.problem_id = pr.id) AS \"totalScore\" ";
    private static final String PROBLEM_FROM = "FROM py_problem pr JOIN users u ON u.id = pr.teacher_id ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> problems(Long teacherId, boolean shared, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT " + PROBLEM_COLS + ", "
                + "(SELECT COUNT(*) FROM py_submission s WHERE s.problem_id = pr.id) AS \"submissionCount\" " + PROBLEM_FROM + "WHERE ");
        sql.append(shared ? "pr.shared AND pr.teacher_id <> :t " : "pr.teacher_id = :t ");
        MapSqlParameterSource ps = p("t", teacherId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND pr.title ILIKE :kw ");
            ps.addValue("kw", "%" + keyword.trim() + "%");
        }
        sql.append("ORDER BY pr.id DESC");
        return jdbc.queryForList(sql.toString(), ps);
    }

    private Map<String, Object> problemRow(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + PROBLEM_COLS + PROBLEM_FROM + "WHERE pr.id = :id", p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("题目不存在");
        return l.get(0);
    }

    private List<Map<String, Object>> cases(Long problemId) {
        return jdbc.queryForList("SELECT id, seq, name, input, expected, score, hidden FROM py_testcase "
                + "WHERE problem_id = :p ORDER BY seq, id", p("p", problemId));
    }

    /** 教师查看题目（自己的或共享的），含全部阅卷点。 */
    @Transactional(readOnly = true)
    public Map<String, Object> problem(Long teacherId, Long id) {
        Map<String, Object> pr = problemRow(id);
        if (!isOwner(pr, teacherId) && !Boolean.TRUE.equals(pr.get("shared"))) throw new IllegalArgumentException("无权查看该题目");
        pr.put("cases", cases(id));
        return pr;
    }

    private static boolean isOwner(Map<String, Object> pr, Long teacherId) {
        return teacherId.equals(((Number) pr.get("teacherId")).longValue());
    }

    @Transactional
    public Map<String, Object> saveProblem(Long teacherId, Long id, Map<String, Object> f) {
        String title = str(f.get("title"));
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("题目标题不能为空");
        int limit = f.get("timeLimitMs") == null ? 2000 : ((Number) f.get("timeLimitMs")).intValue();
        if (limit < 100 || limit > 10000) throw new IllegalArgumentException("单点时限须在 100~10000 毫秒之间");
        List<Map<String, Object>> newCases = normalizeCases(f.get("cases"));

        MapSqlParameterSource ps = p("t", teacherId, "ti", title.trim(), "d", str(f.get("description")),
                "sc", str(f.get("starterCode")), "rc", str(f.get("referenceCode")), "lim", limit,
                "sh", Boolean.TRUE.equals(f.get("shared")), "pr", Boolean.TRUE.equals(f.get("practice")));
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO py_problem(teacher_id, title, description, starter_code, reference_code, time_limit_ms, shared, practice) "
                    + "VALUES (:t, :ti, :d, :sc, :rc, :lim, :sh, :pr) RETURNING id", ps, Long.class);
        } else {
            Map<String, Object> old = problemRow(id);
            if (!isOwner(old, teacherId)) throw new IllegalArgumentException("只能修改自己的题目");
            boolean changed = !sameCases(cases(id), newCases);
            ps.addValue("id", id).addValue("bump", changed ? 1 : 0);
            jdbc.update("UPDATE py_problem SET title = :ti, description = :d, starter_code = :sc, reference_code = :rc, "
                    + "time_limit_ms = :lim, shared = :sh, practice = :pr, version = version + :bump, updated_at = now() WHERE id = :id", ps);
            jdbc.update("DELETE FROM py_testcase WHERE problem_id = :p", p("p", id));
        }
        int seq = 1;
        for (Map<String, Object> c : newCases) {
            jdbc.update("INSERT INTO py_testcase(problem_id, seq, name, input, expected, score, hidden) VALUES (:p, :s, :n, :i, :e, :sc, :h)",
                    p("p", id, "s", seq++, "n", c.get("name"), "i", c.get("input"), "e", c.get("expected"), "sc", c.get("score"), "h", c.get("hidden")));
        }
        return problem(teacherId, id);
    }

    private static List<Map<String, Object>> normalizeCases(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            Map<String, Object> c = new HashMap<>();
            String name = str(m.get("name"));
            c.put("name", name == null || name.trim().isEmpty() ? null : name.trim());
            c.put("input", nz(str(m.get("input"))));
            c.put("expected", nz(str(m.get("expected"))));
            int score = m.get("score") == null ? 0 : ((Number) m.get("score")).intValue();
            if (score < 0) throw new IllegalArgumentException("阅卷点分值不能为负");
            c.put("score", score);
            c.put("hidden", Boolean.TRUE.equals(m.get("hidden")));
            out.add(c);
        }
        if (out.size() > 50) throw new IllegalArgumentException("阅卷点最多 50 个");
        return out;
    }

    private static boolean sameCases(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Map<String, Object> x = a.get(i), y = b.get(i);
            if (!Objects.equals(x.get("input"), y.get("input")) || !Objects.equals(x.get("expected"), y.get("expected"))
                    || ((Number) x.get("score")).intValue() != ((Number) y.get("score")).intValue()) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public Long copyProblem(Long teacherId, Long id) {
        Map<String, Object> src = problem(teacherId, id);
        Long nid = jdbc.queryForObject("INSERT INTO py_problem(teacher_id, title, description, starter_code, reference_code, time_limit_ms) "
                        + "SELECT :t, title || '（副本）', description, starter_code, reference_code, time_limit_ms FROM py_problem WHERE id = :id RETURNING id",
                p("t", teacherId, "id", id), Long.class);
        jdbc.update("INSERT INTO py_testcase(problem_id, seq, name, input, expected, score, hidden) "
                + "SELECT :n, seq, name, input, expected, score, hidden FROM py_testcase WHERE problem_id = :id", p("n", nid, "id", src.get("id")));
        return nid;
    }

    @Transactional
    public void deleteProblem(Long teacherId, Long id) {
        Map<String, Object> pr = problemRow(id);
        if (!isOwner(pr, teacherId)) throw new IllegalArgumentException("只能删除自己的题目");
        Long pages = jdbc.queryForObject("SELECT COUNT(*) FROM course_page WHERE page_type = 'PYTHON' AND ref_id = :id", p("id", id), Long.class);
        if (pages != null && pages > 0) throw new IllegalArgumentException("该题目已被 " + pages + " 个课程页面使用，不能删除");
        Long subs = jdbc.queryForObject("SELECT COUNT(*) FROM py_submission WHERE problem_id = :id", p("id", id), Long.class);
        if (subs != null && subs > 0) throw new IllegalArgumentException("已有学生作答记录，不能删除（可取消课后题库）");
        jdbc.update("DELETE FROM py_problem WHERE id = :id", p("id", id));
    }

    /** 教师试运行（如用参考代码生成期望输出）。 */
    public List<Map<String, Object>> teacherRun(String code, List<String> inputs, Integer timeLimitMs) {
        if (inputs.size() > 50) throw new IllegalArgumentException("一次最多运行 50 组输入");
        return sandbox.run(code, inputs, timeLimitMs == null ? 2000 : Math.max(100, Math.min(10000, timeLimitMs)));
    }

    // ================= 评测 =================

    /** 输出比对规则：统一换行，忽略每行行尾空白和末尾空行。 */
    static String normalize(String s) {
        if (s == null) return "";
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (String l : lines) {
            int end = l.length();
            while (end > 0 && Character.isWhitespace(l.charAt(end - 1))) end--;
            b.append(l, 0, end).append('\n');
        }
        String t = b.toString();
        int end = t.length();
        while (end > 0 && t.charAt(end - 1) == '\n') end--;
        return t.substring(0, end);
    }

    private Map<String, Object> grade(Map<String, Object> problem, List<Map<String, Object>> cases, String code) {
        List<String> inputs = new ArrayList<>();
        for (Map<String, Object> c : cases) inputs.add((String) c.get("input"));
        List<Map<String, Object>> runs = sandbox.run(code, inputs, ((Number) problem.get("timeLimitMs")).intValue());
        List<Map<String, Object>> detail = new ArrayList<>();
        int score = 0, max = 0;
        for (int i = 0; i < cases.size(); i++) {
            Map<String, Object> c = cases.get(i), r = runs.get(i);
            int pts = ((Number) c.get("score")).intValue();
            boolean pass = "OK".equals(r.get("status")) && normalize((String) r.get("stdout")).equals(normalize((String) c.get("expected")));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("seq", c.get("seq"));
            d.put("name", c.get("name"));
            d.put("hidden", c.get("hidden"));
            d.put("status", pass ? "AC" : ("OK".equals(r.get("status")) ? "WA" : r.get("status")));
            d.put("passed", pass);
            d.put("score", pass ? pts : 0);
            d.put("maxScore", pts);
            d.put("ms", r.get("ms"));
            d.put("stdout", r.get("stdout"));
            d.put("stderr", r.get("stderr"));
            detail.add(d);
            max += pts;
            if (pass) score += pts;
        }
        Map<String, Object> res = new HashMap<>();
        res.put("score", score);
        res.put("maxScore", max);
        res.put("detail", detail);
        return res;
    }

    private Long upsertCode(Long problemId, Long studentId, Long lessonId, Long pageId, String code) {
        if (code != null && code.length() > 64 * 1024) throw new IllegalArgumentException("代码过长（上限 64KB）");
        return jdbc.queryForObject("INSERT INTO py_submission(problem_id, student_id, lesson_id, page_id, code) VALUES (:pr, :s, :l, :pg, :c) "
                        + "ON CONFLICT (problem_id, student_id, (COALESCE(lesson_id, 0)), (COALESCE(page_id, 0))) "
                        + "DO UPDATE SET code = EXCLUDED.code, saved_at = now() RETURNING id",
                p("pr", problemId, "s", studentId, "l", lessonId, "pg", pageId, "c", nz(code)), Long.class);
    }

    private void storeGrade(Long submissionId, Map<String, Object> problem, Map<String, Object> res, String gradedCode, boolean countSubmit) {
        try {
            jdbc.update("UPDATE py_submission SET score = :sc, max_score = :mx, best_score = GREATEST(best_score, :sc), detail = :d, "
                            + "problem_version = :v, graded_at = now(), submit_count = submit_count + :inc, submitted_code = :code WHERE id = :id",
                    p("sc", res.get("score"), "mx", res.get("maxScore"), "d", json.writeValueAsString(res.get("detail")),
                            "v", problem.get("version"), "inc", countSubmit ? 1 : 0, "code", gradedCode, "id", submissionId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ================= 学生：课堂内 =================

    private Map<String, Object> lessonProblem(Long studentId, Long lessonId, Long pageId) {
        Map<String, Object> page = lessons.studentPage(studentId, lessonId, pageId);
        if (!"PYTHON".equals(page.get("pageType"))) throw new IllegalArgumentException("该页面不是 Python 操作题");
        if (page.get("refId") == null) throw new IllegalArgumentException("老师还没有为该页面选择题目");
        return problemRow(((Number) page.get("refId")).longValue());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentLessonView(Long studentId, Long lessonId, Long pageId) {
        Map<String, Object> pr = lessonProblem(studentId, lessonId, pageId);
        return studentView(pr, studentId, lessonId, pageId, showReference(lessonId, pageId));
    }

    @Transactional
    public Map<String, Object> studentLessonSave(Long studentId, Long lessonId, Long pageId, String code) {
        Map<String, Object> pr = lessonProblem(studentId, lessonId, pageId);
        upsertCode(((Number) pr.get("id")).longValue(), studentId, lessonId, pageId, code);
        return studentView(pr, studentId, lessonId, pageId, showReference(lessonId, pageId));
    }

    // 不加事务：沙箱运行可能较久，不应占用数据库连接；各步骤为单条语句自动提交
    public Map<String, Object> studentLessonSubmit(Long studentId, Long lessonId, Long pageId, String code) {
        Map<String, Object> pr = lessonProblem(studentId, lessonId, pageId);
        Long problemId = ((Number) pr.get("id")).longValue();
        Long sid = upsertCode(problemId, studentId, lessonId, pageId, code);
        storeGrade(sid, pr, grade(pr, cases(problemId), nz(code)), nz(code), true);
        return studentView(pr, studentId, lessonId, pageId, showReference(lessonId, pageId));
    }

    private boolean showReference(Long lessonId, Long pageId) {
        List<Boolean> l = jdbc.queryForList("SELECT show_reference FROM py_lesson_setting WHERE lesson_id = :l AND page_id = :p",
                p("l", lessonId, "p", pageId), Boolean.class);
        return !l.isEmpty() && Boolean.TRUE.equals(l.get(0));
    }

    /** 学生视角：隐藏点不暴露输入/期望/输出，参考代码仅在教师开放时返回。 */
    private Map<String, Object> studentView(Map<String, Object> pr, Long studentId, Long lessonId, Long pageId, boolean withReference) {
        Long problemId = ((Number) pr.get("id")).longValue();
        Map<String, Object> v = new LinkedHashMap<>();
        for (String k : new String[]{"id", "title", "description", "starterCode", "timeLimitMs", "version", "totalScore"}) v.put(k, pr.get(k));
        v.put("referenceCode", withReference ? pr.get("referenceCode") : null);
        List<Map<String, Object>> cs = new ArrayList<>();
        for (Map<String, Object> c : cases(problemId)) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("seq", c.get("seq"));
            x.put("name", c.get("name"));
            x.put("score", c.get("score"));
            x.put("hidden", c.get("hidden"));
            if (!Boolean.TRUE.equals(c.get("hidden"))) {
                x.put("input", c.get("input"));
                x.put("expected", c.get("expected"));
            }
            cs.add(x);
        }
        v.put("cases", cs);
        List<Map<String, Object>> subs = jdbc.queryForList("SELECT id, code, score, max_score AS \"maxScore\", best_score AS \"bestScore\", detail, "
                        + "problem_version AS \"problemVersion\", submit_count AS \"submitCount\", saved_at AS \"savedAt\", graded_at AS \"gradedAt\" "
                        + "FROM py_submission WHERE problem_id = :pr AND student_id = :s AND COALESCE(lesson_id, 0) = :l AND COALESCE(page_id, 0) = :pg",
                p("pr", problemId, "s", studentId, "l", lessonId == null ? 0L : lessonId, "pg", pageId == null ? 0L : pageId));
        if (subs.isEmpty()) {
            v.put("submission", null);
        } else {
            Map<String, Object> s = subs.get(0);
            List<Map<String, Object>> detail = parseDetail((String) s.get("detail"));
            for (Map<String, Object> d : detail) {
                if (Boolean.TRUE.equals(d.get("hidden"))) {
                    d.remove("stdout");
                    d.remove("stderr");
                }
            }
            s.put("detail", detail);
            s.put("stale", s.get("problemVersion") != null && !Objects.equals(((Number) s.get("problemVersion")).intValue(), ((Number) pr.get("version")).intValue()));
            v.put("submission", s);
        }
        return v;
    }

    private List<Map<String, Object>> parseDetail(String s) {
        if (s == null || s.isEmpty()) return new ArrayList<>();
        try {
            return json.readValue(s, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    // ================= 学生：课后题库 =================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> practiceList(Long studentId) {
        return jdbc.queryForList("SELECT pr.id, pr.title, u.name AS \"teacherName\", pr.updated_at AS \"updatedAt\", "
                + "(SELECT COALESCE(SUM(c.score), 0) FROM py_testcase c WHERE c.problem_id = pr.id) AS \"totalScore\", "
                + "s.best_score AS \"bestScore\", s.submit_count AS \"submitCount\", s.graded_at AS \"gradedAt\" "
                + "FROM py_problem pr JOIN users u ON u.id = pr.teacher_id "
                + "LEFT JOIN py_submission s ON s.problem_id = pr.id AND s.student_id = :s AND s.lesson_id IS NULL AND s.page_id IS NULL "
                + "WHERE pr.practice ORDER BY pr.id DESC", p("s", studentId));
    }

    private Map<String, Object> practiceProblem(Long problemId) {
        Map<String, Object> pr = problemRow(problemId);
        if (!Boolean.TRUE.equals(pr.get("practice"))) throw new IllegalArgumentException("该题目未开放课后练习");
        return pr;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> practiceView(Long studentId, Long problemId) {
        return studentView(practiceProblem(problemId), studentId, null, null, false);
    }

    @Transactional
    public Map<String, Object> practiceSave(Long studentId, Long problemId, String code) {
        Map<String, Object> pr = practiceProblem(problemId);
        upsertCode(problemId, studentId, null, null, code);
        return studentView(pr, studentId, null, null, false);
    }

    // 不加事务：沙箱运行可能较久，不应占用数据库连接；各步骤为单条语句自动提交
    public Map<String, Object> practiceSubmit(Long studentId, Long problemId, String code) {
        Map<String, Object> pr = practiceProblem(problemId);
        Long sid = upsertCode(problemId, studentId, null, null, code);
        storeGrade(sid, pr, grade(pr, cases(problemId), nz(code)), nz(code), true);
        return studentView(pr, studentId, null, null, false);
    }

    // ================= 教师：课堂面板 =================

    /** 课堂中的 Python 页：题目 + 全班作答情况（原"学生答题情况统计表"）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> lessonPanel(Long teacherId, Long lessonId, Long pageId) {
        Map<String, Object> lesson = lessons.requireTeacher(teacherId, lessonId);
        Map<String, Object> page = lessons.requirePageOfLesson(lessonId, pageId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (page.get("refId") == null) {
            out.put("problem", null);
            return out;
        }
        Long problemId = ((Number) page.get("refId")).longValue();
        Map<String, Object> pr = problemRow(problemId);
        pr.put("cases", cases(problemId));
        out.put("problem", pr);
        out.put("showReference", showReference(lessonId, pageId));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT u.id AS \"studentId\", u.name, u.student_no AS \"studentNo\", s.id AS \"submissionId\", s.score, "
                        + "s.max_score AS \"maxScore\", s.best_score AS \"bestScore\", s.submit_count AS \"submitCount\", s.detail, "
                        + "s.problem_version AS \"problemVersion\", s.saved_at AS \"savedAt\", s.graded_at AS \"gradedAt\" "
                        + "FROM users u LEFT JOIN py_submission s ON s.student_id = u.id AND s.problem_id = :pr AND s.lesson_id = :l AND s.page_id = :pg "
                        + "WHERE u.class_id = :k AND u.role = 'STUDENT' AND NOT u.graduated ORDER BY u.student_no, u.name",
                p("pr", problemId, "l", lessonId, "pg", pageId, "k", lesson.get("classId")));
        int version = ((Number) pr.get("version")).intValue();
        for (Map<String, Object> r : rows) {
            List<Boolean> passes = new ArrayList<>();
            for (Map<String, Object> d : parseDetail((String) r.remove("detail"))) passes.add(Boolean.TRUE.equals(d.get("passed")));
            r.put("passes", passes);
            r.put("stale", r.get("problemVersion") != null && ((Number) r.get("problemVersion")).intValue() != version);
        }
        out.put("rows", rows);
        return out;
    }

    @Transactional
    public void setShowReference(Long teacherId, Long lessonId, Long pageId, boolean show) {
        lessons.requireTeacher(teacherId, lessonId);
        lessons.requirePageOfLesson(lessonId, pageId);
        jdbc.update("INSERT INTO py_lesson_setting(lesson_id, page_id, show_reference) VALUES (:l, :p, :s) "
                + "ON CONFLICT (lesson_id, page_id) DO UPDATE SET show_reference = EXCLUDED.show_reference", p("l", lessonId, "p", pageId, "s", show));
    }

    /** 教师查看某个学生的代码与评测详情：课堂作答须是自己的课堂，课后练习须是自己的题目。 */
    @Transactional(readOnly = true)
    public Map<String, Object> submission(Long teacherId, Long submissionId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT s.id, s.problem_id AS \"problemId\", s.lesson_id AS \"lessonId\", s.submitted_code AS \"submittedCode\", s.code AS \"draftCode\", s.score, "
                + "s.max_score AS \"maxScore\", s.detail, s.submit_count AS \"submitCount\", s.saved_at AS \"savedAt\", s.graded_at AS \"gradedAt\", "
                + "u.name AS \"studentName\", pr.teacher_id AS \"problemOwner\" FROM py_submission s JOIN users u ON u.id = s.student_id "
                + "JOIN py_problem pr ON pr.id = s.problem_id WHERE s.id = :id", p("id", submissionId));
        if (l.isEmpty()) throw new IllegalArgumentException("作答记录不存在");
        Map<String, Object> s = l.get(0);
        if (s.get("lessonId") != null) lessons.requireTeacher(teacherId, ((Number) s.get("lessonId")).longValue());
        else if (!teacherId.equals(((Number) s.get("problemOwner")).longValue())) throw new IllegalArgumentException("无权查看");
        s.put("detail", parseDetail((String) s.get("detail")));
        return s;
    }

    /** 题目修改后按学生最近提交的代码重新评测（不计入提交次数）。 */
    // 不加事务：沙箱运行可能较久，不应占用数据库连接；各步骤为单条语句自动提交
    public int regradeLessonPage(Long teacherId, Long lessonId, Long pageId) {
        lessons.requireTeacher(teacherId, lessonId);
        Map<String, Object> page = lessons.requirePageOfLesson(lessonId, pageId);
        if (page.get("refId") == null) return 0;
        Long problemId = ((Number) page.get("refId")).longValue();
        Map<String, Object> pr = problemRow(problemId);
        List<Map<String, Object>> cs = cases(problemId);
        List<Map<String, Object>> subs = jdbc.queryForList("SELECT id, submitted_code AS code FROM py_submission WHERE problem_id = :pr "
                + "AND lesson_id = :l AND page_id = :pg AND submitted_code IS NOT NULL", p("pr", problemId, "l", lessonId, "pg", pageId));
        for (Map<String, Object> s : subs) {
            String code = (String) s.get("code");
            storeGrade(((Number) s.get("id")).longValue(), pr, grade(pr, cs, code), code, false);
        }
        return subs.size();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
