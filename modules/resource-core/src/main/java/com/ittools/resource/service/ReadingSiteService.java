package com.ittools.resource.service;

import com.ittools.classroom.service.ClassBindingService;
import com.ittools.platform.settings.SystemSettingService;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.ZipPackage;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.ittools.platform.db.Sql.p;

/**
 * 课后阅读计时（原 student/resource.aspx “阅读时间”）与学生个人站点（原 student/website.aspx）。
 */
@Service
public class ReadingSiteService {
    public static final Pattern SITE_ID = Pattern.compile("[0-9a-f]{32}");
    static final int MAX_SITE_FILES = 3000;

    private final NamedParameterJdbcTemplate jdbc;
    private final FileStorageService storage;
    private final SystemSettingService settings;
    private final ClassBindingService bindings;

    public ReadingSiteService(NamedParameterJdbcTemplate jdbc, FileStorageService storage, SystemSettingService settings, ClassBindingService bindings) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.settings = settings;
        this.bindings = bindings;
    }

    // ================= 阅读计时 =================

    private void requireAssigned(Long studentId, Long resourceId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM resource_class rc JOIN users s ON s.class_id = rc.class_id "
                + "WHERE rc.resource_id = :r AND s.id = :s", p("r", resourceId, "s", studentId), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("该资料没有分发到你的班级");
    }

    /** 打开资料：记一次阅读。 */
    @Transactional
    public Map<String, Object> open(Long studentId, Long resourceId) {
        requireAssigned(studentId, resourceId);
        jdbc.update("INSERT INTO resource_read(resource_id, student_id, views) VALUES (:r, :s, 1) "
                + "ON CONFLICT (resource_id, student_id) DO UPDATE SET views = resource_read.views + 1, last_at = now()", p("r", resourceId, "s", studentId));
        return mine(studentId, resourceId);
    }

    /**
     * 阅读心跳（页面打开且可见时每 20 秒一次）：按服务器时间累计，距上次心跳超过 60 秒视为中断不计，单次最多计 30 秒。
     */
    @Transactional
    public Map<String, Object> beat(Long studentId, Long resourceId) {
        requireAssigned(studentId, resourceId);
        int n = jdbc.update("UPDATE resource_read SET seconds = seconds + CASE WHEN now() - last_at < INTERVAL '60 seconds' "
                + "THEN LEAST(30, CAST(EXTRACT(EPOCH FROM now() - last_at) AS INT)) ELSE 0 END, last_at = now() "
                + "WHERE resource_id = :r AND student_id = :s", p("r", resourceId, "s", studentId));
        if (n == 0) return open(studentId, resourceId);
        return mine(studentId, resourceId);
    }

    private Map<String, Object> mine(Long studentId, Long resourceId) {
        return jdbc.queryForMap("SELECT seconds, views FROM resource_read WHERE resource_id = :r AND student_id = :s", p("r", resourceId, "s", studentId));
    }

    /** 学生：我的阅读时间（按资料）。 */
    @Transactional(readOnly = true)
    public Map<Long, Map<String, Object>> myReading(Long studentId) {
        Map<Long, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT resource_id, seconds, views FROM resource_read WHERE student_id = :s", p("s", studentId))) {
            m.put(((Number) r.remove("resource_id")).longValue(), r);
        }
        return m;
    }

    /** 教师：某资料在某班的阅读情况。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> readingReport(Long teacherId, Long resourceId, Long classId) {
        List<Long> owner = jdbc.queryForList("SELECT teacher_id FROM resource WHERE id = :r", p("r", resourceId), Long.class);
        if (owner.isEmpty() || !owner.get(0).equals(teacherId)) throw new IllegalArgumentException("只能查看自己资料的阅读情况");
        if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权查看该班级");
        return jdbc.queryForList("SELECT u.id AS \"studentId\", u.student_no AS \"studentNo\", u.name, COALESCE(rr.seconds, 0) AS seconds, "
                + "COALESCE(rr.views, 0) AS views, rr.last_at AS \"lastAt\" FROM users u "
                + "LEFT JOIN resource_read rr ON rr.student_id = u.id AND rr.resource_id = :r "
                + "WHERE u.class_id = :k AND u.role = 'STUDENT' AND NOT u.graduated ORDER BY u.student_no NULLS LAST, u.name", p("r", resourceId, "k", classId));
    }

    // ================= 个人站点 =================

    public Path siteBase() {
        return storage.root().resolve("sites").toAbsolutePath().normalize();
    }

    public Path resolveSite(String id, String rel) {
        if (!SITE_ID.matcher(id).matches()) return null;
        Path dir = siteBase().resolve(id).normalize();
        Path f = dir.resolve(rel).normalize();
        return f.startsWith(dir) && Files.isRegularFile(f) ? f : null;
    }

    private static Map<String, Object> view(Map<String, Object> r) {
        if (r == null) return null;
        Map<String, Object> m = new LinkedHashMap<>(r);
        m.put("url", "/site/" + m.remove("package") + "/" + m.remove("entry"));
        return m;
    }

    private static final String SITE_COLS = "s.student_id AS \"studentId\", u.name, u.student_no AS \"studentNo\", s.title, s.package, s.entry, "
            + "s.file_count AS \"fileCount\", s.size_bytes AS \"sizeBytes\", s.updated_at AS \"updatedAt\" FROM student_site s JOIN users u ON u.id = s.student_id ";

    @Transactional(readOnly = true)
    public Map<String, Object> mySite(Long studentId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + SITE_COLS + "WHERE s.student_id = :s", p("s", studentId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("site", l.isEmpty() ? null : view(l.get(0)));
        out.put("maxMb", settings.getInt(SystemSettingService.SITE_MAX_MB));
        return out;
    }

    /** 上传网站包：zip 自动解压，首页 index.html（或层级最浅的网页），新站点替换旧站点。 */
    @Transactional
    public Map<String, Object> uploadSite(Long studentId, MultipartFile file, String title) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".zip")) throw new IllegalArgumentException("请把网站整个文件夹压缩成 zip 上传");
        long max = settings.getInt(SystemSettingService.SITE_MAX_MB) * 1024L * 1024L;
        if (file.getSize() > max) throw new IllegalArgumentException("网站包超过 " + settings.getInt(SystemSettingService.SITE_MAX_MB) + "MB");
        String t = title == null || title.trim().isEmpty() ? "我的网站" : title.trim();
        if (t.length() > 100) throw new IllegalArgumentException("网站名称不超过 100 字");
        String id = UUID.randomUUID().toString().replace("-", "");
        Path dir = siteBase().resolve(id);
        File tmp = File.createTempFile("site", ".zip");
        try {
            file.transferTo(tmp);
            ZipPackage.Result res = ZipPackage.extract(tmp, dir, max, MAX_SITE_FILES);
            String entry = ZipPackage.findEntry(res.files, Arrays.asList("index.html", "index.htm", "default.html", "default.htm"));
            List<String> old = jdbc.queryForList("SELECT package FROM student_site WHERE student_id = :s", p("s", studentId), String.class);
            jdbc.update("INSERT INTO student_site(student_id, package, entry, title, file_count, size_bytes) VALUES (:s, :p, :e, :t, :n, :b) "
                            + "ON CONFLICT (student_id) DO UPDATE SET package = EXCLUDED.package, entry = EXCLUDED.entry, title = EXCLUDED.title, "
                            + "file_count = EXCLUDED.file_count, size_bytes = EXCLUDED.size_bytes, updated_at = now()",
                    p("s", studentId, "p", id, "e", entry, "t", t, "n", res.files.size(), "b", res.bytes));
            if (!old.isEmpty()) ZipPackage.deleteDir(siteBase().resolve(old.get(0)));
            return mySite(studentId);
        } finally {
            if (!tmp.delete()) tmp.deleteOnExit();
        }
    }

    @Transactional
    public Map<String, Object> renameSite(Long studentId, String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty() || t.length() > 100) throw new IllegalArgumentException("网站名称为 1~100 字");
        jdbc.update("UPDATE student_site SET title = :t WHERE student_id = :s", p("t", t, "s", studentId));
        return mySite(studentId);
    }

    @Transactional
    public void deleteSite(Long studentId) {
        List<String> old = jdbc.queryForList("SELECT package FROM student_site WHERE student_id = :s", p("s", studentId), String.class);
        jdbc.update("DELETE FROM student_site WHERE student_id = :s", p("s", studentId));
        if (!old.isEmpty()) ZipPackage.deleteDir(siteBase().resolve(old.get(0)));
    }

    /** 班级网站展示：学生看本班；教师看可上课的班级。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> classSites(Long userId, String role, Long classId) {
        Long k = classId;
        if ("STUDENT".equals(role)) k = jdbc.queryForList("SELECT class_id FROM users WHERE id = :u", p("u", userId), Long.class).stream().findFirst().orElse(null);
        else if ("TEACHER".equals(role) && !bindings.teacherCanUseClass(userId, classId)) throw new IllegalArgumentException("无权查看该班级");
        if (k == null) throw new IllegalArgumentException("请选择班级");
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + SITE_COLS + "WHERE u.class_id = :k AND NOT u.graduated "
                + "ORDER BY u.student_no NULLS LAST, u.name", p("k", k));
        l.replaceAll(ReadingSiteService::view);
        return l;
    }

    /** 教师删除本班学生的不当站点。 */
    @Transactional
    public void teacherDeleteSite(Long teacherId, Long studentId) {
        Long k = jdbc.queryForList("SELECT class_id FROM users WHERE id = :u AND role = 'STUDENT'", p("u", studentId), Long.class).stream().findFirst().orElse(null);
        if (k == null || !bindings.teacherCanUseClass(teacherId, k)) throw new IllegalArgumentException("无权管理该学生");
        deleteSite(studentId);
    }
}
