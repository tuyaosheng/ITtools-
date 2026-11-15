package com.ittools.classroom.service;

import com.ittools.lab.service.NetworkControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ittools.platform.db.Sql.p;

/**
 * 成绩开网：教师为本堂课指定一次测验和达标分数，还可以要求若干作品页达标、任务单（课堂调查页）提交、小组全员完成；
 * 小组模式下作品可设为组内有人达标就算。全部达标的学生开网、其余保持断网；
 * 每人有 maxTries 次交卷机会（教师可单独加机会或手动放行）。
 * 定时与 CTR 实际状态对账：电脑重启、中途进课、被手动改动后都会回到规则要求的状态；下课后自动恢复网络。
 */
@Service
public class NetGateService {
    private static final Logger log = LoggerFactory.getLogger(NetGateService.class);
    /** 作品页达标的条件：有提交、没被发回、教师分（没有则自动分）不低于要求。s 为 submission，g 为 lesson_net_gate。 */
    private static final String PROGRAM_OK = "NOT s.returned AND COALESCE(s.teacher_score, s.auto_score) >= g.homework_pass";

    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final NetworkControlService network;

    public NetGateService(NamedParameterJdbcTemplate jdbc, LessonService lessons, NetworkControlService network) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.network = network;
    }

    // ================= 教师：规则与名单 =================

    @Transactional(readOnly = true)
    public Map<String, Object> rule(Long lessonId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT g.lesson_id AS \"lessonId\", g.test_id AS \"testId\", t.page_id AS \"pageId\", "
                + "t.title AS \"title\", g.pass_score AS \"passScore\", g.max_tries AS \"maxTries\", g.enabled, g.homework_pass AS \"homeworkPass\", "
                + "g.require_group AS \"requireGroup\", g.group_programs AS \"groupPrograms\" "
                + "FROM lesson_net_gate g JOIN quiz_test t ON t.id = g.test_id WHERE g.lesson_id = :l", p("l", lessonId));
        if (l.isEmpty()) return null;
        l.get(0).put("pages", requiredPages(lessonId));
        return l.get(0);
    }

    /** 本堂课规则要求的页面（id、标题、类型）：作品页要达标，任务单要提交过。按课程页面顺序。 */
    private List<Map<String, Object>> requiredPages(Long lessonId) {
        return jdbc.queryForList("SELECT p.id, p.title, p.page_type AS \"pageType\" FROM lesson_net_gate_page gp JOIN course_page p ON p.id = gp.page_id "
                + "WHERE gp.lesson_id = :l ORDER BY p.seq, p.id", p("l", lessonId));
    }

    @Transactional
    public void save(Long teacherId, Long lessonId, Long testId, double passScore, int maxTries, boolean enabled,
                     List<Long> pageIds, double homeworkPass, boolean requireGroup, boolean groupPrograms) {
        Map<String, Object> l = lessons.requireTeacher(teacherId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        if (!network.enabled()) throw new IllegalArgumentException("未启用网络控制器（管理员在“系统设置 → 网络控制器”中开启）");
        if (passScore < 0) throw new IllegalArgumentException("达标分数不能为负");
        if (maxTries < 1 || maxTries > 20) throw new IllegalArgumentException("机会次数为 1~20 次");
        if (homeworkPass < 0) throw new IllegalArgumentException("作品达标分数不能为负");
        for (Long pageId : pageIds) {
            Object type = lessons.requirePageOfLesson(lessonId, pageId).get("pageType");
            if (!"HOMEWORK".equals(type) && !"SURVEY".equals(type)) throw new IllegalArgumentException("只能选择作品提交页面或课堂调查（任务单）页面");
        }
        jdbc.update("INSERT INTO lesson_net_gate(lesson_id, test_id, pass_score, max_tries, enabled, homework_pass, require_group, group_programs) "
                        + "VALUES (:l, :t, :ps, :mt, :e, :hp, :rg, :gp) "
                        + "ON CONFLICT (lesson_id) DO UPDATE SET test_id = EXCLUDED.test_id, pass_score = EXCLUDED.pass_score, "
                        + "max_tries = EXCLUDED.max_tries, enabled = EXCLUDED.enabled, homework_pass = EXCLUDED.homework_pass, require_group = EXCLUDED.require_group, "
                        + "group_programs = EXCLUDED.group_programs",
                p("l", lessonId, "t", testId, "ps", passScore, "mt", maxTries, "e", enabled, "hp", homeworkPass, "rg", requireGroup,
                        "gp", requireGroup && groupPrograms));
        jdbc.update("DELETE FROM lesson_net_gate_page WHERE lesson_id = :l", p("l", lessonId));
        for (Long pageId : pageIds) {
            jdbc.update("INSERT INTO lesson_net_gate_page(lesson_id, page_id) VALUES (:l, :p) ON CONFLICT DO NOTHING", p("l", lessonId, "p", pageId));
        }
        // 已经交过卷的学生按现有答卷计入（换测验时先清掉旧记录）
        jdbc.update("DELETE FROM lesson_net_gate_student WHERE lesson_id = :l AND NOT EXISTS "
                + "(SELECT 1 FROM quiz_attempt a WHERE a.test_id = :t AND a.student_id = lesson_net_gate_student.student_id) "
                + "AND extra_tries = 0 AND override IS NULL", p("l", lessonId, "t", testId));
        jdbc.update("INSERT INTO lesson_net_gate_student(lesson_id, student_id, tries, best_score, passed) "
                        + "SELECT :l, a.student_id, 1, a.score, a.score >= :ps FROM quiz_attempt a WHERE a.test_id = :t AND a.status = 'SUBMITTED' "
                        + "ON CONFLICT (lesson_id, student_id) DO UPDATE SET tries = GREATEST(lesson_net_gate_student.tries, 1), "
                        + "best_score = GREATEST(COALESCE(lesson_net_gate_student.best_score, EXCLUDED.best_score), EXCLUDED.best_score)",
                p("l", lessonId, "t", testId, "ps", passScore));
        // 改了达标分后重新判定谁达标
        jdbc.update("UPDATE lesson_net_gate_student SET passed = COALESCE(best_score >= :ps, FALSE) WHERE lesson_id = :l",
                p("l", lessonId, "ps", passScore));
        afterCommit(() -> {
            if (enabled) reconcile(lessonId, null);
            else release(lessonId);
        });
    }

    /** 取消规则：删除并恢复本堂课学生的网络。 */
    @Transactional
    public void remove(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        jdbc.update("DELETE FROM lesson_net_gate WHERE lesson_id = :l", p("l", lessonId));
        jdbc.update("DELETE FROM lesson_net_gate_student WHERE lesson_id = :l", p("l", lessonId));
        afterCommit(() -> release(lessonId));
    }

    /** 名单：每人的机会、最好成绩、是否达标、网络实际状态。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> students(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        Map<String, Object> g = rule(lessonId);
        if (g == null) return new ArrayList<>();
        int maxTries = ((Number) g.get("maxTries")).intValue();
        double homeworkPass = ((Number) g.get("homeworkPass")).doubleValue();
        Map<Long, Map<Long, Map<String, Object>>> subs = requiredSubmissions(lessonId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pages = (List<Map<String, Object>>) g.get("pages");
        boolean requireGroup = Boolean.TRUE.equals(g.get("requireGroup"));
        boolean shared = Boolean.TRUE.equals(g.get("groupPrograms"));
        Set<String> sheets = sheetsDone(lessonId);
        Map<Long, Map<String, Object>> decided = new HashMap<>();
        Map<Integer, Set<Long>> groupOk = new HashMap<>();
        for (Map<String, Object> m : decisions(lessonId)) {
            decided.put(((Number) m.get("studentId")).longValue(), m);
            @SuppressWarnings("unchecked") Set<Long> ok = (Set<Long>) m.get("groupOk");
            groupOk.put(((Number) m.get("groupNo")).intValue(), ok);
        }
        Map<String, Map<String, Object>> st = network.enabled() ? network.statusByIp() : null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT u.id AS \"studentId\", u.name, u.student_no AS \"studentNo\", a.status AS attendance, a.ip_address AS ip, COALESCE(a.group_no, 0) AS \"groupNo\", "
                        + "COALESCE(s.tries, 0) AS tries, COALESCE(s.extra_tries, 0) AS \"extraTries\", s.best_score AS \"bestScore\", "
                        + "COALESCE(s.passed, FALSE) AS passed, s.override, qa.status AS \"attemptStatus\" "
                        + "FROM lesson l JOIN users u ON u.class_id = l.class_id AND u.role = 'STUDENT' AND NOT u.graduated "
                        + "LEFT JOIN lesson_attendance a ON a.lesson_id = l.id AND a.student_id = u.id "
                        + "LEFT JOIN lesson_net_gate_student s ON s.lesson_id = l.id AND s.student_id = u.id "
                        + "LEFT JOIN quiz_attempt qa ON qa.test_id = :t AND qa.student_id = u.id "
                        + "WHERE l.id = :l ORDER BY u.student_no NULLS LAST, u.name", p("l", lessonId, "t", g.get("testId")));
        for (Map<String, Object> r : rows) {
            int tries = ((Number) r.get("tries")).intValue();
            int allowed = maxTries + ((Number) r.get("extraTries")).intValue();
            r.put("allowed", allowed);
            r.put("left", Math.max(0, allowed - tries));
            boolean passed = Boolean.TRUE.equals(r.get("passed"));
            String as = (String) r.get("attemptStatus");
            List<Map<String, Object>> programs = new ArrayList<>();
            boolean programsOk = true;
            boolean sheetsOk = true;
            long sid = ((Number) r.get("studentId")).longValue();
            int groupNo = ((Number) r.get("groupNo")).intValue();
            Map<Long, Map<String, Object>> mine = subs.getOrDefault(sid, new HashMap<>());
            for (Map<String, Object> pg : pages) {
                long pageId = ((Number) pg.get("id")).longValue();
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("pageId", pageId);
                if ("SURVEY".equals(pg.get("pageType"))) {
                    boolean ok = sheets.contains(sid + ":" + pageId);
                    one.put("ok", ok);
                    sheetsOk &= ok;
                } else {
                    Map<String, Object> sub = mine.get(pageId);
                    one.put("score", sub == null ? null : sub.get("score"));
                    one.put("returned", sub != null && Boolean.TRUE.equals(sub.get("returned")));
                    boolean ok = sub != null && !Boolean.TRUE.equals(sub.get("returned")) && sub.get("score") != null
                            && ((Number) sub.get("score")).doubleValue() >= homeworkPass;
                    one.put("ok", ok);
                    // 作品组内共享时，本人没交但组员交了也算
                    one.put("groupOk", shared && groupNo > 0 && groupOk.getOrDefault(groupNo, new HashSet<>()).contains(pageId));
                    if (!shared) programsOk &= ok;
                }
                programs.add(one);
            }
            r.put("programs", programs);
            Map<String, Object> me = decided.get(sid);
            r.put("groupMissing", me == null ? new ArrayList<>() : me.get("groupMissing"));
            String groupState = !requireGroup ? "PASS" : groupNo == 0 ? "NO_GROUP" : me != null && Boolean.TRUE.equals(me.get("open")) ? "PASS" : "GROUP";
            r.put("state", passed && !sheetsOk ? "SHEET" : passed && !programsOk ? "PROGRAM" : passed ? groupState
                    : (tries >= allowed && as != null && !"IN_PROGRESS".equals(as)) ? "EXHAUSTED" : as == null ? "NOT_STARTED" : "TRYING");
            Map<String, Object> agent = st == null || r.get("ip") == null ? null : st.get((String) r.get("ip"));
            r.put("net", agent == null || !Boolean.TRUE.equals(agent.get("online")) ? null : "normal".equals(agent.get("netState")) ? "on" : "off");
        }
        return rows;
    }

    /**
     * 本堂课出席学生的开网判定。done：本人完成（手动放行也算，手动断网不算）；open：规则要求开网；
     * missing：本人还差的作品和任务单标题（不含测验）；groupMissing：作品组内共享时，本组还没人达标的作品；groupOk：本组已有人达标的作品页。
     * 要求小组全员完成时，同组所有出席的同学都 done 才开网；请假、没进课的不算在内，他们交的作品也不算。手动放行只开被放行的学生。
     */
    private List<Map<String, Object>> decisions(Long lessonId) {
        List<Map<String, Object>> gs = jdbc.queryForList("SELECT require_group, group_programs FROM lesson_net_gate WHERE lesson_id = :l", p("l", lessonId));
        if (gs.isEmpty()) return new ArrayList<>();
        boolean requireGroup = Boolean.TRUE.equals(gs.get(0).get("require_group"));
        boolean shared = requireGroup && Boolean.TRUE.equals(gs.get(0).get("group_programs"));
        List<Map<String, Object>> pages = requiredPages(lessonId);
        Set<String> ok = sheetsDone(lessonId);
        for (Map<String, Object> s : jdbc.queryForList("SELECT s.student_id, s.page_id FROM submission s JOIN lesson_net_gate g ON g.lesson_id = s.lesson_id "
                + "JOIN lesson_net_gate_page gp ON gp.lesson_id = s.lesson_id AND gp.page_id = s.page_id WHERE s.lesson_id = :l AND " + PROGRAM_OK, p("l", lessonId))) {
            ok.add(s.get("student_id") + ":" + s.get("page_id"));
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.student_id AS \"studentId\", u.name, a.ip_address AS ip, COALESCE(a.group_no, 0) AS \"groupNo\", "
                + "COALESCE(n.passed, FALSE) AS passed, n.override "
                + "FROM lesson_attendance a JOIN users u ON u.id = a.student_id "
                + "LEFT JOIN lesson_net_gate_student n ON n.lesson_id = a.lesson_id AND n.student_id = a.student_id "
                + "WHERE a.lesson_id = :l AND a.status = 'PRESENT'", p("l", lessonId));
        Map<Integer, Set<Long>> groupOk = new HashMap<>();
        for (Map<String, Object> r : rows) {
            int group = ((Number) r.get("groupNo")).intValue();
            Set<Long> got = groupOk.computeIfAbsent(group, k -> new HashSet<>());
            for (Map<String, Object> pg : pages) {
                if ("HOMEWORK".equals(pg.get("pageType")) && ok.contains(r.get("studentId") + ":" + pg.get("id"))) got.add(((Number) pg.get("id")).longValue());
            }
        }
        Map<Integer, Boolean> groupDone = new HashMap<>();
        for (Map<String, Object> r : rows) {
            List<String> missing = new ArrayList<>();
            for (Map<String, Object> pg : pages) {
                if (shared && "HOMEWORK".equals(pg.get("pageType"))) continue;
                if (!ok.contains(r.get("studentId") + ":" + pg.get("id"))) missing.add((String) pg.get("title"));
            }
            Object ov = r.get("override");
            boolean done = ov != null ? "OPEN".equals(ov) : Boolean.TRUE.equals(r.get("passed")) && missing.isEmpty();
            r.put("missing", missing);
            r.put("done", done);
            groupDone.merge(((Number) r.get("groupNo")).intValue(), done, Boolean::logicalAnd);
        }
        for (Map<String, Object> r : rows) {
            int group = ((Number) r.get("groupNo")).intValue();
            Set<Long> got = groupOk.get(group);
            List<String> groupMissing = new ArrayList<>();
            if (shared && group > 0) {
                for (Map<String, Object> pg : pages) {
                    if ("HOMEWORK".equals(pg.get("pageType")) && !got.contains(((Number) pg.get("id")).longValue())) groupMissing.add((String) pg.get("title"));
                }
            }
            r.put("groupOk", group > 0 ? got : new HashSet<Long>());
            r.put("groupMissing", groupMissing);
            boolean done = Boolean.TRUE.equals(r.get("done"));
            r.put("open", r.get("override") != null ? done
                    : done && (!requireGroup || (group > 0 && groupDone.get(group) && groupMissing.isEmpty())));
        }
        return rows;
    }

    /** 已提交的任务单（课堂调查页），"学生id:页面id"。调查要全部填完才能提交，有作答记录就是交过。 */
    private Set<String> sheetsDone(Long lessonId) {
        Set<String> out = new HashSet<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT DISTINCT a.student_id, a.page_id FROM survey_answer a "
                + "JOIN lesson_net_gate_page gp ON gp.lesson_id = a.lesson_id AND gp.page_id = a.page_id WHERE a.lesson_id = :l", p("l", lessonId))) {
            out.add(r.get("student_id") + ":" + r.get("page_id"));
        }
        return out;
    }

    /** 学生 → 作品页 → 该页的提交（得分、是否被发回）。 */
    private Map<Long, Map<Long, Map<String, Object>>> requiredSubmissions(Long lessonId) {
        Map<Long, Map<Long, Map<String, Object>>> out = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT s.student_id, s.page_id, COALESCE(s.teacher_score, s.auto_score) AS score, s.returned "
                + "FROM submission s JOIN lesson_net_gate_page gp ON gp.lesson_id = s.lesson_id AND gp.page_id = s.page_id WHERE s.lesson_id = :l", p("l", lessonId))) {
            out.computeIfAbsent(((Number) r.get("student_id")).longValue(), k -> new HashMap<>()).put(((Number) r.get("page_id")).longValue(), r);
        }
        return out;
    }

    @Transactional
    public void grantTry(Long teacherId, Long lessonId, Long studentId, int n) {
        lessons.requireTeacher(teacherId, lessonId);
        if (rule(lessonId) == null) throw new IllegalArgumentException("还没有设置成绩开网规则");
        if (n < 1 || n > 10) throw new IllegalArgumentException("一次加 1~10 次机会");
        jdbc.update("INSERT INTO lesson_net_gate_student(lesson_id, student_id, extra_tries) VALUES (:l, :s, :n) "
                        + "ON CONFLICT (lesson_id, student_id) DO UPDATE SET extra_tries = lesson_net_gate_student.extra_tries + :n, override = NULL",
                p("l", lessonId, "s", studentId, "n", n));
    }

    /** 手动放行（OPEN）/ 手动断网（CUT）/ 恢复按成绩（null）。 */
    @Transactional
    public void setOverride(Long teacherId, Long lessonId, Long studentId, String mode) {
        lessons.requireTeacher(teacherId, lessonId);
        if (mode != null && !"OPEN".equals(mode) && !"CUT".equals(mode)) throw new IllegalArgumentException("无效操作");
        if (rule(lessonId) == null) throw new IllegalArgumentException("还没有设置成绩开网规则");
        jdbc.update("INSERT INTO lesson_net_gate_student(lesson_id, student_id, override) VALUES (:l, :s, :o) "
                        + "ON CONFLICT (lesson_id, student_id) DO UPDATE SET override = EXCLUDED.override",
                p("l", lessonId, "s", studentId, "o", mode));
        afterCommit(() -> reconcile(lessonId, studentId));
    }

    /** 座位图批量开网 / 断网时同步成手动状态，免得被对账改回去。 */
    @Transactional
    public void onManualBatch(Long lessonId, List<String> ips, boolean on) {
        if (rule(lessonId) == null || ips == null || ips.isEmpty()) return;
        List<Long> sids = jdbc.queryForList("SELECT student_id FROM lesson_attendance WHERE lesson_id = :l AND ip_address IN (:ips)",
                p("l", lessonId, "ips", ips), Long.class);
        for (Long sid : sids) {
            jdbc.update("INSERT INTO lesson_net_gate_student(lesson_id, student_id, override) VALUES (:l, :s, :o) "
                            + "ON CONFLICT (lesson_id, student_id) DO UPDATE SET override = EXCLUDED.override",
                    p("l", lessonId, "s", sid, "o", on ? "OPEN" : "CUT"));
        }
    }

    // ================= 学生 / 测验侧 =================

    /** 学生在该测验上的机会信息；该测验没有启用的成绩开网规则时返回 null。 */
    @Transactional(readOnly = true)
    public Map<String, Object> studentInfo(Long studentId, Long testId) {
        List<Map<String, Object>> g = jdbc.queryForList("SELECT lesson_id, pass_score, max_tries, require_group, group_programs FROM lesson_net_gate WHERE test_id = :t AND enabled", p("t", testId));
        if (g.isEmpty()) return null;
        Long lessonId = ((Number) g.get(0).get("lesson_id")).longValue();
        List<Map<String, Object>> s = jdbc.queryForList("SELECT tries, extra_tries, best_score, passed FROM lesson_net_gate_student WHERE lesson_id = :l AND student_id = :s",
                p("l", lessonId, "s", studentId));
        int tries = s.isEmpty() ? 0 : ((Number) s.get(0).get("tries")).intValue();
        int allowed = ((Number) g.get(0).get("max_tries")).intValue() + (s.isEmpty() ? 0 : ((Number) s.get(0).get("extra_tries")).intValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passScore", g.get(0).get("pass_score"));
        out.put("allowed", allowed);
        out.put("tries", tries);
        out.put("left", Math.max(0, allowed - tries));
        out.put("bestScore", s.isEmpty() ? null : s.get(0).get("best_score"));
        out.put("passed", !s.isEmpty() && Boolean.TRUE.equals(s.get(0).get("passed")));
        boolean requireGroup = Boolean.TRUE.equals(g.get(0).get("require_group"));
        out.put("requireGroup", requireGroup);
        out.put("groupPrograms", requireGroup && Boolean.TRUE.equals(g.get(0).get("group_programs")));
        List<Map<String, Object>> rows = decisions(lessonId);
        Map<String, Object> me = null;
        for (Map<String, Object> r : rows) if (studentId.equals(((Number) r.get("studentId")).longValue())) me = r;
        // 本人还没完成的作品和任务单：测验达标后，这里为空才会开网（作品组内共享时不含作品）
        out.put("missingPrograms", me != null ? me.get("missing") : jdbc.queryForList("SELECT p.title FROM lesson_net_gate_page gp "
                + "JOIN course_page p ON p.id = gp.page_id WHERE gp.lesson_id = :l ORDER BY p.seq, p.id", p("l", lessonId), String.class));
        if (requireGroup) putGroupProgress(out, rows, me);
        return out;
    }

    /** 学生看到的小组进度：所在组、是否已开网、本组还差的作品、同组还没完成的同学和各自差什么。 */
    private void putGroupProgress(Map<String, Object> out, List<Map<String, Object>> rows, Map<String, Object> me) {
        int group = me == null ? 0 : ((Number) me.get("groupNo")).intValue();
        out.put("open", me != null && Boolean.TRUE.equals(me.get("open")));
        out.put("groupNo", group);
        out.put("groupMissing", me == null ? new ArrayList<>() : me.get("groupMissing"));
        List<Map<String, Object>> waiting = new ArrayList<>();
        if (group > 0) {
            for (Map<String, Object> r : rows) {
                if (r == me || ((Number) r.get("groupNo")).intValue() != group || Boolean.TRUE.equals(r.get("done"))) continue;
                List<String> missing = new ArrayList<>();
                if (!Boolean.TRUE.equals(r.get("passed"))) missing.add("测验");
                @SuppressWarnings("unchecked") List<String> rest = (List<String>) r.get("missing");
                missing.addAll(rest);
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("name", r.get("name"));
                w.put("missing", missing);
                waiting.add(w);
            }
        }
        out.put("waiting", waiting);
    }

    /** 学生交卷 / 教师改分后调用：countTry=true 记一次机会，达标则标记并开网。 */
    @Transactional
    public void onScored(Long testId, Long studentId, double score, boolean countTry) {
        List<Map<String, Object>> gs = jdbc.queryForList("SELECT lesson_id, pass_score FROM lesson_net_gate WHERE test_id = :t AND enabled", p("t", testId));
        for (Map<String, Object> g : gs) {
            Long lessonId = ((Number) g.get("lesson_id")).longValue();
            boolean pass = score >= ((Number) g.get("pass_score")).doubleValue();
            jdbc.update("INSERT INTO lesson_net_gate_student(lesson_id, student_id, tries, best_score, passed) VALUES (:l, :s, :n, :sc, :ps) "
                            + "ON CONFLICT (lesson_id, student_id) DO UPDATE SET tries = lesson_net_gate_student.tries + :n, "
                            + "best_score = GREATEST(COALESCE(lesson_net_gate_student.best_score, EXCLUDED.best_score), EXCLUDED.best_score), "
                            + "passed = lesson_net_gate_student.passed OR EXCLUDED.passed, "
                            // 测验达标只解除手动断网；手动放行要保留，学生可能还有作品没完成
                            + "override = CASE WHEN EXCLUDED.passed AND lesson_net_gate_student.override = 'CUT' THEN NULL ELSE lesson_net_gate_student.override END",
                    p("l", lessonId, "s", studentId, "n", countTry ? 1 : 0, "sc", score, "ps", pass));
            afterCommit(() -> reconcile(lessonId, studentId));
        }
    }

    // ================= 对账：让电脑的网络状态符合规则 =================

    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void reconcileAll() {
        try {
            if (!network.enabled()) return;
            for (Map<String, Object> g : jdbc.queryForList("SELECT g.lesson_id, l.status FROM lesson_net_gate g JOIN lesson l ON l.id = g.lesson_id WHERE g.enabled", p())) {
                Long lessonId = ((Number) g.get("lesson_id")).longValue();
                if ("ACTIVE".equals(g.get("status"))) {
                    reconcile(lessonId, null);
                } else {
                    // 下课：恢复网络并停用规则
                    release(lessonId);
                    jdbc.update("UPDATE lesson_net_gate SET enabled = FALSE WHERE lesson_id = :l", p("l", lessonId));
                }
            }
        } catch (RuntimeException e) {
            log.debug("成绩开网对账失败：{}", e.getMessage());
        }
    }

    private void reconcile(Long lessonId, Long onlyStudent) {
        try {
            Map<String, Map<String, Object>> st = network.enabled() ? network.statusByIp() : null;
            if (st == null) return;
            boolean requireGroup = !jdbc.queryForList("SELECT 1 FROM lesson_net_gate WHERE lesson_id = :l AND require_group", p("l", lessonId)).isEmpty();
            for (Map<String, Object> r : decisions(lessonId)) {
                // 要求全组完成时，一个人的变化会影响同组所有人
                if (r.get("ip") == null || (onlyStudent != null && !requireGroup && !onlyStudent.equals(((Number) r.get("studentId")).longValue()))) continue;
                apply(st, (String) r.get("ip"), Boolean.TRUE.equals(r.get("open")));
            }
        } catch (RuntimeException e) {
            log.debug("成绩开网对账失败（课堂 {}）：{}", lessonId, e.getMessage());
        }
    }

    /** 恢复本堂课所有出席学生的网络（取消规则 / 下课）。 */
    private void release(Long lessonId) {
        try {
            Map<String, Map<String, Object>> st = network.enabled() ? network.statusByIp() : null;
            if (st == null) return;
            for (String ip : jdbc.queryForList("SELECT ip_address FROM lesson_attendance WHERE lesson_id = :l AND status = 'PRESENT' AND ip_address IS NOT NULL",
                    p("l", lessonId), String.class)) {
                apply(st, ip, true);
            }
        } catch (RuntimeException e) {
            log.debug("成绩开网恢复网络失败（课堂 {}）：{}", lessonId, e.getMessage());
        }
    }

    /** 只在电脑在线且实际状态与期望不同时下发指令（CTR 的 normal = 网络正常）。 */
    private void apply(Map<String, Map<String, Object>> st, String ip, boolean wantOpen) {
        Map<String, Object> agent = st.get(ip);
        if (agent == null || !Boolean.TRUE.equals(agent.get("online"))) return;
        boolean open = "normal".equals(agent.get("netState"));
        if (open == wantOpen) return;
        try {
            network.setIp(ip, wantOpen);
        } catch (RuntimeException e) {
            log.debug("{} {} 失败：{}", wantOpen ? "开网" : "断网", ip, e.getMessage());
        }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
