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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 成绩开网：教师为本堂课指定一次测验和达标分数，达标的学生开网、其余保持断网；
 * 每人有 maxTries 次交卷机会（教师可单独加机会或手动放行）。
 * 定时与 CTR 实际状态对账：电脑重启、中途进课、被手动改动后都会回到规则要求的状态；下课后自动恢复网络。
 */
@Service
public class NetGateService {
    private static final Logger log = LoggerFactory.getLogger(NetGateService.class);

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
                + "t.title AS \"title\", g.pass_score AS \"passScore\", g.max_tries AS \"maxTries\", g.enabled "
                + "FROM lesson_net_gate g JOIN quiz_test t ON t.id = g.test_id WHERE g.lesson_id = :l", p("l", lessonId));
        return l.isEmpty() ? null : l.get(0);
    }

    @Transactional
    public void save(Long teacherId, Long lessonId, Long testId, double passScore, int maxTries, boolean enabled) {
        Map<String, Object> l = lessons.requireTeacher(teacherId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        if (!network.enabled()) throw new IllegalArgumentException("未启用网络控制器（管理员在“系统设置 → 网络控制器”中开启）");
        if (passScore < 0) throw new IllegalArgumentException("达标分数不能为负");
        if (maxTries < 1 || maxTries > 20) throw new IllegalArgumentException("机会次数为 1~20 次");
        jdbc.update("INSERT INTO lesson_net_gate(lesson_id, test_id, pass_score, max_tries, enabled) VALUES (:l, :t, :ps, :mt, :e) "
                        + "ON CONFLICT (lesson_id) DO UPDATE SET test_id = EXCLUDED.test_id, pass_score = EXCLUDED.pass_score, "
                        + "max_tries = EXCLUDED.max_tries, enabled = EXCLUDED.enabled",
                p("l", lessonId, "t", testId, "ps", passScore, "mt", maxTries, "e", enabled));
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
        Map<String, Map<String, Object>> st = network.enabled() ? network.statusByIp() : null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT u.id AS \"studentId\", u.name, u.student_no AS \"studentNo\", a.status AS attendance, a.ip_address AS ip, "
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
            r.put("state", passed ? "PASS" : (tries >= allowed && as != null && !"IN_PROGRESS".equals(as)) ? "EXHAUSTED" : as == null ? "NOT_STARTED" : "TRYING");
            Map<String, Object> agent = st == null || r.get("ip") == null ? null : st.get((String) r.get("ip"));
            r.put("net", agent == null || !Boolean.TRUE.equals(agent.get("online")) ? null : "normal".equals(agent.get("netState")) ? "on" : "off");
        }
        return rows;
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
        List<Map<String, Object>> g = jdbc.queryForList("SELECT lesson_id, pass_score, max_tries FROM lesson_net_gate WHERE test_id = :t AND enabled", p("t", testId));
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
        return out;
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
                            + "override = CASE WHEN EXCLUDED.passed THEN NULL ELSE lesson_net_gate_student.override END",
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
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.ip_address AS ip, COALESCE(s.passed, FALSE) AS passed, s.override "
                    + "FROM lesson_attendance a LEFT JOIN lesson_net_gate_student s ON s.lesson_id = a.lesson_id AND s.student_id = a.student_id "
                    + "WHERE a.lesson_id = :l AND a.status = 'PRESENT' AND a.ip_address IS NOT NULL "
                    + (onlyStudent == null ? "" : "AND a.student_id = :o"), p("l", lessonId, "o", onlyStudent));
            for (Map<String, Object> r : rows) {
                String ov = (String) r.get("override");
                boolean wantOpen = ov != null ? "OPEN".equals(ov) : Boolean.TRUE.equals(r.get("passed"));
                apply(st, (String) r.get("ip"), wantOpen);
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
