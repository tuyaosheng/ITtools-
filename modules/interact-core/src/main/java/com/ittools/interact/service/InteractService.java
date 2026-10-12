package com.ittools.interact.service;

import com.ittools.classroom.service.LessonService;
import com.ittools.lab.service.LabQueryService;
import com.ittools.platform.settings.SystemSettingService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ittools.platform.db.Sql.p;

/**
 * 子项目 6：举手求助、教师广播、小组讨论、抢答、在线监控、行为日志、我的小组。
 */
@Service
public class InteractService {
    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final LessonEvents events;
    private final LabQueryService lab;
    private final SystemSettingService settings;
    /** 在线状态：lessonId -> studentId -> {pageId, pageTitle, at} */
    private final Map<Long, Map<Long, Map<String, Object>>> presence = new ConcurrentHashMap<>();

    public InteractService(NamedParameterJdbcTemplate jdbc, LessonService lessons, LessonEvents events,
                           LabQueryService lab, SystemSettingService settings) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.events = events;
        this.lab = lab;
        this.settings = settings;
    }

    private void requireActive(Map<String, Object> lesson) {
        if (!"ACTIVE".equals(lesson.get("status"))) throw new IllegalArgumentException("课堂已结束");
    }

    // ================= 举手求助 =================

    @Transactional
    public Map<String, Object> raiseHand(Long studentId, Long lessonId, String message, String ip) {
        requireActive(lessons.requireStudent(studentId, lessonId));
        Long open = jdbc.queryForObject("SELECT COUNT(*) FROM help_request WHERE lesson_id = :l AND student_id = :s AND status = 'OPEN'",
                p("l", lessonId, "s", studentId), Long.class);
        if (open != null && open > 0) throw new IllegalArgumentException("你已经举手了，请等待老师处理");
        String pc = (String) lab.workstationByIp(ip).get("pcName");
        Long id = jdbc.queryForObject("INSERT INTO help_request(lesson_id, student_id, pc_name, message) VALUES (:l, :s, :pc, :m) RETURNING id",
                p("l", lessonId, "s", studentId, "pc", pc, "m", settings.filterSensitive(message)), Long.class);
        Map<String, Object> h = help(id);
        events.toTeacher(lessonId, "HELP", h);
        log(lessonId, studentId, "HELP", message);
        return h;
    }

    @Transactional
    public void cancelHand(Long studentId, Long lessonId) {
        lessons.requireStudent(studentId, lessonId);
        jdbc.update("UPDATE help_request SET status = 'RESOLVED', resolved_at = now() WHERE lesson_id = :l AND student_id = :s AND status = 'OPEN'",
                p("l", lessonId, "s", studentId));
        events.toTeacher(lessonId, "HELP_CHANGED", null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> helps(Long lessonId) {
        return jdbc.queryForList("SELECT h.id, h.student_id AS \"studentId\", u.name AS \"studentName\", h.pc_name AS \"pcName\", h.message, "
                + "h.status, h.created_at AS \"createdAt\", h.resolved_at AS \"resolvedAt\", hp.name AS \"helperName\" "
                + "FROM help_request h JOIN users u ON u.id = h.student_id LEFT JOIN users hp ON hp.id = h.helper_id "
                + "WHERE h.lesson_id = :l ORDER BY h.status = 'OPEN' DESC, h.id", p("l", lessonId));
    }

    @Transactional
    public void resolveHelp(Long teacherId, Long helpId) {
        Map<String, Object> h = help(helpId);
        Long lessonId = ((Number) h.get("lessonId")).longValue();
        lessons.requireTeacher(teacherId, lessonId);
        jdbc.update("UPDATE help_request SET status = 'RESOLVED', resolved_at = now() WHERE id = :id", p("id", helpId));
        events.toClass(lessonId, "HELP_RESOLVED", h.get("studentId"));
    }

    /**
     * 登记“收到谁的帮助”（原 coursehelplist）：求助的学生指定帮助自己的同学，同时结束本次求助。
     * 只能登记本堂课已进课的同班同学，不能是自己。
     */
    @Transactional
    public void thankHelper(Long studentId, Long lessonId, Long helperId) {
        requireActive(lessons.requireStudent(studentId, lessonId));
        if (helperId == null || helperId.equals(studentId)) throw new IllegalArgumentException("请选择帮助你的同学");
        Long present = jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id = :l AND student_id = :h",
                p("l", lessonId, "h", helperId), Long.class);
        if (present == null || present == 0) throw new IllegalArgumentException("该同学不在本堂课中");
        List<Long> open = jdbc.queryForList("SELECT id FROM help_request WHERE lesson_id = :l AND student_id = :s AND status = 'OPEN'",
                p("l", lessonId, "s", studentId), Long.class);
        if (open.isEmpty()) {
            jdbc.update("INSERT INTO help_request(lesson_id, student_id, message, status, resolved_at, helper_id) "
                    + "VALUES (:l, :s, '同学互助', 'RESOLVED', now(), :h)", p("l", lessonId, "s", studentId, "h", helperId));
        } else {
            jdbc.update("UPDATE help_request SET status = 'RESOLVED', resolved_at = now(), helper_id = :h WHERE id = :id",
                    p("h", helperId, "id", open.get(0)));
        }
        events.toTeacher(lessonId, "HELP_CHANGED", null);
        log(lessonId, studentId, "HELPED_BY", String.valueOf(helperId));
    }

    /** 本堂课互助榜：每位同学帮助了几人次。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> helpers(Long lessonId) {
        return jdbc.queryForList("SELECT h.helper_id AS \"studentId\", u.name, COUNT(*) AS count FROM help_request h JOIN users u ON u.id = h.helper_id "
                + "WHERE h.lesson_id = :l AND h.helper_id IS NOT NULL GROUP BY h.helper_id, u.name ORDER BY count DESC, u.name", p("l", lessonId));
    }

    /** 学生可选的“帮助我的同学”：本堂课已进课的其他同学。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> classmatesInLesson(Long studentId, Long lessonId) {
        return jdbc.queryForList("SELECT u.id, u.name FROM lesson_attendance a JOIN users u ON u.id = a.student_id "
                + "WHERE a.lesson_id = :l AND a.student_id <> :s AND a.status = 'PRESENT' ORDER BY u.student_no, u.name", p("l", lessonId, "s", studentId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myHelp(Long studentId, Long lessonId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT id, message, created_at AS \"createdAt\" FROM help_request "
                + "WHERE lesson_id = :l AND student_id = :s AND status = 'OPEN'", p("l", lessonId, "s", studentId));
        return l.isEmpty() ? null : l.get(0);
    }

    private Map<String, Object> help(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT h.id, h.lesson_id AS \"lessonId\", h.student_id AS \"studentId\", "
                + "u.name AS \"studentName\", h.pc_name AS \"pcName\", h.message, h.status, h.created_at AS \"createdAt\" "
                + "FROM help_request h JOIN users u ON u.id = h.student_id WHERE h.id = :id", p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("求助不存在");
        return l.get(0);
    }

    // ================= 广播 =================

    @Transactional
    public Map<String, Object> broadcast(Long teacherId, Long lessonId, String content) {
        requireActive(lessons.requireTeacher(teacherId, lessonId));
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("消息不能为空");
        Long id = jdbc.queryForObject("INSERT INTO broadcast_message(lesson_id, sender_id, content) VALUES (:l, :t, :c) RETURNING id",
                p("l", lessonId, "t", teacherId, "c", content.trim()), Long.class);
        Map<String, Object> m = jdbc.queryForMap("SELECT id, content, created_at AS \"createdAt\" FROM broadcast_message WHERE id = :id", p("id", id));
        events.toClass(lessonId, "BROADCAST", m);
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> broadcasts(Long lessonId) {
        return jdbc.queryForList("SELECT id, content, created_at AS \"createdAt\" FROM broadcast_message WHERE lesson_id = :l ORDER BY id DESC",
                p("l", lessonId));
    }

    // ================= 小组讨论 =================

    /** 讨论内容：学生只看自己小组；教师看全部（groupNo 为空时）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> discussPosts(Long lessonId, Long pageId, Integer groupNo) {
        return jdbc.queryForList("SELECT d.id, d.group_no AS \"groupNo\", d.author_id AS \"authorId\", u.name AS \"authorName\", u.role, "
                        + "d.content, d.is_conclusion AS \"conclusion\", d.created_at AS \"createdAt\" FROM discuss_post d JOIN users u ON u.id = d.author_id "
                        + "WHERE d.lesson_id = :l AND d.page_id = :p " + (groupNo == null ? "" : "AND d.group_no = :g ") + "ORDER BY d.id",
                p("l", lessonId, "p", pageId, "g", groupNo));
    }

    @Transactional
    public Map<String, Object> discussPost(Long userId, String role, Long lessonId, Long pageId, String content, boolean conclusion, Integer teacherGroup) {
        Map<String, Object> lesson = "TEACHER".equals(role) ? lessons.requireTeacher(userId, lessonId) : lessons.requireStudent(userId, lessonId);
        requireActive(lesson);
        Map<String, Object> page = lessons.requirePageOfLesson(lessonId, pageId);
        if (!"DISCUSS".equals(page.get("pageType"))) throw new IllegalArgumentException("该页面不是讨论页");
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("内容不能为空");
        int group = "TEACHER".equals(role) ? (teacherGroup == null ? 0 : teacherGroup) : lessons.groupNoOf(lessonId, userId);
        if (conclusion) {
            // 每组仅保留最新一条结论
            jdbc.update("UPDATE discuss_post SET is_conclusion = FALSE WHERE lesson_id = :l AND page_id = :p AND group_no = :g AND is_conclusion",
                    p("l", lessonId, "p", pageId, "g", group));
        }
        Long id = jdbc.queryForObject("INSERT INTO discuss_post(lesson_id, page_id, group_no, author_id, content, is_conclusion) "
                        + "VALUES (:l, :p, :g, :a, :c, :co) RETURNING id",
                p("l", lessonId, "p", pageId, "g", group, "a", userId, "c", settings.filterSensitive(content.trim()), "co", conclusion), Long.class);
        Map<String, Object> post = jdbc.queryForMap("SELECT d.id, d.page_id AS \"pageId\", d.group_no AS \"groupNo\", u.name AS \"authorName\", u.role, "
                + "d.content, d.is_conclusion AS \"conclusion\", d.created_at AS \"createdAt\" FROM discuss_post d JOIN users u ON u.id = d.author_id "
                + "WHERE d.id = :id", p("id", id));
        events.toTeacher(lessonId, "DISCUSS", post);
        events.toClass(lessonId, "DISCUSS", post);
        if (!"TEACHER".equals(role)) log(lessonId, userId, conclusion ? "CONCLUSION" : "DISCUSS", null);
        return post;
    }

    /** 各组结论汇总（原 "第N小组结论"）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> conclusions(Long lessonId, Long pageId) {
        return jdbc.queryForList("SELECT d.group_no AS \"groupNo\", d.content, u.name AS \"authorName\", d.created_at AS \"createdAt\" "
                + "FROM discuss_post d JOIN users u ON u.id = d.author_id WHERE d.lesson_id = :l AND d.page_id = :p AND d.is_conclusion "
                + "ORDER BY d.group_no", p("l", lessonId, "p", pageId));
    }

    // ================= 抢答 =================

    @Transactional
    public Map<String, Object> startAnswerFirst(Long teacherId, Long lessonId, Long pageId, String question) {
        requireActive(lessons.requireTeacher(teacherId, lessonId));
        if (question == null || question.trim().isEmpty()) throw new IllegalArgumentException("抢答题目不能为空");
        jdbc.update("UPDATE answer_first_round SET status = 'CLOSED' WHERE lesson_id = :l AND status = 'OPEN'", p("l", lessonId));
        Long id = jdbc.queryForObject("INSERT INTO answer_first_round(lesson_id, page_id, question) VALUES (:l, :p, :q) RETURNING id",
                p("l", lessonId, "p", pageId, "q", question.trim()), Long.class);
        Map<String, Object> r = round(id);
        events.toClass(lessonId, "ANSWER_FIRST_START", r);
        return r;
    }

    @Transactional
    public Map<String, Object> closeAnswerFirst(Long teacherId, Long roundId) {
        Map<String, Object> r = round(roundId);
        Long lessonId = ((Number) r.get("lessonId")).longValue();
        lessons.requireTeacher(teacherId, lessonId);
        jdbc.update("UPDATE answer_first_round SET status = 'CLOSED' WHERE id = :id", p("id", roundId));
        r = round(roundId);
        events.toClass(lessonId, "ANSWER_FIRST_END", r);
        return r;
    }

    /** 学生抢答：按提交先后排名（行锁保证名次唯一）。 */
    @Transactional
    public Map<String, Object> answerFirst(Long studentId, Long roundId, String answer) {
        Map<String, Object> r = jdbc.queryForMap("SELECT id, lesson_id, status FROM answer_first_round WHERE id = :id FOR UPDATE", p("id", roundId));
        Long lessonId = ((Number) r.get("lesson_id")).longValue();
        lessons.requireStudent(studentId, lessonId);
        if (!"OPEN".equals(r.get("status"))) throw new IllegalArgumentException("抢答已结束");
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM answer_first_entry WHERE round_id = :r AND student_id = :s",
                p("r", roundId, "s", studentId), Long.class);
        if (exists != null && exists > 0) throw new IllegalArgumentException("你已经抢答过了");
        Integer rank = jdbc.queryForObject("SELECT COUNT(*) + 1 FROM answer_first_entry WHERE round_id = :r", p("r", roundId), Integer.class);
        jdbc.update("INSERT INTO answer_first_entry(round_id, student_id, answer, rank_no) VALUES (:r, :s, :a, :k)",
                p("r", roundId, "s", studentId, "a", settings.filterSensitive(answer), "k", rank));
        Map<String, Object> out = round(roundId);
        events.toTeacher(lessonId, "ANSWER_FIRST_ENTRY", out);
        log(lessonId, studentId, "ANSWER_FIRST", "第" + rank + "名");
        return out;
    }

    @Transactional
    public Map<String, Object> judgeAnswerFirst(Long teacherId, Long roundId, Long studentId, Boolean correct, Integer score) {
        Map<String, Object> r = round(roundId);
        Long lessonId = ((Number) r.get("lessonId")).longValue();
        lessons.requireTeacher(teacherId, lessonId);
        jdbc.update("UPDATE answer_first_entry SET correct = :c, score = :s WHERE round_id = :r AND student_id = :st",
                p("c", correct, "s", score, "r", roundId, "st", studentId));
        r = round(roundId);
        events.toClass(lessonId, "ANSWER_FIRST_JUDGED", r);
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> round(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT id, lesson_id AS \"lessonId\", page_id AS \"pageId\", question, status, "
                + "created_at AS \"createdAt\" FROM answer_first_round WHERE id = :id", p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("抢答不存在");
        Map<String, Object> r = l.get(0);
        r.put("entries", jdbc.queryForList("SELECT e.student_id AS \"studentId\", u.name AS \"studentName\", e.answer, e.rank_no AS \"rank\", "
                + "e.correct, e.score, e.created_at AS \"createdAt\" FROM answer_first_entry e JOIN users u ON u.id = e.student_id "
                + "WHERE e.round_id = :r ORDER BY e.rank_no", p("r", id)));
        return r;
    }

    /** 当前课堂最近一轮抢答（可按页面过滤）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> latestRound(Long lessonId, Long pageId) {
        List<Long> l = jdbc.queryForList("SELECT id FROM answer_first_round WHERE lesson_id = :l "
                + (pageId == null ? "" : "AND page_id = :p ") + "ORDER BY id DESC LIMIT 1", p("l", lessonId, "p", pageId), Long.class);
        return l.isEmpty() ? null : round(l.get(0));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rounds(Long lessonId, Long pageId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : jdbc.queryForList("SELECT id FROM answer_first_round WHERE lesson_id = :l AND page_id = :p ORDER BY id DESC",
                p("l", lessonId, "p", pageId), Long.class)) {
            out.add(round(id));
        }
        return out;
    }

    // ================= 在线监控 / 行为日志 =================

    public void heartbeat(Long studentId, Long lessonId, Long pageId, String pageTitle) {
        lessons.requireStudent(studentId, lessonId);
        Map<String, Object> st = new HashMap<>();
        st.put("pageId", pageId);
        st.put("pageTitle", pageTitle);
        st.put("at", Instant.now().toEpochMilli());
        presence.computeIfAbsent(lessonId, k -> new ConcurrentHashMap<>()).put(studentId, st);
    }

    /** 全班学生在线状态：最近 60 秒有心跳为在线。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> monitor(Long lessonId) {
        Map<Long, Map<String, Object>> m = presence.getOrDefault(lessonId, new HashMap<>());
        long now = Instant.now().toEpochMilli();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : lessons.attendance(lessonId)) {
            Map<String, Object> row = new HashMap<>(a);
            Map<String, Object> st = m.get(((Number) a.get("studentId")).longValue());
            boolean online = st != null && now - ((Number) st.get("at")).longValue() < 60_000;
            row.put("online", online);
            row.put("currentPage", st == null ? null : st.get("pageTitle"));
            row.put("lastSeen", st == null ? null : st.get("at"));
            out.add(row);
        }
        return out;
    }

    public void log(Long lessonId, Long studentId, String action, String detail) {
        jdbc.update("INSERT INTO student_activity(lesson_id, student_id, action, detail) VALUES (:l, :s, :a, :d)",
                p("l", lessonId, "s", studentId, "a", action, "d", detail == null ? null : (detail.length() > 250 ? detail.substring(0, 250) : detail)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> activity(Long lessonId) {
        return jdbc.queryForList("SELECT a.id, u.name AS \"studentName\", a.action, a.detail, a.created_at AS \"createdAt\" "
                + "FROM student_activity a JOIN users u ON u.id = a.student_id WHERE a.lesson_id = :l ORDER BY a.id DESC LIMIT 200", p("l", lessonId));
    }

    /** 我的小组成员（原 courseingroup）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> myGroup(Long studentId, Long lessonId) {
        lessons.requireStudent(studentId, lessonId);
        int g = lessons.groupNoOf(lessonId, studentId);
        Map<String, Object> out = new HashMap<>();
        out.put("groupNo", g);
        out.put("members", g == 0 ? new ArrayList<>() : jdbc.queryForList(
                "SELECT u.name, a.pc_name AS \"pcName\" FROM lesson_attendance a JOIN users u ON u.id = a.student_id "
                        + "WHERE a.lesson_id = :l AND a.group_no = :g ORDER BY u.student_no", p("l", lessonId, "g", g)));
        return out;
    }
}
