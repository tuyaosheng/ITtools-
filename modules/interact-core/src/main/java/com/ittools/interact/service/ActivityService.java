package com.ittools.interact.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.classroom.service.CourseService;
import com.ittools.classroom.service.LessonService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ittools.platform.db.Sql.p;

/**
 * 课堂调查（原 coursecheck / editcoursecheck）与随机抽题（原 courseTakequestions）。
 * 题目配置存放在 course_page.config（JSON）中，随课程复制与课程包导出。
 */
@Service
public class ActivityService {
    public static final List<String> SURVEY_TYPES = Arrays.asList("JUDGE", "SINGLE", "MULTI", "FILL");
    public static final List<String> NAME_MODES = Arrays.asList("NONE", "TEACHER", "ALL");

    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final CourseService courses;
    private final ObjectMapper json = new ObjectMapper();

    public ActivityService(NamedParameterJdbcTemplate jdbc, LessonService lessons, CourseService courses) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.courses = courses;
    }

    // ================= 配置读写 =================

    private Map<String, Object> config(Long pageId) {
        List<String> l = jdbc.queryForList("SELECT config FROM course_page WHERE id = :id", p("id", pageId), String.class);
        if (l.isEmpty()) throw new IllegalArgumentException("页面不存在");
        if (l.get(0) == null || l.get(0).isEmpty()) return new LinkedHashMap<>();
        try {
            return json.readValue(l.get(0), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private void saveConfig(Long pageId, Map<String, Object> cfg) {
        try {
            jdbc.update("UPDATE course_page SET config = :c WHERE id = :id", p("c", json.writeValueAsString(cfg), "id", pageId));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> ownedPage(Long teacherId, Long pageId, String type) {
        Map<String, Object> pg = courses.page(pageId);
        courses.requireOwner(teacherId, ((Number) pg.get("courseId")).longValue());
        if (!type.equals(pg.get("pageType"))) throw new IllegalArgumentException("页面类型不匹配");
        return pg;
    }

    /** 教师端读取配置（编辑页面用）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> teacherConfig(Long teacherId, Long pageId) {
        Map<String, Object> pg = courses.page(pageId);
        courses.getReadable(teacherId, ((Number) pg.get("courseId")).longValue());
        Map<String, Object> cfg = config(pageId);
        Long answered = jdbc.queryForObject("SELECT COUNT(DISTINCT item_id) FROM survey_answer WHERE page_id = :p", p("p", pageId), Long.class);
        cfg.put("answeredItems", jdbc.queryForList("SELECT DISTINCT item_id FROM survey_answer WHERE page_id = :p", p("p", pageId), String.class));
        cfg.put("hasAnswers", answered != null && answered > 0);
        return cfg;
    }

    // ================= 课堂调查 =================

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> cfg) {
        Object o = cfg.get("items");
        return o instanceof List ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> options(Map<String, Object> item) {
        Object o = item.get("options");
        return o instanceof List ? (List<String>) o : new ArrayList<>();
    }

    /** 保存调查题目。已有作答的题目不能删除、不能改题型和选项个数（原系统：已有调查记录后只能编辑题目内容）。 */
    @Transactional
    public Map<String, Object> saveSurvey(Long teacherId, Long pageId, Map<String, Object> body) {
        ownedPage(teacherId, pageId, "SURVEY");
        Object nm = body.get("nameMode");
        String nameMode = nm == null ? "NONE" : String.valueOf(nm);
        if (!NAME_MODES.contains(nameMode)) throw new IllegalArgumentException("学生名字显示设置无效");
        Object rawItems = body.get("items");
        if (!(rawItems instanceof List)) throw new IllegalArgumentException("请添加调查题目");
        List<?> list = (List<?>) rawItems;
        if (list.isEmpty() || list.size() > 50) throw new IllegalArgumentException("调查题目为 1~50 道");
        Map<String, Map<String, Object>> old = new HashMap<>();
        for (Map<String, Object> it : items(config(pageId))) old.put(String.valueOf(it.get("id")), it);
        Set<String> answered = new HashSet<>(jdbc.queryForList("SELECT DISTINCT item_id FROM survey_answer WHERE page_id = :p", p("p", pageId), String.class));
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object o : list) {
            if (!(o instanceof Map)) throw new IllegalArgumentException("题目格式错误");
            Map<?, ?> m = (Map<?, ?>) o;
            String qtype = String.valueOf(m.get("qtype"));
            if (!SURVEY_TYPES.contains(qtype)) throw new IllegalArgumentException("题型无效");
            String stem = m.get("stem") == null ? "" : String.valueOf(m.get("stem")).trim();
            if (stem.isEmpty() || stem.length() > 500) throw new IllegalArgumentException("题目内容为 1~500 字");
            List<String> opts = new ArrayList<>();
            if ("SINGLE".equals(qtype) || "MULTI".equals(qtype)) {
                Object ol = m.get("options");
                if (ol instanceof List) for (Object x : (List<?>) ol) opts.add(x == null ? "" : String.valueOf(x).trim());
                if (opts.size() < 2 || opts.size() > 8) throw new IllegalArgumentException("「" + stem + "」选项为 2~8 个");
                for (String x : opts) if (x.isEmpty() || x.length() > 100) throw new IllegalArgumentException("「" + stem + "」选项内容为 1~100 字");
            }
            String id = m.get("id") == null || String.valueOf(m.get("id")).isEmpty() ? UUID.randomUUID().toString().substring(0, 8) : String.valueOf(m.get("id"));
            if (!seen.add(id)) throw new IllegalArgumentException("题目编号重复");
            if (answered.contains(id)) {
                Map<String, Object> prev = old.get(id);
                if (prev != null && (!qtype.equals(prev.get("qtype")) || options(prev).size() != opts.size())) {
                    throw new IllegalArgumentException("「" + stem + "」已有学生作答，不能修改题型或选项个数");
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("qtype", qtype);
            item.put("stem", stem);
            if (!opts.isEmpty()) item.put("options", opts);
            out.add(item);
        }
        for (String a : answered) if (!seen.contains(a)) throw new IllegalArgumentException("已有学生作答的题目不能删除");
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("nameMode", nameMode);
        cfg.put("showResult", !Boolean.FALSE.equals(body.get("showResult")));
        cfg.put("items", out);
        saveConfig(pageId, cfg);
        return cfg;
    }

    private Map<String, Object> answersOf(Long lessonId, Long pageId, Long studentId) {
        Map<String, Object> m = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT item_id, answer FROM survey_answer WHERE lesson_id = :l AND page_id = :p AND student_id = :s",
                p("l", lessonId, "p", pageId, "s", studentId))) {
            m.put((String) r.get("item_id"), parse((String) r.get("answer")));
        }
        return m;
    }

    private Object parse(String s) {
        try {
            return json.readValue(s, Object.class);
        } catch (IOException e) {
            return s;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentSurvey(Long studentId, Long lessonId, Long pageId) {
        lessons.studentPage(studentId, lessonId, pageId);
        Map<String, Object> cfg = config(pageId);
        Map<String, Object> mine = answersOf(lessonId, pageId, studentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items(cfg));
        out.put("answers", mine);
        out.put("submitted", !mine.isEmpty());
        boolean show = !mine.isEmpty() && !Boolean.FALSE.equals(cfg.get("showResult"));
        out.put("results", show ? results(Arrays.asList(lessonId), pageId, cfg, "ALL".equals(cfg.get("nameMode"))) : null);
        return out;
    }

    /** 提交/修改调查（课堂进行中可修改）。全部题目都要作答。 */
    @Transactional
    public void submitSurvey(Long studentId, Long lessonId, Long pageId, Map<String, Object> answers) {
        Map<String, Object> l = lessons.requireStudent(studentId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        lessons.studentPage(studentId, lessonId, pageId);
        Map<String, Object> cfg = config(pageId);
        List<Map<String, Object>> its = items(cfg);
        if (its.isEmpty()) throw new IllegalArgumentException("教师还没有设置调查题目");
        if (answers == null) answers = new HashMap<>();
        for (Map<String, Object> it : its) {
            String id = String.valueOf(it.get("id"));
            Object a = answers.get(id);
            String qtype = String.valueOf(it.get("qtype"));
            int n = options(it).size();
            Object norm;
            switch (qtype) {
                case "JUDGE":
                    if (!"T".equals(a) && !"F".equals(a)) throw new IllegalArgumentException("请完成第 " + (its.indexOf(it) + 1) + " 题");
                    norm = a;
                    break;
                case "SINGLE":
                    if (!(a instanceof String) || !validLetter((String) a, n)) throw new IllegalArgumentException("请完成第 " + (its.indexOf(it) + 1) + " 题");
                    norm = a;
                    break;
                case "MULTI":
                    List<String> picks = new ArrayList<>();
                    if (a instanceof List) for (Object x : (List<?>) a) if (x instanceof String && validLetter((String) x, n) && !picks.contains(x)) picks.add((String) x);
                    if (picks.isEmpty()) throw new IllegalArgumentException("请完成第 " + (its.indexOf(it) + 1) + " 题");
                    java.util.Collections.sort(picks);
                    norm = picks;
                    break;
                default:
                    String txt = a == null ? "" : String.valueOf(a).trim();
                    if (txt.isEmpty()) throw new IllegalArgumentException("请完成第 " + (its.indexOf(it) + 1) + " 题");
                    if (txt.length() > 500) throw new IllegalArgumentException("填空内容不超过 500 字");
                    norm = txt;
            }
            try {
                jdbc.update("INSERT INTO survey_answer(lesson_id, page_id, item_id, student_id, answer) VALUES (:l, :p, :i, :s, :a) "
                                + "ON CONFLICT (lesson_id, page_id, item_id, student_id) DO UPDATE SET answer = EXCLUDED.answer, answered_at = now()",
                        p("l", lessonId, "p", pageId, "i", id, "s", studentId, "a", json.writeValueAsString(norm)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean validLetter(String s, int n) {
        return s.length() == 1 && s.charAt(0) >= 'A' && s.charAt(0) < 'A' + n;
    }

    /** 统计：判断/单选/多选按选项计数，填空列出内容（按设置附带学生名）。 */
    private Map<String, Object> results(List<Long> lessonIds, Long pageId, Map<String, Object> cfg, boolean withNames) {
        List<Map<String, Object>> rows = lessonIds.isEmpty() ? new ArrayList<>() : jdbc.queryForList(
                "SELECT a.item_id, a.answer, a.student_id, u.name FROM survey_answer a JOIN users u ON u.id = a.student_id "
                        + "WHERE a.page_id = :p AND a.lesson_id IN (:ls) ORDER BY u.student_no NULLS LAST, u.name", p("p", pageId, "ls", lessonIds));
        Set<Object> respondents = new HashSet<>();
        Map<String, List<Map<String, Object>>> byItem = new HashMap<>();
        for (Map<String, Object> r : rows) {
            respondents.add(r.get("student_id"));
            byItem.computeIfAbsent((String) r.get("item_id"), k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> stats = new ArrayList<>();
        for (Map<String, Object> it : items(cfg)) {
            String qtype = String.valueOf(it.get("qtype"));
            List<Map<String, Object>> ans = byItem.getOrDefault(String.valueOf(it.get("id")), new ArrayList<>());
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("id", it.get("id"));
            st.put("count", ans.size());
            if ("FILL".equals(qtype)) {
                List<Map<String, Object>> texts = new ArrayList<>();
                for (Map<String, Object> a : ans) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("text", parse((String) a.get("answer")));
                    if (withNames) t.put("name", a.get("name"));
                    texts.add(t);
                }
                st.put("texts", texts);
            } else {
                List<String> keys = new ArrayList<>();
                if ("JUDGE".equals(qtype)) keys = Arrays.asList("T", "F");
                else for (int i = 0; i < options(it).size(); i++) keys.add(String.valueOf((char) ('A' + i)));
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (String k : keys) counts.put(k, 0);
                for (Map<String, Object> a : ans) {
                    Object v = parse((String) a.get("answer"));
                    List<Object> vs = v instanceof List ? new ArrayList<>((List<?>) v) : Arrays.asList(v);
                    for (Object x : vs) if (counts.containsKey(String.valueOf(x))) counts.merge(String.valueOf(x), 1, Integer::sum);
                }
                st.put("counts", counts);
            }
            stats.add(st);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("respondents", respondents.size());
        out.put("items", stats);
        return out;
    }

    /** 教师查看调查结果：scope=CLASS 本堂课；GRADE 同年级各班上过该页面的课堂（原“查看年级段结果”）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> teacherSurvey(Long teacherId, Long lessonId, Long pageId, String scope) {
        lessons.requireTeacher(teacherId, lessonId);
        lessons.requirePageOfLesson(lessonId, pageId);
        Map<String, Object> cfg = config(pageId);
        List<Long> ids;
        if ("GRADE".equals(scope)) {
            ids = jdbc.queryForList("SELECT DISTINCT l.id FROM lesson l JOIN klass k ON k.id = l.class_id "
                    + "WHERE k.school_year_id = (SELECT k2.school_year_id FROM lesson l2 JOIN klass k2 ON k2.id = l2.class_id WHERE l2.id = :l) "
                    + "AND l.id IN (SELECT lesson_id FROM survey_answer WHERE page_id = :p)", p("l", lessonId, "p", pageId), Long.class);
        } else {
            ids = Arrays.asList(lessonId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("config", cfg);
        out.put("results", results(ids, pageId, cfg, !"NONE".equals(cfg.get("nameMode")) && !"GRADE".equals(scope)));
        out.put("classSize", jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id = :l AND status = 'PRESENT'", p("l", lessonId), Long.class));
        if ("GRADE".equals(scope)) out.put("lessons", ids.size());
        return out;
    }

    // ================= 随机抽题 =================

    @SuppressWarnings("unchecked")
    private static List<String> questions(Map<String, Object> cfg) {
        Object o = cfg.get("questions");
        return o instanceof List ? (List<String>) o : new ArrayList<>();
    }

    @Transactional
    public Map<String, Object> saveQuestions(Long teacherId, Long pageId, List<?> raw) {
        ownedPage(teacherId, pageId, "TAKE_QUESTION");
        if (raw == null || raw.isEmpty() || raw.size() > 60) throw new IllegalArgumentException("题目为 1~60 道");
        List<String> qs = new ArrayList<>();
        for (Object o : raw) {
            String s = o == null ? "" : String.valueOf(o).trim();
            if (s.isEmpty() || s.length() > 2000) throw new IllegalArgumentException("每道题内容为 1~2000 字");
            qs.add(s);
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("questions", qs);
        saveConfig(pageId, cfg);
        return cfg;
    }

    /** 题号 = 学号 mod 题目数（余 0 取最后一题），与原系统一致：相邻学号拿到不同题目。学号无数字时按用户 id。 */
    static int questionNo(String studentNo, long studentId, int count) {
        long base = studentId;
        if (studentNo != null) {
            String digits = studentNo.replaceAll("\\D", "");
            if (!digits.isEmpty()) base = Long.parseLong(digits.length() > 15 ? digits.substring(digits.length() - 15) : digits);
        }
        int no = (int) (base % count);
        return no == 0 ? count : no;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentTake(Long studentId, Long lessonId, Long pageId) {
        lessons.studentPage(studentId, lessonId, pageId);
        List<String> qs = questions(config(pageId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", qs.size());
        List<Integer> d = jdbc.queryForList("SELECT question_no FROM take_question_draw WHERE lesson_id = :l AND page_id = :p AND student_id = :s",
                p("l", lessonId, "p", pageId, "s", studentId), Integer.class);
        if (!d.isEmpty() && d.get(0) <= qs.size()) {
            out.put("no", d.get(0));
            out.put("question", qs.get(d.get(0) - 1));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> draw(Long studentId, Long lessonId, Long pageId) {
        Map<String, Object> l = lessons.requireStudent(studentId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        lessons.studentPage(studentId, lessonId, pageId);
        List<String> qs = questions(config(pageId));
        if (qs.isEmpty()) throw new IllegalArgumentException("教师还没有设置题目");
        String no = jdbc.queryForObject("SELECT student_no FROM users WHERE id = :s", p("s", studentId), String.class);
        jdbc.update("INSERT INTO take_question_draw(lesson_id, page_id, student_id, question_no) VALUES (:l, :p, :s, :n) "
                + "ON CONFLICT (lesson_id, page_id, student_id) DO NOTHING", p("l", lessonId, "p", pageId, "s", studentId, "n", questionNo(no, studentId, qs.size())));
        return studentTake(studentId, lessonId, pageId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> teacherTake(Long teacherId, Long lessonId, Long pageId) {
        lessons.requireTeacher(teacherId, lessonId);
        lessons.requirePageOfLesson(lessonId, pageId);
        List<String> qs = questions(config(pageId));
        List<Map<String, Object>> students = jdbc.queryForList("SELECT u.id AS \"studentId\", u.student_no AS \"studentNo\", u.name, a.status, "
                + "d.question_no AS \"drawnNo\", d.drawn_at AS \"drawnAt\" FROM lesson l JOIN users u ON u.class_id = l.class_id AND u.role = 'STUDENT' AND NOT u.graduated "
                + "LEFT JOIN lesson_attendance a ON a.lesson_id = l.id AND a.student_id = u.id "
                + "LEFT JOIN take_question_draw d ON d.lesson_id = l.id AND d.page_id = :p AND d.student_id = u.id "
                + "WHERE l.id = :l ORDER BY u.student_no NULLS LAST, u.name", p("l", lessonId, "p", pageId));
        for (Map<String, Object> s : students) {
            if (!qs.isEmpty()) s.put("questionNo", questionNo((String) s.get("studentNo"), ((Number) s.get("studentId")).longValue(), qs.size()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questions", qs);
        out.put("students", students);
        return out;
    }

    // ================= 随机点名（原 stu_dianming） =================

    /** 从本堂课出席的学生中随机点一人；repeat=false 时本堂课已点过的不再点（全部点完后重新开始一轮）。 */
    @Transactional
    public Map<String, Object> rollCall(Long teacherId, Long lessonId, boolean repeat) {
        Map<String, Object> l = lessons.requireTeacher(teacherId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束，不能点名");
        List<Map<String, Object>> present = jdbc.queryForList("SELECT u.id AS \"studentId\", u.name, u.student_no AS \"studentNo\", a.pc_name AS \"pcName\" "
                + "FROM lesson_attendance a JOIN users u ON u.id = a.student_id WHERE a.lesson_id = :l AND a.status = 'PRESENT'", p("l", lessonId));
        if (present.isEmpty()) throw new IllegalArgumentException("还没有学生进入课堂");
        List<Map<String, Object>> pool = new ArrayList<>(present);
        if (!repeat) {
            Set<Long> called = new HashSet<>(jdbc.queryForList("SELECT student_id FROM roll_call WHERE lesson_id = :l", p("l", lessonId), Long.class));
            pool.removeIf(s -> called.contains(((Number) s.get("studentId")).longValue()));
            if (pool.isEmpty()) {
                jdbc.update("DELETE FROM roll_call WHERE lesson_id = :l", p("l", lessonId));
                pool = new ArrayList<>(present);
            }
        }
        Map<String, Object> pick = pool.get(new java.security.SecureRandom().nextInt(pool.size()));
        jdbc.update("INSERT INTO roll_call(lesson_id, student_id) VALUES (:l, :s)", p("l", lessonId, "s", pick.get("studentId")));
        Map<String, Object> out = new LinkedHashMap<>(pick);
        out.put("candidates", present);
        return out;
    }

    /** 点名面板：出席名单、缺席/请假名单（含原因）、已点记录。 */
    @Transactional(readOnly = true)
    public Map<String, Object> rollCallOverview(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("students", jdbc.queryForList("SELECT u.id AS \"studentId\", u.name, u.student_no AS \"studentNo\", "
                + "COALESCE(a.status, 'ABSENT') AS status, a.pc_name AS \"pcName\", a.leave_reason AS \"leaveReason\" "
                + "FROM lesson l JOIN users u ON u.class_id = l.class_id AND u.role = 'STUDENT' AND NOT u.graduated "
                + "LEFT JOIN lesson_attendance a ON a.lesson_id = l.id AND a.student_id = u.id WHERE l.id = :l "
                + "ORDER BY u.student_no NULLS LAST, u.name", p("l", lessonId)));
        out.put("called", jdbc.queryForList("SELECT r.student_id AS \"studentId\", u.name, r.called_at AS \"calledAt\" FROM roll_call r "
                + "JOIN users u ON u.id = r.student_id WHERE r.lesson_id = :l ORDER BY r.id DESC", p("l", lessonId)));
        return out;
    }
}
