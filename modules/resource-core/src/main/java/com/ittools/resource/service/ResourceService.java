package com.ittools.resource.service;

import com.ittools.classroom.service.ClassBindingService;
import com.ittools.platform.settings.SystemSettingService;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 子项目 7：教学资源（resourcemanage / resource / viewresource）、教案（Lessonplans）、网盘（webdisc）、学习笔记（note / reflection）。
 */
@Service
public class ResourceService {
    private final NamedParameterJdbcTemplate jdbc;
    private final FileStorageService storage;
    private final SystemSettingService settings;
    private final ClassBindingService bindings;

    public ResourceService(NamedParameterJdbcTemplate jdbc, FileStorageService storage, SystemSettingService settings,
                           ClassBindingService bindings) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.settings = settings;
        this.bindings = bindings;
    }

    // ================= 教学资源 =================

    private static final String RES_COLS =
            "r.id, r.teacher_id AS \"teacherId\", u.name AS \"teacherName\", r.title, r.description, r.category, r.term_id AS \"termId\", "
                    + "t.year_name || ' ' || t.term_name AS \"termLabel\", r.file_name AS \"fileName\", r.file_size AS \"fileSize\", "
                    + "r.link_url AS \"linkUrl\", r.shared, r.downloads, r.created_at AS \"createdAt\" "
                    + "FROM resource r JOIN users u ON u.id = r.teacher_id LEFT JOIN teaching_term t ON t.id = r.term_id ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherResources(Long teacherId, boolean shared, String category, Long termId) {
        StringBuilder sql = new StringBuilder("SELECT " + RES_COLS + "WHERE ");
        sql.append(shared ? "r.shared AND r.teacher_id <> :t " : "r.teacher_id = :t ");
        MapSqlParameterSource ps = p("t", teacherId);
        if (category != null && !category.isEmpty()) { sql.append("AND r.category = :c "); ps.addValue("c", category); }
        if (termId != null) { sql.append("AND r.term_id = :term "); ps.addValue("term", termId); }
        sql.append("ORDER BY r.id DESC");
        List<Map<String, Object>> l = jdbc.queryForList(sql.toString(), ps);
        for (Map<String, Object> r : l) {
            r.put("classIds", jdbc.queryForList("SELECT class_id FROM resource_class WHERE resource_id = :r", p("r", r.get("id")), Long.class));
        }
        return l;
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM resource WHERE category IS NOT NULL AND category <> '' ORDER BY category", p(), String.class);
    }

    @Transactional
    public Map<String, Object> saveResource(Long teacherId, Long id, Map<String, Object> f, MultipartFile file) {
        String title = f.get("title") == null ? null : String.valueOf(f.get("title")).trim();
        if (title == null || title.isEmpty()) throw new IllegalArgumentException("资源标题不能为空");
        Object term = f.get("termId");
        MapSqlParameterSource ps = p("t", teacherId, "ti", title, "d", f.get("description"), "c", f.get("category"),
                "term", term == null || "".equals(term) ? null : Long.valueOf(String.valueOf(term)),
                "l", f.get("linkUrl"), "s", "true".equals(String.valueOf(f.get("shared"))));
        if (id == null) {
            if ((file == null || file.isEmpty()) && (f.get("linkUrl") == null || String.valueOf(f.get("linkUrl")).trim().isEmpty())) {
                throw new IllegalArgumentException("请上传文件或填写链接");
            }
            id = jdbc.queryForObject("INSERT INTO resource(teacher_id, title, description, category, term_id, link_url, shared) "
                    + "VALUES (:t, :ti, :d, :c, :term, :l, :s) RETURNING id", ps, Long.class);
        } else {
            requireResourceOwner(teacherId, id);
            ps.addValue("id", id);
            jdbc.update("UPDATE resource SET title = :ti, description = :d, category = :c, term_id = :term, link_url = :l, shared = :s WHERE id = :id", ps);
        }
        if (file != null && !file.isEmpty()) {
            StoredFile sf = storage.store(file, "resource");
            List<String> old = jdbc.queryForList("SELECT file_path FROM resource WHERE id = :id AND file_path IS NOT NULL", p("id", id), String.class);
            jdbc.update("UPDATE resource SET file_path = :fp, file_name = :fn, file_size = :sz WHERE id = :id",
                    p("fp", sf.getRelPath(), "fn", sf.getOriginalName(), "sz", sf.getSize(), "id", id));
            if (!old.isEmpty()) storage.delete(old.get(0));
        }
        return jdbc.queryForMap("SELECT " + RES_COLS + "WHERE r.id = :id", p("id", id));
    }

    /** 分发给班级（原 it_resourcestu），整体替换。 */
    @Transactional
    public void distribute(Long teacherId, Long resourceId, List<Long> classIds) {
        requireResourceOwner(teacherId, resourceId);
        for (Long k : classIds) {
            if (!bindings.teacherCanUseClass(teacherId, k)) throw new IllegalArgumentException("无权向该班级分发");
        }
        jdbc.update("DELETE FROM resource_class WHERE resource_id = :r", p("r", resourceId));
        for (Long k : classIds) {
            jdbc.update("INSERT INTO resource_class(resource_id, class_id) VALUES (:r, :k)", p("r", resourceId, "k", k));
        }
    }

    @Transactional
    public void deleteResource(Long teacherId, Long id) {
        requireResourceOwner(teacherId, id);
        List<String> fp = jdbc.queryForList("SELECT file_path FROM resource WHERE id = :id AND file_path IS NOT NULL", p("id", id), String.class);
        jdbc.update("DELETE FROM resource WHERE id = :id", p("id", id));
        if (!fp.isEmpty()) storage.delete(fp.get(0));
    }

    /** 学生可见资源：分发到本班的资源（原 resource.aspx 按学期分类浏览）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentResources(Long studentId) {
        return jdbc.queryForList("SELECT " + RES_COLS + "JOIN resource_class rc ON rc.resource_id = r.id "
                + "JOIN users s ON s.class_id = rc.class_id WHERE s.id = :s ORDER BY r.id DESC", p("s", studentId));
    }

    /** 下载权限：作者/共享给教师/分发给学生所在班级。 */
    @Transactional
    public Map<String, Object> resourceFile(Long userId, String role, Long id) {
        Map<String, Object> r = jdbc.queryForMap("SELECT teacher_id, shared, file_path, file_name FROM resource WHERE id = :id", p("id", id));
        boolean ok;
        if ("TEACHER".equals(role)) {
            ok = userId.equals(((Number) r.get("teacher_id")).longValue()) || Boolean.TRUE.equals(r.get("shared"));
        } else if ("STUDENT".equals(role)) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM resource_class rc JOIN users s ON s.class_id = rc.class_id "
                    + "WHERE rc.resource_id = :r AND s.id = :s", p("r", id, "s", userId), Long.class);
            ok = n != null && n > 0;
        } else {
            ok = true;
        }
        if (!ok) throw new IllegalArgumentException("无权下载该资源");
        if (r.get("file_path") == null) throw new IllegalArgumentException("该资源没有文件");
        jdbc.update("UPDATE resource SET downloads = downloads + 1 WHERE id = :id", p("id", id));
        return r;
    }

    private void requireResourceOwner(Long teacherId, Long id) {
        List<Long> l = jdbc.queryForList("SELECT teacher_id FROM resource WHERE id = :id", p("id", id), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("资源不存在");
        if (!l.get(0).equals(teacherId)) throw new IllegalArgumentException("只能修改自己的资源");
    }

    // ================= 教案 =================

    private static final String PLAN_COLS =
            "lp.id, lp.teacher_id AS \"teacherId\", u.name AS \"teacherName\", lp.title, lp.term_id AS \"termId\", "
                    + "t.year_name || ' ' || t.term_name AS \"termLabel\", lp.content, lp.doc_name AS \"docName\", lp.ppt_name AS \"pptName\", "
                    + "lp.shared, lp.created_at AS \"createdAt\", lp.updated_at AS \"updatedAt\" "
                    + "FROM lesson_plan lp JOIN users u ON u.id = lp.teacher_id LEFT JOIN teaching_term t ON t.id = lp.term_id ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> plans(Long teacherId, boolean shared, Long termId) {
        return jdbc.queryForList("SELECT " + PLAN_COLS + "WHERE " + (shared ? "lp.shared AND lp.teacher_id <> :t " : "lp.teacher_id = :t ")
                + (termId == null ? "" : "AND lp.term_id = :term ") + "ORDER BY lp.id DESC", p("t", teacherId, "term", termId));
    }

    @Transactional
    public Map<String, Object> savePlan(Long teacherId, Long id, Map<String, Object> f, MultipartFile doc, MultipartFile ppt) {
        String title = f.get("title") == null ? null : String.valueOf(f.get("title")).trim();
        if (title == null || title.isEmpty()) throw new IllegalArgumentException("教案标题不能为空");
        Object term = f.get("termId");
        MapSqlParameterSource ps = p("t", teacherId, "ti", title, "c", f.get("content"),
                "term", term == null || "".equals(term) ? null : Long.valueOf(String.valueOf(term)),
                "s", "true".equals(String.valueOf(f.get("shared"))));
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO lesson_plan(teacher_id, title, content, term_id, shared) VALUES (:t, :ti, :c, :term, :s) RETURNING id",
                    ps, Long.class);
        } else {
            requirePlanOwner(teacherId, id);
            ps.addValue("id", id);
            jdbc.update("UPDATE lesson_plan SET title = :ti, content = :c, term_id = :term, shared = :s, updated_at = now() WHERE id = :id", ps);
        }
        if (doc != null && !doc.isEmpty()) replacePlanFile(id, "doc", storage.store(doc, "lessonplan"));
        if (ppt != null && !ppt.isEmpty()) replacePlanFile(id, "ppt", storage.store(ppt, "lessonplan"));
        return jdbc.queryForMap("SELECT " + PLAN_COLS + "WHERE lp.id = :id", p("id", id));
    }

    private void replacePlanFile(Long id, String kind, StoredFile sf) {
        List<String> old = jdbc.queryForList("SELECT " + kind + "_path FROM lesson_plan WHERE id = :id AND " + kind + "_path IS NOT NULL",
                p("id", id), String.class);
        jdbc.update("UPDATE lesson_plan SET " + kind + "_path = :fp, " + kind + "_name = :fn WHERE id = :id",
                p("fp", sf.getRelPath(), "fn", sf.getOriginalName(), "id", id));
        if (!old.isEmpty()) deletePlanFileIfUnused(old.get(0));
    }

    /** 复制的教案与原教案共用附件文件，只有不再被任何教案引用时才删除物理文件。 */
    private void deletePlanFileIfUnused(String path) {
        if (path == null) return;
        Long refs = jdbc.queryForObject("SELECT COUNT(*) FROM lesson_plan WHERE doc_path = :p OR ppt_path = :p", p("p", path), Long.class);
        if (refs == null || refs == 0) storage.delete(path);
    }

    @Transactional
    public void deletePlan(Long teacherId, Long id) {
        requirePlanOwner(teacherId, id);
        Map<String, Object> r = jdbc.queryForMap("SELECT doc_path, ppt_path FROM lesson_plan WHERE id = :id", p("id", id));
        jdbc.update("DELETE FROM lesson_plan WHERE id = :id", p("id", id));
        deletePlanFileIfUnused((String) r.get("doc_path"));
        deletePlanFileIfUnused((String) r.get("ppt_path"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> planFile(Long teacherId, Long id, String kind) {
        if (!"doc".equals(kind) && !"ppt".equals(kind)) throw new IllegalArgumentException("无效文件类型");
        Map<String, Object> r = jdbc.queryForMap("SELECT teacher_id, shared, " + kind + "_path AS path, " + kind + "_name AS name FROM lesson_plan WHERE id = :id",
                p("id", id));
        if (!teacherId.equals(((Number) r.get("teacher_id")).longValue()) && !Boolean.TRUE.equals(r.get("shared"))) {
            throw new IllegalArgumentException("无权查看该教案");
        }
        if (r.get("path") == null) throw new IllegalArgumentException("没有该文件");
        return r;
    }

    /** 复制共享教案到自己名下。 */
    @Transactional
    public Long copyPlan(Long teacherId, Long id) {
        Map<String, Object> r = jdbc.queryForMap("SELECT teacher_id, shared FROM lesson_plan WHERE id = :id", p("id", id));
        if (!teacherId.equals(((Number) r.get("teacher_id")).longValue()) && !Boolean.TRUE.equals(r.get("shared"))) {
            throw new IllegalArgumentException("无权复制该教案");
        }
        return jdbc.queryForObject("INSERT INTO lesson_plan(teacher_id, title, term_id, content, doc_path, doc_name, ppt_path, ppt_name, shared) "
                + "SELECT :t, title || '（副本）', term_id, content, doc_path, doc_name, ppt_path, ppt_name, FALSE FROM lesson_plan WHERE id = :id RETURNING id",
                p("t", teacherId, "id", id), Long.class);
    }

    private void requirePlanOwner(Long teacherId, Long id) {
        List<Long> l = jdbc.queryForList("SELECT teacher_id FROM lesson_plan WHERE id = :id", p("id", id), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("教案不存在");
        if (!l.get(0).equals(teacherId)) throw new IllegalArgumentException("只能修改自己的教案");
    }

    // ================= 网盘 =================

    @Transactional(readOnly = true)
    public Map<String, Object> disk(Long ownerId) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("files", jdbc.queryForList("SELECT id, title, note, file_name AS \"fileName\", file_size AS \"fileSize\", created_at AS \"createdAt\" "
                + "FROM disk_file WHERE owner_id = :o ORDER BY id DESC", p("o", ownerId)));
        Long used = jdbc.queryForObject("SELECT COALESCE(SUM(file_size), 0) FROM disk_file WHERE owner_id = :o", p("o", ownerId), Long.class);
        m.put("used", used);
        m.put("quota", settings.getInt(SystemSettingService.DISK_QUOTA_MB) * 1024L * 1024L);
        Map<String, Object> pol = policy(ownerId);
        m.put("disabled", pol.get("disabled"));
        m.put("penaltyBytes", pol.get("penalty_bytes"));
        return m;
    }

    /** 教师对学生网盘的限制：关闭网盘、扣除空间（原 teacher/webdisc）。 */
    private Map<String, Object> policy(Long userId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT disabled, penalty_bytes FROM disk_policy WHERE user_id = :u", p("u", userId));
        if (!l.isEmpty()) return l.get(0);
        Map<String, Object> d = new java.util.HashMap<>();
        d.put("disabled", false);
        d.put("penalty_bytes", 0L);
        return d;
    }

    @Transactional
    public Map<String, Object> diskUpload(Long ownerId, String role, MultipartFile file, String title, String note) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择文件");
        long quota = settings.getInt(SystemSettingService.DISK_QUOTA_MB) * 1024L * 1024L;
        Long used = jdbc.queryForObject("SELECT COALESCE(SUM(file_size), 0) FROM disk_file WHERE owner_id = :o", p("o", ownerId), Long.class);
        // 容量限制针对学生（原"网盘设置：学生网盘容量"），教师不限
        if ("STUDENT".equals(role)) {
            Map<String, Object> pol = policy(ownerId);
            if (Boolean.TRUE.equals(pol.get("disabled"))) throw new IllegalArgumentException("你的网盘已被老师关闭");
            used += ((Number) pol.get("penalty_bytes")).longValue();
        }
        if ("STUDENT".equals(role) && used + file.getSize() > quota) {
            throw new IllegalArgumentException("网盘空间不足（容量 " + settings.getInt(SystemSettingService.DISK_QUOTA_MB) + "MB）");
        }
        StoredFile sf = storage.store(file, "disk");
        jdbc.update("INSERT INTO disk_file(owner_id, title, note, file_path, file_name, file_size) VALUES (:o, :t, :n, :fp, :fn, :sz)",
                p("o", ownerId, "t", title == null || title.trim().isEmpty() ? sf.getOriginalName() : title.trim(), "n", note,
                        "fp", sf.getRelPath(), "fn", sf.getOriginalName(), "sz", sf.getSize()));
        return disk(ownerId);
    }

    @Transactional
    public void diskDelete(Long ownerId, Long id) {
        List<String> l = jdbc.queryForList("SELECT file_path FROM disk_file WHERE id = :id AND owner_id = :o", p("id", id, "o", ownerId), String.class);
        if (l.isEmpty()) throw new IllegalArgumentException("文件不存在");
        jdbc.update("DELETE FROM disk_file WHERE id = :id", p("id", id));
        storage.delete(l.get(0));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> diskFile(Long ownerId, Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT file_path, file_name FROM disk_file WHERE id = :id AND owner_id = :o",
                p("id", id, "o", ownerId));
        if (l.isEmpty()) throw new IllegalArgumentException("文件不存在");
        return l.get(0);
    }

    // ================= 学习笔记 / 反思 =================

    private static final String NOTE_COLS = "n.id, n.title, n.content, n.course_id AS \"courseId\", c.name AS \"courseName\", n.submitted, "
            + "n.teacher_comment AS \"teacherComment\", n.created_at AS \"createdAt\", n.updated_at AS \"updatedAt\" ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myNotes(Long authorId) {
        return jdbc.queryForList("SELECT " + NOTE_COLS + "FROM study_note n LEFT JOIN course c ON c.id = n.course_id WHERE n.author_id = :a "
                + "ORDER BY n.updated_at DESC", p("a", authorId));
    }

    /** 学生可关联的课程：本班上过课的课程。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentCourses(Long studentId) {
        return jdbc.queryForList("SELECT DISTINCT c.id, c.name FROM lesson l JOIN course c ON c.id = l.course_id JOIN users s ON s.class_id = l.class_id "
                + "WHERE s.id = :s ORDER BY c.id DESC", p("s", studentId));
    }

    @Transactional
    public Map<String, Object> saveNote(Long authorId, Long id, Map<String, Object> f) {
        String title = f.get("title") == null ? null : String.valueOf(f.get("title")).trim();
        if (title == null || title.isEmpty()) throw new IllegalArgumentException("标题不能为空");
        Object course = f.get("courseId");
        MapSqlParameterSource ps = p("a", authorId, "t", title, "c", f.get("content"),
                "co", course == null || "".equals(course) ? null : ((Number) course).longValue(), "s", Boolean.TRUE.equals(f.get("submitted")));
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO study_note(author_id, title, content, course_id, submitted) VALUES (:a, :t, :c, :co, :s) RETURNING id",
                    ps, Long.class);
        } else {
            ps.addValue("id", id);
            if (jdbc.update("UPDATE study_note SET title = :t, content = :c, course_id = :co, submitted = :s, updated_at = now() "
                    + "WHERE id = :id AND author_id = :a", ps) == 0) {
                throw new IllegalArgumentException("笔记不存在");
            }
        }
        return jdbc.queryForMap("SELECT " + NOTE_COLS + "FROM study_note n LEFT JOIN course c ON c.id = n.course_id WHERE n.id = :id", p("id", id));
    }

    @Transactional
    public void deleteNote(Long authorId, Long id) {
        jdbc.update("DELETE FROM study_note WHERE id = :id AND author_id = :a", p("id", id, "a", authorId));
    }

    /** 教师查看提交到自己课程的学生反思。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> reflectionsForTeacher(Long teacherId) {
        return jdbc.queryForList("SELECT " + NOTE_COLS + ", u.name AS \"studentName\", sy.label || k.name AS \"className\" "
                + "FROM study_note n JOIN course c ON c.id = n.course_id JOIN users u ON u.id = n.author_id "
                + "LEFT JOIN klass k ON k.id = u.class_id LEFT JOIN school_year sy ON sy.id = k.school_year_id "
                + "WHERE n.submitted AND c.teacher_id = :t ORDER BY n.updated_at DESC", p("t", teacherId));
    }

    @Transactional
    public void commentReflection(Long teacherId, Long noteId, String comment) {
        int n = jdbc.update("UPDATE study_note SET teacher_comment = :c WHERE id = :id AND submitted "
                + "AND course_id IN (SELECT id FROM course WHERE teacher_id = :t)", p("c", comment, "id", noteId, "t", teacherId));
        if (n == 0) throw new IllegalArgumentException("反思不存在或不属于你的课程");
    }
}
