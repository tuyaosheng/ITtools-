package com.ittools.classroom.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.ittools.platform.db.Sql.p;

/**
 * 课件包（原 storyline：Storyline 发布包 / 打包的网站，rar 改为 zip）。
 * 解压到 storage/courseware/{包编号}/，由 /courseware/{包编号}/** 公开提供（编号不可猜测，响应带 CSP sandbox）。
 */
@Service
public class CoursewareService {
    public static final Pattern PACKAGE_ID = Pattern.compile("[0-9a-f]{32}");
    static final long MAX_TOTAL = 500L * 1024 * 1024;
    static final int MAX_ENTRIES = 20000;
    private static final List<String> ENTRY_CANDIDATES = Arrays.asList("story.html", "story_html5.html", "index.html", "index.htm", "index_lms.html");

    private final NamedParameterJdbcTemplate jdbc;
    private final CourseService courses;
    private final FileStorageService storage;
    private final ObjectMapper json = new ObjectMapper();

    public CoursewareService(NamedParameterJdbcTemplate jdbc, CourseService courses, FileStorageService storage) {
        this.jdbc = jdbc;
        this.courses = courses;
        this.storage = storage;
    }

    public Path base() {
        return storage.root().resolve("courseware").toAbsolutePath().normalize();
    }

    /** 兼容模式标记文件：存在时该包不加沙箱（与平台同源运行，可使用 localStorage 等）。 */
    static final String TRUSTED_MARK = ".ittools-compat";

    /** 包内文件路径 → 磁盘路径；越界、不存在或为内部标记文件时返回 null。 */
    public Path resolve(String packageId, String relPath) {
        if (!PACKAGE_ID.matcher(packageId).matches()) return null;
        Path dir = base().resolve(packageId).normalize();
        Path f = dir.resolve(relPath).normalize();
        if (!f.startsWith(dir) || !Files.isRegularFile(f) || f.equals(dir.resolve(TRUSTED_MARK))) return null;
        return f;
    }

    public boolean isCompat(String packageId) {
        return PACKAGE_ID.matcher(packageId).matches() && Files.exists(base().resolve(packageId).resolve(TRUSTED_MARK));
    }

    /** 设置兼容模式（关闭隔离）。课件脚本将能以当前登录用户身份访问平台，只对可信的课件开启。 */
    @Transactional
    public Map<String, Object> setCompat(Long teacherId, Long pageId, boolean compat) {
        Map<String, Object> pg = courses.page(pageId);
        courses.requireOwner(teacherId, ((Number) pg.get("courseId")).longValue());
        Object pkg = config(pageId).get("package");
        if (pkg == null || !PACKAGE_ID.matcher(String.valueOf(pkg)).matches()) throw new IllegalArgumentException("请先上传课件包");
        Path mark = base().resolve(String.valueOf(pkg)).resolve(TRUSTED_MARK);
        try {
            if (compat) Files.write(mark, new byte[0]);
            else Files.deleteIfExists(mark);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return publicConfig(pageId);
    }

    @Transactional
    public Map<String, Object> importPackage(Long teacherId, Long pageId, MultipartFile file, String entry) {
        Map<String, Object> pg = courses.page(pageId);
        courses.requireOwner(teacherId, ((Number) pg.get("courseId")).longValue());
        if (!"STORYLINE".equals(pg.get("pageType"))) throw new IllegalArgumentException("只有课件包页面可以上传课件包");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".zip")) throw new IllegalArgumentException("请上传 zip 格式的课件包");
        String id = UUID.randomUUID().toString().replace("-", "");
        Path dir = base().resolve(id);
        File tmp = null;
        try {
            tmp = File.createTempFile("courseware", ".zip");
            file.transferTo(tmp);
            List<String> files = ZipPackage.extract(tmp, dir, MAX_TOTAL, MAX_ENTRIES).files;
            String start = findEntry(files, entry);
            Map<String, Object> cfg = config(pageId);
            Object old = cfg.get("package");
            cfg.put("package", id);
            cfg.put("entry", start);
            cfg.put("fileName", name);
            cfg.put("files", files.size());
            jdbc.update("UPDATE course_page SET config = :c WHERE id = :id", p("c", json.writeValueAsString(cfg), "id", pageId));
            if (old != null) removeIfUnused(String.valueOf(old));
            return cfg;
        } catch (IOException e) {
            ZipPackage.deleteDir(dir);
            throw new IllegalArgumentException("课件包解压失败：" + e.getMessage());
        } catch (RuntimeException e) {
            ZipPackage.deleteDir(dir);
            throw e;
        } finally {
            if (tmp != null && !tmp.delete()) tmp.deleteOnExit();
        }
    }

    /** 修改入口页（课件包中的主页文件）。 */
    @Transactional
    public Map<String, Object> setEntry(Long teacherId, Long pageId, String entry) {
        Map<String, Object> pg = courses.page(pageId);
        courses.requireOwner(teacherId, ((Number) pg.get("courseId")).longValue());
        Map<String, Object> cfg = config(pageId);
        Object pkg = cfg.get("package");
        if (pkg == null) throw new IllegalArgumentException("请先上传课件包");
        if (entry == null || resolve(String.valueOf(pkg), entry.trim()) == null) throw new IllegalArgumentException("课件包中没有该文件");
        cfg.put("entry", entry.trim());
        try {
            jdbc.update("UPDATE course_page SET config = :c WHERE id = :id", p("c", json.writeValueAsString(cfg), "id", pageId));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return cfg;
    }

    /** 包内的 html 文件列表（供选择入口页）。 */
    @Transactional(readOnly = true)
    public List<String> htmlFiles(Long teacherId, Long pageId) {
        Map<String, Object> pg = courses.page(pageId);
        courses.getReadable(teacherId, ((Number) pg.get("courseId")).longValue());
        Object pkg = config(pageId).get("package");
        List<String> out = new ArrayList<>();
        if (pkg == null || !PACKAGE_ID.matcher(String.valueOf(pkg)).matches()) return out;
        Path dir = base().resolve(String.valueOf(pkg));
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.walk(dir, 4)) {
            s.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase().matches(".*\\.html?$"))
                    .map(f -> dir.relativize(f).toString().replace('\\', '/')).sorted().limit(200).forEach(out::add);
        } catch (IOException ignored) {
            // 读不到就返回空列表
        }
        return out;
    }

    /** 学生/教师读取课件包页面配置（只含包编号与入口，供 iframe 使用）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> publicConfig(Long pageId) {
        Map<String, Object> cfg = config(pageId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (cfg.get("package") != null) {
            out.put("url", "/courseware/" + cfg.get("package") + "/" + cfg.get("entry"));
            out.put("fileName", cfg.get("fileName"));
            out.put("compat", isCompat(String.valueOf(cfg.get("package"))));
        }
        return out;
    }

    private Map<String, Object> config(Long pageId) {
        List<String> l = jdbc.queryForList("SELECT config FROM course_page WHERE id = :id", p("id", pageId), String.class);
        if (l.isEmpty() || l.get(0) == null || l.get(0).isEmpty()) return new LinkedHashMap<>();
        try {
            return json.readValue(l.get(0), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    /** 课程复制后多个页面可能共用同一个包：没有页面再引用时才删除目录。 */
    private void removeIfUnused(String packageId) {
        if (!PACKAGE_ID.matcher(packageId).matches()) return;
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM course_page WHERE config LIKE :k", p("k", "%\"package\":\"" + packageId + "\"%"), Long.class);
        if (n != null && n == 0) ZipPackage.deleteDir(base().resolve(packageId));
    }

    static String findEntry(List<String> files, String wanted) {
        if (wanted != null && !wanted.trim().isEmpty()) {
            String w = wanted.trim().replace('\\', '/');
            if (files.contains(w)) return w;
            throw new ZipPackage.BadPackage("课件包中没有主页文件：" + w);
        }
        return ZipPackage.findEntry(files, ENTRY_CANDIDATES);
    }

}
