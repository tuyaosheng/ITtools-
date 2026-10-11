package com.ittools.interact.service;

import com.ittools.classroom.service.LessonService;
import com.ittools.platform.settings.SystemSettingService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/** 课堂论坛（原 coursestubbs / coursetebbs：按课程的主题帖、回复、置顶、推荐、点击数）。 */
@Service
public class ForumService {
    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final SystemSettingService settings;

    public ForumService(NamedParameterJdbcTemplate jdbc, LessonService lessons, SystemSettingService settings) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.settings = settings;
    }

    public void requireAccess(Long userId, String role, Long courseId) {
        if (!lessons.canReadCourse(userId, role, courseId)) throw new IllegalArgumentException("无权访问该课程论坛");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> topics(Long courseId) {
        return jdbc.queryForList("SELECT t.id, t.title, u.name AS \"authorName\", u.role AS \"authorRole\", t.author_id AS \"authorId\", "
                + "t.pinned, t.recommended, t.clicks, t.reply_count AS \"replyCount\", t.last_reply_name AS \"lastReplyName\", "
                + "t.last_reply_at AS \"lastReplyAt\", t.created_at AS \"createdAt\" FROM forum_topic t JOIN users u ON u.id = t.author_id "
                + "WHERE t.course_id = :c ORDER BY t.pinned DESC, COALESCE(t.last_reply_at, t.created_at) DESC", p("c", courseId));
    }

    @Transactional
    public Map<String, Object> topic(Long topicId) {
        jdbc.update("UPDATE forum_topic SET clicks = clicks + 1 WHERE id = :id", p("id", topicId));
        List<Map<String, Object>> l = jdbc.queryForList("SELECT t.id, t.course_id AS \"courseId\", t.title, t.content, u.name AS \"authorName\", "
                + "u.role AS \"authorRole\", t.author_id AS \"authorId\", t.pinned, t.recommended, t.clicks, t.created_at AS \"createdAt\" "
                + "FROM forum_topic t JOIN users u ON u.id = t.author_id WHERE t.id = :id", p("id", topicId));
        if (l.isEmpty()) throw new IllegalArgumentException("帖子不存在");
        Map<String, Object> t = l.get(0);
        t.put("replies", jdbc.queryForList("SELECT r.id, r.content, u.name AS \"authorName\", u.role AS \"authorRole\", r.author_id AS \"authorId\", "
                + "r.created_at AS \"createdAt\" FROM forum_reply r JOIN users u ON u.id = r.author_id WHERE r.topic_id = :id ORDER BY r.id", p("id", topicId)));
        return t;
    }

    public Long courseOfTopic(Long topicId) {
        List<Long> l = jdbc.queryForList("SELECT course_id FROM forum_topic WHERE id = :id", p("id", topicId), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("帖子不存在");
        return l.get(0);
    }

    @Transactional
    public Long create(Long userId, Long courseId, String title, String content) {
        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("标题和内容不能为空");
        }
        return jdbc.queryForObject("INSERT INTO forum_topic(course_id, author_id, title, content) VALUES (:c, :u, :t, :b) RETURNING id",
                p("c", courseId, "u", userId, "t", settings.filterSensitive(title.trim()), "b", settings.filterSensitive(content.trim())), Long.class);
    }

    @Transactional
    public void reply(Long userId, Long topicId, String content) {
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("回复内容不能为空");
        jdbc.update("INSERT INTO forum_reply(topic_id, author_id, content) VALUES (:t, :u, :c)",
                p("t", topicId, "u", userId, "c", settings.filterSensitive(content.trim())));
        jdbc.update("UPDATE forum_topic SET reply_count = reply_count + 1, last_reply_at = now(), "
                + "last_reply_name = (SELECT name FROM users WHERE id = :u) WHERE id = :t", p("t", topicId, "u", userId));
    }

    /** 教师管理：置顶/推荐。 */
    @Transactional
    public void flags(Long topicId, Boolean pinned, Boolean recommended) {
        jdbc.update("UPDATE forum_topic SET pinned = COALESCE(:p, pinned), recommended = COALESCE(:r, recommended) WHERE id = :id",
                p("p", pinned, "r", recommended, "id", topicId));
    }

    /** 删除：作者本人或课程教师。 */
    @Transactional
    public void delete(Long userId, String role, Long topicId) {
        Map<String, Object> t = jdbc.queryForMap("SELECT t.author_id, c.teacher_id FROM forum_topic t JOIN course c ON c.id = t.course_id WHERE t.id = :id",
                p("id", topicId));
        if (!userId.equals(((Number) t.get("author_id")).longValue()) && !("TEACHER".equals(role) && userId.equals(((Number) t.get("teacher_id")).longValue()))) {
            throw new IllegalArgumentException("无权删除");
        }
        jdbc.update("DELETE FROM forum_topic WHERE id = :id", p("id", topicId));
    }

    @Transactional
    public void deleteReply(Long userId, String role, Long replyId) {
        Map<String, Object> r = jdbc.queryForMap("SELECT r.author_id, c.teacher_id FROM forum_reply r JOIN forum_topic t ON t.id = r.topic_id "
                + "JOIN course c ON c.id = t.course_id WHERE r.id = :id", p("id", replyId));
        if (!userId.equals(((Number) r.get("author_id")).longValue()) && !("TEACHER".equals(role) && userId.equals(((Number) r.get("teacher_id")).longValue()))) {
            throw new IllegalArgumentException("无权删除");
        }
        jdbc.update("UPDATE forum_topic SET reply_count = GREATEST(reply_count - 1, 0) WHERE id = (SELECT topic_id FROM forum_reply WHERE id = :id)",
                p("id", replyId));
        jdbc.update("DELETE FROM forum_reply WHERE id = :id", p("id", replyId));
    }

    public boolean isCourseTeacher(Long userId, Long courseId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = :c AND teacher_id = :u", p("c", courseId, "u", userId), Long.class);
        return n != null && n > 0;
    }
}
