package com.ittools.classroom.service;

import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 课程与课程页面（原 it_course / it_courselist；teacher/course.aspx、editcourse*.aspx）。
 */
@Service
public class CourseService {
    public static final List<String> PAGE_TYPES = Arrays.asList(
            "TEXT", "FILE", "VIDEO", "LINK", "HOMEWORK", "QUIZ", "DISCUSS", "ANSWER_FIRST", "PYTHON",
            "GROUPING", "GROUP_WORK", "GROUP_SHOW", "SURVEY", "TAKE_QUESTION", "STORYLINE", "SCRATCH");

    private final NamedParameterJdbcTemplate jdbc;
    private final FileStorageService storage;

    public CourseService(NamedParameterJdbcTemplate jdbc, FileStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    private static final String COURSE_COLS =
            "c.id, c.name, c.category, c.description, c.shared, c.term_id AS \"termId\", "
                    + "t.year_name || ' ' || t.term_name AS \"termLabel\", c.teacher_id AS \"teacherId\", u.name AS \"teacherName\", "
                    + "c.created_at AS \"createdAt\", (SELECT COUNT(*) FROM course_page p WHERE p.course_id = c.id) AS \"pageCount\", "
                    + "(SELECT COUNT(*) FROM lesson l WHERE l.course_id = c.id) AS \"lessonCount\" "
                    + "FROM course c LEFT JOIN teaching_term t ON t.id = c.term_id JOIN users u ON u.id = c.teacher_id ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myCourses(Long teacherId, Long termId) {
        MapSqlParameterSource ps = p("t", teacherId, "term", termId);
        return jdbc.queryForList("SELECT " + COURSE_COLS + "WHERE c.teacher_id = :t "
                + (termId == null ? "" : "AND c.term_id = :term ") + "ORDER BY c.id DESC", ps);
    }

    /** 其他教师共享的课程（原"共享课程"）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sharedCourses(Long teacherId) {
        return jdbc.queryForList("SELECT " + COURSE_COLS + "WHERE c.shared AND c.teacher_id <> :t ORDER BY c.id DESC",
                p("t", teacherId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long courseId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + COURSE_COLS + "WHERE c.id = :id", p("id", courseId));
        if (l.isEmpty()) throw new IllegalArgumentException("课程不存在");
        return l.get(0);
    }

    /** 教师可读（本人或共享）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getReadable(Long teacherId, Long courseId) {
        Map<String, Object> c = get(courseId);
        if (!teacherId.equals(((Number) c.get("teacherId")).longValue()) && !Boolean.TRUE.equals(c.get("shared"))) {
            throw new IllegalArgumentException("无权查看该课程");
        }
        return c;
    }

    public void requireOwner(Long teacherId, Long courseId) {
        List<Long> l = jdbc.queryForList("SELECT teacher_id FROM course WHERE id = :id", p("id", courseId), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("课程不存在");
        if (!l.get(0).equals(teacherId)) throw new IllegalArgumentException("只能修改自己的课程");
    }

    @Transactional
    public Map<String, Object> create(Long teacherId, String name, String category, String description, Long termId, boolean shared) {
        requireName(name);
        Long id = jdbc.queryForObject(
                "INSERT INTO course(teacher_id, term_id, name, category, description, shared) VALUES (:t, :term, :n, :cat, :d, :s) RETURNING id",
                p("t", teacherId, "term", termId, "n", name, "cat", category, "d", description, "s", shared), Long.class);
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(Long teacherId, Long id, String name, String category, String description, Long termId, boolean shared) {
        requireOwner(teacherId, id);
        requireName(name);
        jdbc.update("UPDATE course SET name = :n, category = :cat, description = :d, term_id = :term, shared = :s, updated_at = now() WHERE id = :id",
                p("n", name, "cat", category, "d", description, "term", termId, "s", shared, "id", id));
        return get(id);
    }

    @Transactional
    public void delete(Long teacherId, Long id) {
        requireOwner(teacherId, id);
        Long lessons = jdbc.queryForObject("SELECT COUNT(*) FROM lesson WHERE course_id = :id", p("id", id), Long.class);
        if (lessons != null && lessons > 0) {
            throw new IllegalArgumentException("该课程已有开课记录，不能删除（可取消共享或改名归档）");
        }
        jdbc.update("DELETE FROM course WHERE id = :id", p("id", id));
    }

    /** 复制课程（自己的或共享的）为自己的新课程，连同页面与附件引用。 */
    @Transactional
    public Map<String, Object> copy(Long teacherId, Long sourceId, Long termId) {
        Map<String, Object> src = getReadable(teacherId, sourceId);
        Long id = jdbc.queryForObject(
                "INSERT INTO course(teacher_id, term_id, name, category, description, shared) "
                        + "SELECT :t, :term, name || '（副本）', category, description, FALSE FROM course WHERE id = :src RETURNING id",
                p("t", teacherId, "term", termId != null ? termId : src.get("termId"), "src", sourceId), Long.class);
        jdbc.update("INSERT INTO course_page(course_id, seq, title, page_type, content, link_url, file_path, file_name, student_visible, score, ref_id, config) "
                        + "SELECT :id, seq, title, page_type, content, link_url, file_path, file_name, student_visible, score, "
                        + "CASE WHEN page_type IN ('QUIZ', 'PYTHON') THEN ref_id ELSE NULL END, config FROM course_page WHERE course_id = :src ORDER BY seq",
                p("id", id, "src", sourceId));
        return get(id);
    }

    // ---- 页面 ----

    private static final String PAGE_COLS =
            "id, course_id AS \"courseId\", seq, title, page_type AS \"pageType\", content, link_url AS \"linkUrl\", "
                    + "file_path AS \"filePath\", file_name AS \"fileName\", student_visible AS \"studentVisible\", score, ref_id AS \"refId\" ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pages(Long courseId) {
        return jdbc.queryForList("SELECT " + PAGE_COLS + "FROM course_page WHERE course_id = :c ORDER BY seq, id", p("c", courseId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> page(Long pageId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + PAGE_COLS + "FROM course_page WHERE id = :id", p("id", pageId));
        if (l.isEmpty()) throw new IllegalArgumentException("页面不存在");
        return l.get(0);
    }

    @Transactional
    public Map<String, Object> createPage(Long teacherId, Long courseId, Map<String, Object> f) {
        requireOwner(teacherId, courseId);
        String type = validType((String) f.get("pageType"));
        Integer seq = jdbc.queryForObject("SELECT COALESCE(MAX(seq), 0) + 1 FROM course_page WHERE course_id = :c", p("c", courseId), Integer.class);
        Long id = jdbc.queryForObject(
                "INSERT INTO course_page(course_id, seq, title, page_type, content, link_url, student_visible, score, ref_id) "
                        + "VALUES (:c, :seq, :title, :type, :content, :link, :vis, :score, :ref) RETURNING id",
                pageParams(f).addValue("c", courseId).addValue("seq", seq).addValue("type", type), Long.class);
        return page(id);
    }

    @Transactional
    public Map<String, Object> updatePage(Long teacherId, Long pageId, Map<String, Object> f) {
        Map<String, Object> pg = page(pageId);
        requireOwner(teacherId, ((Number) pg.get("courseId")).longValue());
        String type = validType((String) f.get("pageType"));
        jdbc.update("UPDATE course_page SET title = :title, page_type = :type, content = :content, link_url = :link, "
                        + "student_visible = :vis, score = :score, ref_id = :ref WHERE id = :id",
                pageParams(f).addValue("type", type).addValue("id", pageId));
        return page(pageId);
    }

    @Transactional
    public void deletePage(Long teacherId, Long pageId) {
        Map<String, Object> pg = page(pageId);
        requireOwner(teacherId, ((Number) pg.get("courseId")).longValue());
        jdbc.update("DELETE FROM course_page WHERE id = :id", p("id", pageId));
    }

    /** 重排：orderedIds 为该课程全部页面 id 的新顺序。 */
    @Transactional
    public List<Map<String, Object>> reorder(Long teacherId, Long courseId, List<Long> orderedIds) {
        requireOwner(teacherId, courseId);
        int seq = 1;
        for (Long id : orderedIds) {
            jdbc.update("UPDATE course_page SET seq = :s WHERE id = :id AND course_id = :c", p("s", seq++, "id", id, "c", courseId));
        }
        return pages(courseId);
    }

    @Transactional
    public Map<String, Object> attachFile(Long teacherId, Long pageId, MultipartFile file) {
        Map<String, Object> pg = page(pageId);
        requireOwner(teacherId, ((Number) pg.get("courseId")).longValue());
        StoredFile sf = storage.store(file, "course");
        jdbc.update("UPDATE course_page SET file_path = :fp, file_name = :fn WHERE id = :id",
                p("fp", sf.getRelPath(), "fn", sf.getOriginalName(), "id", pageId));
        return page(pageId);
    }

    private MapSqlParameterSource pageParams(Map<String, Object> f) {
        String title = (String) f.get("title");
        requireName(title);
        Object vis = f.get("studentVisible");
        Object score = f.get("score");
        Object ref = f.get("refId");
        return p("title", title.trim(), "content", f.get("content"), "link", f.get("linkUrl"),
                "vis", vis == null || Boolean.TRUE.equals(vis),
                "score", score == null || "".equals(score) ? 0 : ((Number) score).intValue(),
                "ref", ref == null || "".equals(ref) ? null : ((Number) ref).longValue());
    }

    private static String validType(String t) {
        if (t == null || !PAGE_TYPES.contains(t)) throw new IllegalArgumentException("无效的页面类型");
        return t;
    }

    private static void requireName(String n) {
        if (n == null || n.trim().isEmpty()) throw new IllegalArgumentException("名称不能为空");
    }
}
