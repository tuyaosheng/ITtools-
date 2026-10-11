package com.ittools.interact.service;

import com.ittools.classroom.service.LessonService;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 分组与小组活动（原 courseingroup / coursegroupmanage / courseloadgroup / coursegroupactive / courseviewgroupfile）。
 * 学生所在组以 lesson_attendance.group_no 为准（0 = 未分组）；尚未进课的学生分组记在 lesson_group_preset，进课时生效。
 */
@Service
public class GroupService {
    private static final int MAX_GROUPS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final LessonEvents events;
    private final FileStorageService storage;

    public GroupService(NamedParameterJdbcTemplate jdbc, LessonService lessons, LessonEvents events, FileStorageService storage) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.events = events;
        this.storage = storage;
    }

    // ================= 分组概览 =================

    private Map<String, Object> settings(Long lessonId) {
        return jdbc.queryForMap("SELECT group_count AS \"groupCount\", group_join_open AS \"joinOpen\", leader_vote_open AS \"voteOpen\", "
                + "class_id AS \"classId\", status FROM lesson WHERE id = :l", p("l", lessonId));
    }

    /**
     * 全班分组情况。includeAbsent=true（教师）时未进课学生也列出（预分组显示在组内并标记 joined=false）。
     */
    private Map<String, Object> overview(Long lessonId, boolean includeAbsent) {
        Map<String, Object> s = settings(lessonId);
        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT u.id AS \"studentId\", u.name, u.student_no AS \"studentNo\", (a.id IS NOT NULL) AS joined, a.status, "
                        + "a.pc_name AS \"pcName\", a.group_task AS task, COALESCE(a.group_no, pr.group_no, 0) AS \"groupNo\", "
                        + "(SELECT COUNT(*) FROM lesson_group_vote v WHERE v.lesson_id = l.id AND v.candidate_id = u.id) AS votes "
                        + "FROM lesson l JOIN users u ON u.class_id = l.class_id AND u.role = 'STUDENT' AND NOT u.graduated "
                        + "LEFT JOIN lesson_attendance a ON a.lesson_id = l.id AND a.student_id = u.id "
                        + "LEFT JOIN lesson_group_preset pr ON pr.lesson_id = l.id AND pr.student_id = u.id "
                        + "WHERE l.id = :l ORDER BY u.student_no NULLS LAST, u.name", p("l", lessonId));
        Map<Integer, Map<String, Object>> meta = new HashMap<>();
        for (Map<String, Object> g : jdbc.queryForList("SELECT g.group_no AS \"groupNo\", g.leader_id AS \"leaderId\", g.topic, "
                + "g.result_text AS \"resultText\", g.result_file_id AS \"resultFileId\", f.file_name AS \"resultFileName\" "
                + "FROM lesson_group g LEFT JOIN lesson_group_file f ON f.id = g.result_file_id WHERE g.lesson_id = :l", p("l", lessonId))) {
            meta.put(((Number) g.get("groupNo")).intValue(), g);
        }
        int count = ((Number) s.get("groupCount")).intValue();
        for (Map<String, Object> st : students) count = Math.max(count, ((Number) st.get("groupNo")).intValue());
        List<Map<String, Object>> groups = new ArrayList<>();
        for (int g = 1; g <= count; g++) {
            Map<String, Object> m = new LinkedHashMap<>();
            Map<String, Object> mt = meta.get(g);
            m.put("groupNo", g);
            m.put("leaderId", mt == null ? null : mt.get("leaderId"));
            m.put("topic", mt == null ? null : mt.get("topic"));
            m.put("resultText", mt == null ? null : mt.get("resultText"));
            m.put("resultFileId", mt == null ? null : mt.get("resultFileId"));
            m.put("resultFileName", mt == null ? null : mt.get("resultFileName"));
            m.put("members", new ArrayList<Map<String, Object>>());
            groups.add(m);
        }
        List<Map<String, Object>> ungrouped = new ArrayList<>();
        for (Map<String, Object> st : students) {
            boolean joined = Boolean.TRUE.equals(st.get("joined"));
            if (!joined && !includeAbsent) continue;
            int g = ((Number) st.get("groupNo")).intValue();
            if (g == 0) ungrouped.add(st);
            else members(groups.get(g - 1)).add(st);
        }
        for (Map<String, Object> g : groups) {
            Object leader = g.get("leaderId");
            String leaderName = null;
            for (Map<String, Object> m : members(g)) {
                if (leader != null && ((Number) leader).longValue() == ((Number) m.get("studentId")).longValue()) leaderName = (String) m.get("name");
            }
            g.put("leaderName", leaderName);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupCount", s.get("groupCount"));
        out.put("joinOpen", s.get("joinOpen"));
        out.put("voteOpen", s.get("voteOpen"));
        out.put("groups", groups);
        out.put("ungrouped", ungrouped);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> members(Map<String, Object> group) {
        return (List<Map<String, Object>>) group.get("members");
    }

    private int groupOf(Long lessonId, Long studentId) {
        return lessons.groupNoOf(lessonId, studentId);
    }

    private Long leaderOf(Long lessonId, int groupNo) {
        List<Long> l = jdbc.queryForList("SELECT leader_id FROM lesson_group WHERE lesson_id = :l AND group_no = :g AND leader_id IS NOT NULL",
                p("l", lessonId, "g", groupNo), Long.class);
        return l.isEmpty() ? null : l.get(0);
    }

    private void ensureGroup(Long lessonId, int groupNo) {
        jdbc.update("INSERT INTO lesson_group(lesson_id, group_no) VALUES (:l, :g) ON CONFLICT DO NOTHING", p("l", lessonId, "g", groupNo));
    }

    private void changed(Long lessonId) {
        events.toClass(lessonId, "GROUPS_CHANGED", null);
        events.toTeacher(lessonId, "GROUPS_CHANGED", null);
    }

    /** 学生离开原组时：撤销其投票、清掉其得票、如是组长则卸任。 */
    private void leaveGroup(Long lessonId, Long studentId, int oldGroup) {
        jdbc.update("DELETE FROM lesson_group_vote WHERE lesson_id = :l AND (voter_id = :s OR candidate_id = :s)", p("l", lessonId, "s", studentId));
        if (oldGroup > 0) {
            jdbc.update("UPDATE lesson_group SET leader_id = NULL WHERE lesson_id = :l AND group_no = :g AND leader_id = :s",
                    p("l", lessonId, "g", oldGroup, "s", studentId));
        }
        jdbc.update("UPDATE lesson_attendance SET group_task = NULL WHERE lesson_id = :l AND student_id = :s", p("l", lessonId, "s", studentId));
    }

    // ================= 学生：自由分组 / 组长推选 =================

    @Transactional(readOnly = true)
    public Map<String, Object> studentOverview(Long studentId, Long lessonId) {
        lessons.requireStudent(studentId, lessonId);
        Map<String, Object> o = overview(lessonId, false);
        int g = groupOf(lessonId, studentId);
        Map<String, Object> me = new HashMap<>();
        me.put("studentId", studentId);
        me.put("groupNo", g);
        me.put("joined", !jdbc.queryForList("SELECT 1 FROM lesson_attendance WHERE lesson_id = :l AND student_id = :s",
                p("l", lessonId, "s", studentId)).isEmpty());
        me.put("isLeader", g > 0 && studentId.equals(leaderOf(lessonId, g)));
        List<Long> v = jdbc.queryForList("SELECT candidate_id FROM lesson_group_vote WHERE lesson_id = :l AND voter_id = :s",
                p("l", lessonId, "s", studentId), Long.class);
        me.put("myVote", v.isEmpty() ? null : v.get(0));
        o.put("me", me);
        return o;
    }

    @Transactional
    public Map<String, Object> studentJoin(Long studentId, Long lessonId, int groupNo) {
        Map<String, Object> l = lessons.requireStudent(studentId, lessonId);
        if (!"ACTIVE".equals(l.get("status"))) throw new IllegalArgumentException("课堂已结束");
        Map<String, Object> s = settings(lessonId);
        if (!Boolean.TRUE.equals(s.get("joinOpen"))) throw new IllegalArgumentException("老师还没有开放自由分组");
        int count = ((Number) s.get("groupCount")).intValue();
        if (groupNo < 0 || groupNo > count) throw new IllegalArgumentException("没有第 " + groupNo + " 组");
        int old = groupOf(lessonId, studentId);
        if (jdbc.update("UPDATE lesson_attendance SET group_no = :g WHERE lesson_id = :l AND student_id = :s",
                p("g", groupNo, "l", lessonId, "s", studentId)) == 0) {
            throw new IllegalArgumentException("请先进入课堂签到");
        }
        if (old != groupNo) leaveGroup(lessonId, studentId, old);
        changed(lessonId);
        return studentOverview(studentId, lessonId);
    }

    @Transactional
    public Map<String, Object> studentVote(Long studentId, Long lessonId, Long candidateId) {
        lessons.requireStudent(studentId, lessonId);
        if (!Boolean.TRUE.equals(settings(lessonId).get("voteOpen"))) throw new IllegalArgumentException("老师还没有开放组长推选");
        int g = groupOf(lessonId, studentId);
        if (g == 0) throw new IllegalArgumentException("你还没有加入小组");
        if (candidateId == null) {
            jdbc.update("DELETE FROM lesson_group_vote WHERE lesson_id = :l AND voter_id = :s", p("l", lessonId, "s", studentId));
        } else {
            if (candidateId.equals(studentId)) throw new IllegalArgumentException("不能推选自己");
            if (groupOf(lessonId, candidateId) != g) throw new IllegalArgumentException("只能推选本组同学");
            jdbc.update("INSERT INTO lesson_group_vote(lesson_id, voter_id, candidate_id) VALUES (:l, :s, :c) "
                    + "ON CONFLICT (lesson_id, voter_id) DO UPDATE SET candidate_id = EXCLUDED.candidate_id, created_at = now()",
                    p("l", lessonId, "s", studentId, "c", candidateId));
        }
        events.toTeacher(lessonId, "GROUPS_CHANGED", null);
        return studentOverview(studentId, lessonId);
    }

    // ================= 教师：分组管理 =================

    @Transactional(readOnly = true)
    public Map<String, Object> teacherOverview(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        return overview(lessonId, true);
    }

    @Transactional
    public Map<String, Object> updateSettings(Long teacherId, Long lessonId, Integer groupCount, Boolean joinOpen, Boolean voteOpen) {
        lessons.requireTeacher(teacherId, lessonId);
        if (groupCount != null) {
            if (groupCount < 0 || groupCount > MAX_GROUPS) throw new IllegalArgumentException("组数须在 0~" + MAX_GROUPS + " 之间");
            Integer used = jdbc.queryForObject("SELECT COALESCE(MAX(group_no), 0) FROM lesson_attendance WHERE lesson_id = :l", p("l", lessonId), Integer.class);
            if (used != null && groupCount < used) throw new IllegalArgumentException("第 " + used + " 组还有学生，请先调整后再减少组数");
        }
        jdbc.update("UPDATE lesson SET group_count = COALESCE(:c, group_count), group_join_open = COALESCE(:j, group_join_open), "
                + "leader_vote_open = COALESCE(:v, leader_vote_open) WHERE id = :l", p("c", groupCount, "j", joinOpen, "v", voteOpen, "l", lessonId));
        changed(lessonId);
        return overview(lessonId, true);
    }

    /** 教师把学生调到某组（0 = 移出小组）；未进课学生记为预分组。 */
    @Transactional
    public Map<String, Object> assign(Long teacherId, Long lessonId, Long studentId, int groupNo) {
        Map<String, Object> l = lessons.requireTeacher(teacherId, lessonId);
        if (groupNo < 0 || groupNo > MAX_GROUPS) throw new IllegalArgumentException("组号无效");
        Long inClass = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = :s AND class_id = :k AND role = 'STUDENT'",
                p("s", studentId, "k", l.get("classId")), Long.class);
        if (inClass == null || inClass == 0) throw new IllegalArgumentException("学生不在该班级");
        int old = groupOf(lessonId, studentId);
        if (jdbc.update("UPDATE lesson_attendance SET group_no = :g WHERE lesson_id = :l AND student_id = :s",
                p("g", groupNo, "l", lessonId, "s", studentId)) == 0) {
            jdbc.update("INSERT INTO lesson_group_preset(lesson_id, student_id, group_no) VALUES (:l, :s, :g) "
                    + "ON CONFLICT (lesson_id, student_id) DO UPDATE SET group_no = EXCLUDED.group_no", p("l", lessonId, "s", studentId, "g", groupNo));
        }
        if (old != groupNo) leaveGroup(lessonId, studentId, old);
        if (groupNo > ((Number) settings(lessonId).get("groupCount")).intValue()) {
            jdbc.update("UPDATE lesson SET group_count = :g WHERE id = :l", p("g", groupNo, "l", lessonId));
        }
        changed(lessonId);
        return overview(lessonId, true);
    }

    @Transactional
    public Map<String, Object> setLeader(Long teacherId, Long lessonId, int groupNo, Long studentId) {
        lessons.requireTeacher(teacherId, lessonId);
        if (studentId != null) {
            List<Integer> g = jdbc.queryForList("SELECT COALESCE(a.group_no, pr.group_no, 0) FROM users u "
                    + "LEFT JOIN lesson_attendance a ON a.lesson_id = :l AND a.student_id = u.id "
                    + "LEFT JOIN lesson_group_preset pr ON pr.lesson_id = :l AND pr.student_id = u.id WHERE u.id = :s",
                    p("l", lessonId, "s", studentId), Integer.class);
            if (g.isEmpty() || g.get(0) != groupNo) throw new IllegalArgumentException("组长必须是本组成员");
        }
        ensureGroup(lessonId, groupNo);
        jdbc.update("UPDATE lesson_group SET leader_id = :s, updated_at = now() WHERE lesson_id = :l AND group_no = :g",
                p("s", studentId, "l", lessonId, "g", groupNo));
        changed(lessonId);
        return overview(lessonId, true);
    }

    /** 按推选票数自动确定各组组长（原"学生推选票数最高的设为组长"）；平票取学号靠前者，无人得票的组不变。 */
    @Transactional
    public Map<String, Object> autoLeaders(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        List<Map<String, Object>> top = jdbc.queryForList(
                "SELECT DISTINCT ON (a.group_no) a.group_no AS g, v.candidate_id AS s, COUNT(*) AS n "
                        + "FROM lesson_group_vote v JOIN lesson_attendance a ON a.lesson_id = v.lesson_id AND a.student_id = v.candidate_id "
                        + "JOIN users u ON u.id = v.candidate_id WHERE v.lesson_id = :l AND a.group_no > 0 "
                        + "GROUP BY a.group_no, v.candidate_id, u.student_no ORDER BY a.group_no, COUNT(*) DESC, u.student_no", p("l", lessonId));
        for (Map<String, Object> t : top) {
            int g = ((Number) t.get("g")).intValue();
            ensureGroup(lessonId, g);
            jdbc.update("UPDATE lesson_group SET leader_id = :s, updated_at = now() WHERE lesson_id = :l AND group_no = :g",
                    p("s", t.get("s"), "l", lessonId, "g", g));
        }
        changed(lessonId);
        return overview(lessonId, true);
    }

    /** 已进课学生随机均分到 n 个组。 */
    @Transactional
    public Map<String, Object> randomize(Long teacherId, Long lessonId, int groupCount) {
        lessons.requireTeacher(teacherId, lessonId);
        if (groupCount < 1 || groupCount > MAX_GROUPS) throw new IllegalArgumentException("组数须在 1~" + MAX_GROUPS + " 之间");
        List<Long> present = jdbc.queryForList("SELECT student_id FROM lesson_attendance WHERE lesson_id = :l AND status = 'PRESENT'",
                p("l", lessonId), Long.class);
        Collections.shuffle(present);
        resetGroups(lessonId);
        for (int i = 0; i < present.size(); i++) {
            jdbc.update("UPDATE lesson_attendance SET group_no = :g WHERE lesson_id = :l AND student_id = :s",
                    p("g", i % groupCount + 1, "l", lessonId, "s", present.get(i)));
        }
        jdbc.update("UPDATE lesson SET group_count = :c WHERE id = :l", p("c", groupCount, "l", lessonId));
        changed(lessonId);
        return overview(lessonId, true);
    }

    @Transactional
    public Map<String, Object> clear(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        resetGroups(lessonId);
        changed(lessonId);
        return overview(lessonId, true);
    }

    private void resetGroups(Long lessonId) {
        jdbc.update("UPDATE lesson_attendance SET group_no = 0, group_task = NULL WHERE lesson_id = :l", p("l", lessonId));
        jdbc.update("DELETE FROM lesson_group_preset WHERE lesson_id = :l", p("l", lessonId));
        jdbc.update("DELETE FROM lesson_group_vote WHERE lesson_id = :l", p("l", lessonId));
        jdbc.update("UPDATE lesson_group SET leader_id = NULL WHERE lesson_id = :l", p("l", lessonId));
    }

    /** 本班以往有分组记录的课堂（原 courseloadgroup 历史课程列表）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(Long teacherId, Long lessonId) {
        Map<String, Object> l = lessons.requireTeacher(teacherId, lessonId);
        return jdbc.queryForList("SELECT l.id, c.name AS \"courseName\", l.started_at AS \"startedAt\", "
                + "(SELECT COUNT(DISTINCT a.group_no) FROM lesson_attendance a WHERE a.lesson_id = l.id AND a.group_no > 0) AS \"groupCount\" "
                + "FROM lesson l JOIN course c ON c.id = l.course_id WHERE l.class_id = :k AND l.id <> :id "
                + "AND EXISTS (SELECT 1 FROM lesson_attendance a WHERE a.lesson_id = l.id AND a.group_no > 0) "
                + "ORDER BY l.started_at DESC LIMIT 30", p("k", l.get("classId"), "id", lessonId));
    }

    /** 加载历史分组：组号与组长照搬；已进课学生直接生效，未进课学生记为预分组。 */
    @Transactional
    public Map<String, Object> loadHistory(Long teacherId, Long lessonId, Long fromLessonId) {
        Map<String, Object> l = lessons.requireTeacher(teacherId, lessonId);
        Map<String, Object> from = lessons.get(fromLessonId);
        if (!l.get("classId").equals(from.get("classId"))) throw new IllegalArgumentException("只能加载本班的历史分组");
        resetGroups(lessonId);
        int max = 0;
        for (Map<String, Object> a : jdbc.queryForList("SELECT student_id, group_no FROM lesson_attendance WHERE lesson_id = :f AND group_no > 0",
                p("f", fromLessonId))) {
            int g = ((Number) a.get("group_no")).intValue();
            max = Math.max(max, g);
            if (jdbc.update("UPDATE lesson_attendance SET group_no = :g WHERE lesson_id = :l AND student_id = :s",
                    p("g", g, "l", lessonId, "s", a.get("student_id"))) == 0) {
                jdbc.update("INSERT INTO lesson_group_preset(lesson_id, student_id, group_no) VALUES (:l, :s, :g)",
                        p("l", lessonId, "s", a.get("student_id"), "g", g));
            }
        }
        for (Map<String, Object> g : jdbc.queryForList("SELECT group_no, leader_id FROM lesson_group WHERE lesson_id = :f AND leader_id IS NOT NULL",
                p("f", fromLessonId))) {
            int no = ((Number) g.get("group_no")).intValue();
            ensureGroup(lessonId, no);
            jdbc.update("UPDATE lesson_group SET leader_id = :s WHERE lesson_id = :l AND group_no = :g", p("s", g.get("leader_id"), "l", lessonId, "g", no));
        }
        jdbc.update("UPDATE lesson SET group_count = GREATEST(group_count, :c) WHERE id = :l", p("c", max, "l", lessonId));
        changed(lessonId);
        return overview(lessonId, true);
    }

    // ================= 小组合作 =================

    private int requireMyGroup(Long studentId, Long lessonId) {
        lessons.requireStudent(studentId, lessonId);
        int g = groupOf(lessonId, studentId);
        if (g == 0) throw new IllegalArgumentException("你还没有加入小组");
        return g;
    }

    private void requireLeader(Long studentId, Long lessonId, int g) {
        if (!studentId.equals(leaderOf(lessonId, g))) throw new IllegalArgumentException("只有组长可以操作");
    }

    private List<Map<String, Object>> files(Long lessonId, int groupNo) {
        return jdbc.queryForList("SELECT f.id, f.title, f.intro, f.file_name AS \"fileName\", f.file_size AS \"fileSize\", "
                + "f.uploader_id AS \"uploaderId\", u.name AS \"uploaderName\", f.created_at AS \"createdAt\" "
                + "FROM lesson_group_file f JOIN users u ON u.id = f.uploader_id WHERE f.lesson_id = :l AND f.group_no = :g ORDER BY f.id DESC",
                p("l", lessonId, "g", groupNo));
    }

    /** 组员互评汇总：每位组员收到的平均星级与评价数。 */
    private Map<Long, Map<String, Object>> peerSummary(Long lessonId) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT target_id, ROUND(AVG(score), 1) AS avg, COUNT(*) AS n FROM lesson_group_peer "
                + "WHERE lesson_id = :l GROUP BY target_id", p("l", lessonId))) {
            out.put(((Number) r.get("target_id")).longValue(), r);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myWork(Long studentId, Long lessonId) {
        lessons.requireStudent(studentId, lessonId);
        int g = groupOf(lessonId, studentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupNo", g);
        if (g == 0) return out;
        Map<String, Object> group = null;
        for (Map<String, Object> x : groupsOf(overview(lessonId, false))) {
            if (((Number) x.get("groupNo")).intValue() == g) group = x;
        }
        out.put("group", group);
        out.put("isLeader", studentId.equals(leaderOf(lessonId, g)));
        out.put("files", files(lessonId, g));
        Map<Long, Integer> mine = new HashMap<>();
        Map<Long, String> myComments = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT target_id, score, comment FROM lesson_group_peer WHERE lesson_id = :l AND evaluator_id = :s",
                p("l", lessonId, "s", studentId))) {
            mine.put(((Number) r.get("target_id")).longValue(), ((Number) r.get("score")).intValue());
            myComments.put(((Number) r.get("target_id")).longValue(), (String) r.get("comment"));
        }
        List<Map<String, Object>> evals = new ArrayList<>();
        for (Map<String, Object> m : members(group)) {
            Long sid = ((Number) m.get("studentId")).longValue();
            if (sid.equals(studentId)) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("studentId", sid);
            e.put("name", m.get("name"));
            e.put("score", mine.get(sid));
            e.put("comment", myComments.get(sid));
            evals.add(e);
        }
        out.put("myEvaluations", evals);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groupsOf(Map<String, Object> overview) {
        return (List<Map<String, Object>>) overview.get("groups");
    }

    @Transactional
    public Map<String, Object> setTopic(Long studentId, Long lessonId, String topic) {
        int g = requireMyGroup(studentId, lessonId);
        requireLeader(studentId, lessonId, g);
        if (topic != null && topic.length() > 200) throw new IllegalArgumentException("主题不超过 200 字");
        ensureGroup(lessonId, g);
        jdbc.update("UPDATE lesson_group SET topic = :t, updated_at = now() WHERE lesson_id = :l AND group_no = :g", p("t", topic, "l", lessonId, "g", g));
        changed(lessonId);
        return myWork(studentId, lessonId);
    }

    @Transactional
    public Map<String, Object> setTasks(Long studentId, Long lessonId, List<Map<String, Object>> tasks) {
        int g = requireMyGroup(studentId, lessonId);
        requireLeader(studentId, lessonId, g);
        for (Map<String, Object> t : tasks) {
            Object task = t.get("task");
            String text = task == null ? null : String.valueOf(task).trim();
            if (text != null && text.length() > 200) throw new IllegalArgumentException("分工不超过 200 字");
            jdbc.update("UPDATE lesson_attendance SET group_task = :t WHERE lesson_id = :l AND student_id = :s AND group_no = :g",
                    p("t", text == null || text.isEmpty() ? null : text, "l", lessonId, "s", ((Number) t.get("studentId")).longValue(), "g", g));
        }
        changed(lessonId);
        return myWork(studentId, lessonId);
    }

    @Transactional
    public Map<String, Object> uploadFile(Long studentId, Long lessonId, MultipartFile file, String title, String intro) {
        int g = requireMyGroup(studentId, lessonId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择文件");
        StoredFile sf = storage.store(file, "group");
        jdbc.update("INSERT INTO lesson_group_file(lesson_id, group_no, uploader_id, title, intro, file_path, file_name, file_size) "
                        + "VALUES (:l, :g, :u, :t, :i, :fp, :fn, :sz)",
                p("l", lessonId, "g", g, "u", studentId, "t", title == null || title.trim().isEmpty() ? sf.getOriginalName() : title.trim(),
                        "i", intro, "fp", sf.getRelPath(), "fn", sf.getOriginalName(), "sz", sf.getSize()));
        changed(lessonId);
        return myWork(studentId, lessonId);
    }

    @Transactional
    public Map<String, Object> deleteFile(Long studentId, Long lessonId, Long fileId) {
        int g = requireMyGroup(studentId, lessonId);
        Map<String, Object> f = fileRow(fileId);
        if (!lessonId.equals(((Number) f.get("lesson_id")).longValue()) || ((Number) f.get("group_no")).intValue() != g) {
            throw new IllegalArgumentException("文件不属于你的小组");
        }
        if (!studentId.equals(((Number) f.get("uploader_id")).longValue()) && !studentId.equals(leaderOf(lessonId, g))) {
            throw new IllegalArgumentException("只能删除自己上传的文件");
        }
        jdbc.update("UPDATE lesson_group SET result_file_id = NULL WHERE result_file_id = :f", p("f", fileId));
        jdbc.update("DELETE FROM lesson_group_file WHERE id = :f", p("f", fileId));
        storage.delete((String) f.get("file_path"));
        changed(lessonId);
        return myWork(studentId, lessonId);
    }

    private Map<String, Object> fileRow(Long fileId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT * FROM lesson_group_file WHERE id = :f", p("f", fileId));
        if (l.isEmpty()) throw new IllegalArgumentException("文件不存在");
        return l.get(0);
    }

    @Transactional
    public Map<String, Object> setResult(Long studentId, Long lessonId, String text, Long fileId) {
        int g = requireMyGroup(studentId, lessonId);
        requireLeader(studentId, lessonId, g);
        if (fileId != null) {
            Map<String, Object> f = fileRow(fileId);
            if (!lessonId.equals(((Number) f.get("lesson_id")).longValue()) || ((Number) f.get("group_no")).intValue() != g) {
                throw new IllegalArgumentException("只能选择本组的资源作为成果");
            }
        }
        ensureGroup(lessonId, g);
        jdbc.update("UPDATE lesson_group SET result_text = :t, result_file_id = :f, updated_at = now() WHERE lesson_id = :l AND group_no = :g",
                p("t", text, "f", fileId, "l", lessonId, "g", g));
        changed(lessonId);
        return myWork(studentId, lessonId);
    }

    @Transactional
    public Map<String, Object> evaluatePeer(Long studentId, Long lessonId, Long targetId, int score, String comment) {
        int g = requireMyGroup(studentId, lessonId);
        if (targetId.equals(studentId)) throw new IllegalArgumentException("不能评价自己");
        if (groupOf(lessonId, targetId) != g) throw new IllegalArgumentException("只能评价本组同学");
        if (score < 1 || score > 5) throw new IllegalArgumentException("评分为 1~5 星");
        jdbc.update("INSERT INTO lesson_group_peer(lesson_id, evaluator_id, target_id, score, comment) VALUES (:l, :e, :t, :s, :c) "
                        + "ON CONFLICT (lesson_id, evaluator_id, target_id) DO UPDATE SET score = EXCLUDED.score, comment = EXCLUDED.comment, created_at = now()",
                p("l", lessonId, "e", studentId, "t", targetId, "s", score, "c", comment));
        events.toTeacher(lessonId, "GROUPS_CHANGED", null);
        return myWork(studentId, lessonId);
    }

    /** 下载组内文件：本组成员、本课堂教师；已设为小组成果的文件全班可下载（成果展示）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> fileForDownload(Long userId, String role, Long fileId) {
        Map<String, Object> f = fileRow(fileId);
        Long lessonId = ((Number) f.get("lesson_id")).longValue();
        if ("TEACHER".equals(role)) {
            lessons.requireTeacher(userId, lessonId);
        } else {
            lessons.requireStudent(userId, lessonId);
            boolean sameGroup = groupOf(lessonId, userId) == ((Number) f.get("group_no")).intValue();
            Long asResult = jdbc.queryForObject("SELECT COUNT(*) FROM lesson_group WHERE result_file_id = :f", p("f", fileId), Long.class);
            if (!sameGroup && (asResult == null || asResult == 0)) throw new IllegalArgumentException("无权下载该文件");
        }
        return f;
    }

    // ================= 小组成果展示 =================

    private Map<Integer, Map<String, Object>> reviewSummary(Long lessonId) {
        Map<Integer, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT group_no, ROUND(AVG(score), 1) AS avg, COUNT(*) AS n FROM lesson_group_review "
                + "WHERE lesson_id = :l GROUP BY group_no", p("l", lessonId))) {
            out.put(((Number) r.get("group_no")).intValue(), r);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> showcase(Long studentId, Long lessonId) {
        lessons.requireStudent(studentId, lessonId);
        int mine = groupOf(lessonId, studentId);
        Map<Integer, Map<String, Object>> my = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT group_no, score, comment FROM lesson_group_review WHERE lesson_id = :l AND reviewer_id = :s",
                p("l", lessonId, "s", studentId))) {
            my.put(((Number) r.get("group_no")).intValue(), r);
        }
        Map<Integer, Map<String, Object>> sum = reviewSummary(lessonId);
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map<String, Object> g : groupsOf(overview(lessonId, false))) {
            if (members(g).isEmpty()) continue;
            int no = ((Number) g.get("groupNo")).intValue();
            Map<String, Object> r = my.get(no);
            g.put("myScore", r == null ? null : r.get("score"));
            g.put("myComment", r == null ? null : r.get("comment"));
            g.put("reviewCount", sum.containsKey(no) ? sum.get(no).get("n") : 0);
            g.put("isMine", no == mine);
            groups.add(g);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("myGroupNo", mine);
        out.put("groups", groups);
        return out;
    }

    @Transactional
    public Map<String, Object> review(Long studentId, Long lessonId, int groupNo, int score, String comment) {
        lessons.requireStudent(studentId, lessonId);
        if (groupOf(lessonId, studentId) == groupNo) throw new IllegalArgumentException("不能评价自己的小组");
        if (score < 0 || score > 100) throw new IllegalArgumentException("评分为 0~100 分");
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id = :l AND group_no = :g", p("l", lessonId, "g", groupNo), Long.class);
        if (groupNo < 1 || n == null || n == 0) throw new IllegalArgumentException("该小组不存在");
        jdbc.update("INSERT INTO lesson_group_review(lesson_id, group_no, reviewer_id, score, comment) VALUES (:l, :g, :r, :s, :c) "
                        + "ON CONFLICT (lesson_id, group_no, reviewer_id) DO UPDATE SET score = EXCLUDED.score, comment = EXCLUDED.comment, created_at = now()",
                p("l", lessonId, "g", groupNo, "r", studentId, "s", score, "c", comment));
        events.toTeacher(lessonId, "GROUPS_CHANGED", null);
        return showcase(studentId, lessonId);
    }

    /** 教师查看各组合作情况：主题、分工、资源、成果、组员互评与组间互评。 */
    @Transactional(readOnly = true)
    public Map<String, Object> teacherWork(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        Map<String, Object> o = overview(lessonId, false);
        Map<Long, Map<String, Object>> peers = peerSummary(lessonId);
        Map<Integer, Map<String, Object>> reviews = reviewSummary(lessonId);
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map<String, Object> g : groupsOf(o)) {
            if (members(g).isEmpty()) continue;
            int no = ((Number) g.get("groupNo")).intValue();
            for (Map<String, Object> m : members(g)) {
                Map<String, Object> ps = peers.get(((Number) m.get("studentId")).longValue());
                m.put("peerAvg", ps == null ? null : ps.get("avg"));
                m.put("peerCount", ps == null ? 0 : ps.get("n"));
            }
            g.put("files", files(lessonId, no));
            g.put("reviewAvg", reviews.containsKey(no) ? reviews.get(no).get("avg") : null);
            g.put("reviewCount", reviews.containsKey(no) ? reviews.get(no).get("n") : 0);
            g.put("reviews", jdbc.queryForList("SELECT u.name AS \"reviewerName\", r.score, r.comment FROM lesson_group_review r "
                    + "JOIN users u ON u.id = r.reviewer_id WHERE r.lesson_id = :l AND r.group_no = :g ORDER BY r.created_at", p("l", lessonId, "g", no)));
            groups.add(g);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groups", groups);
        return out;
    }
}
