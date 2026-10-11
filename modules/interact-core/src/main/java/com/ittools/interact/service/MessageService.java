package com.ittools.interact.service;

import com.ittools.classroom.service.ClassBindingService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ittools.platform.db.Sql.p;

/**
 * 系统消息（原 note.aspx）：教师向学生发送个人消息或班级群体消息；所有用户都有收件箱（含系统自动通知）。
 */
@Service
public class MessageService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ClassBindingService bindings;

    public MessageService(NamedParameterJdbcTemplate jdbc, ClassBindingService bindings) {
        this.jdbc = jdbc;
        this.bindings = bindings;
    }

    // ================= 发送 =================

    /** 教师发送：classIds 整班发送，studentIds 指定个人；两者可同时给出，收件人去重。 */
    @Transactional
    public Map<String, Object> send(Long teacherId, String title, String content, List<Long> classIds, List<Long> studentIds) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("消息标题不能为空");
        if (title.length() > 200) throw new IllegalArgumentException("标题不超过 200 字");
        if (content != null && content.length() > 2000) throw new IllegalArgumentException("内容不超过 2000 字");
        Set<Long> recipients = new LinkedHashSet<>();
        List<String> desc = new ArrayList<>();
        for (Long k : classIds) {
            if (!bindings.teacherCanUseClass(teacherId, k)) throw new IllegalArgumentException("无权向该班级发送消息");
            recipients.addAll(jdbc.queryForList("SELECT id FROM users WHERE class_id = :k AND role = 'STUDENT' AND NOT graduated AND enabled",
                    p("k", k), Long.class));
            desc.add(className(k) + "（全班）");
        }
        if (!studentIds.isEmpty()) {
            List<Map<String, Object>> st = jdbc.queryForList("SELECT id, name, class_id FROM users WHERE id IN (:ids) AND role = 'STUDENT'",
                    p("ids", studentIds));
            if (st.size() != new LinkedHashSet<>(studentIds).size()) throw new IllegalArgumentException("收件人不存在");
            List<String> names = new ArrayList<>();
            for (Map<String, Object> s : st) {
                Object k = s.get("class_id");
                if (k == null || !bindings.teacherCanUseClass(teacherId, ((Number) k).longValue())) {
                    throw new IllegalArgumentException("无权向 " + s.get("name") + " 发送消息");
                }
                recipients.add(((Number) s.get("id")).longValue());
                names.add((String) s.get("name"));
            }
            desc.add(names.size() <= 3 ? String.join("、", names) : String.join("、", names.subList(0, 3)) + " 等 " + names.size() + " 人");
        }
        if (recipients.isEmpty()) throw new IllegalArgumentException("请选择收件人");
        String target = String.join("；", desc);
        Long id = insert(teacherId, title.trim(), content, target.length() > 200 ? target.substring(0, 197) + "..." : target, recipients);
        return sentRow(teacherId, id);
    }

    /** 系统通知（如答疑被回复），不做权限检查，供其他服务调用。 */
    @Transactional
    public void notify(Long senderId, Long userId, String title, String content) {
        Set<Long> r = new LinkedHashSet<>();
        r.add(userId);
        insert(senderId, title.length() > 200 ? title.substring(0, 197) + "..." : title, content, null, r);
    }

    private Long insert(Long senderId, String title, String content, String target, Set<Long> recipients) {
        Long id = jdbc.queryForObject("INSERT INTO sys_message(sender_id, title, content, target_desc) VALUES (:s, :t, :c, :d) RETURNING id",
                p("s", senderId, "t", title, "c", content, "d", target), Long.class);
        for (Long u : recipients) {
            jdbc.update("INSERT INTO sys_message_recipient(message_id, user_id) VALUES (:m, :u)", p("m", id, "u", u));
        }
        return id;
    }

    private String className(Long classId) {
        List<String> l = jdbc.queryForList("SELECT sy.label || k.name FROM klass k JOIN school_year sy ON sy.id = k.school_year_id WHERE k.id = :k",
                p("k", classId), String.class);
        if (l.isEmpty()) throw new IllegalArgumentException("班级不存在");
        return l.get(0);
    }

    private static final String SENT_COLS = "m.id, m.title, m.content, m.target_desc AS \"targetDesc\", m.created_at AS \"createdAt\", "
            + "(SELECT COUNT(*) FROM sys_message_recipient r WHERE r.message_id = m.id) AS \"recipientCount\", "
            + "(SELECT COUNT(*) FROM sys_message_recipient r WHERE r.message_id = m.id AND r.read_at IS NOT NULL) AS \"readCount\" "
            + "FROM sys_message m ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sent(Long senderId) {
        return jdbc.queryForList("SELECT " + SENT_COLS + "WHERE m.sender_id = :s AND m.target_desc IS NOT NULL ORDER BY m.id DESC LIMIT 300",
                p("s", senderId));
    }

    private Map<String, Object> sentRow(Long senderId, Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + SENT_COLS + "WHERE m.id = :id AND m.sender_id = :s", p("id", id, "s", senderId));
        if (l.isEmpty()) throw new IllegalArgumentException("消息不存在");
        return l.get(0);
    }

    /** 已发送消息的阅读情况。 */
    @Transactional(readOnly = true)
    public Map<String, Object> sentDetail(Long senderId, Long id) {
        Map<String, Object> m = sentRow(senderId, id);
        m.put("recipients", jdbc.queryForList("SELECT u.name, sy.label || k.name AS \"className\", r.read_at AS \"readAt\" "
                + "FROM sys_message_recipient r JOIN users u ON u.id = r.user_id LEFT JOIN klass k ON k.id = u.class_id "
                + "LEFT JOIN school_year sy ON sy.id = k.school_year_id WHERE r.message_id = :m "
                + "ORDER BY r.read_at NULLS FIRST, u.student_no", p("m", id)));
        return m;
    }

    /** 撤回：连同所有收件记录删除。 */
    @Transactional
    public void retract(Long senderId, Long id) {
        if (jdbc.update("DELETE FROM sys_message WHERE id = :id AND sender_id = :s", p("id", id, "s", senderId)) == 0) {
            throw new IllegalArgumentException("消息不存在");
        }
    }

    // ================= 收件箱 =================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> inbox(Long userId) {
        return jdbc.queryForList("SELECT m.id, m.title, m.content, COALESCE(u.name, '系统') AS \"senderName\", m.created_at AS \"createdAt\", "
                + "r.read_at AS \"readAt\" FROM sys_message_recipient r JOIN sys_message m ON m.id = r.message_id "
                + "LEFT JOIN users u ON u.id = m.sender_id WHERE r.user_id = :u ORDER BY m.id DESC LIMIT 300", p("u", userId));
    }

    @Transactional
    public void markRead(Long userId, Long messageId) {
        jdbc.update("UPDATE sys_message_recipient SET read_at = now() WHERE user_id = :u AND message_id = :m AND read_at IS NULL",
                p("u", userId, "m", messageId));
    }

    @Transactional
    public int markAllRead(Long userId) {
        return jdbc.update("UPDATE sys_message_recipient SET read_at = now() WHERE user_id = :u AND read_at IS NULL", p("u", userId));
    }

    @Transactional
    public void removeFromInbox(Long userId, Long messageId) {
        jdbc.update("DELETE FROM sys_message_recipient WHERE user_id = :u AND message_id = :m", p("u", userId, "m", messageId));
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sys_message_recipient WHERE user_id = :u AND read_at IS NULL", p("u", userId), Integer.class);
        return n == null ? 0 : n;
    }
}
