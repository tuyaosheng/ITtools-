package com.ittools.classroom.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.ittools.platform.db.Sql.p;

/**
 * 课程包导出/导入（原“课程打包下载/导入”）：一个 zip 包含 course.json（课程与页面、页面设置）、
 * files/（页面附件）与 courseware/（课件包页面的解压内容），可在不同学校、不同服务器之间交换课程。
 */
@Service
public class CoursePackService {
    public static final String FORMAT = "ittools-course";
    static final long MAX_TOTAL = 1024L * 1024 * 1024;
    static final int MAX_ENTRIES = 50000;
    static final int MAX_PAGES = 300;

    private final NamedParameterJdbcTemplate jdbc;
    private final CourseService courses;
    private final CoursewareService courseware;
    private final FileStorageService storage;
    private final ObjectMapper json = new ObjectMapper();

    public CoursePackService(NamedParameterJdbcTemplate jdbc, CourseService courses, CoursewareService courseware, FileStorageService storage) {
        this.jdbc = jdbc;
        this.courses = courses;
        this.courseware = courseware;
        this.storage = storage;
    }

    // ================= 导出 =================

    @Transactional(readOnly = true)
    public String fileName(Long teacherId, Long courseId) {
        Map<String, Object> c = courses.getReadable(teacherId, courseId);
        return c.get("name") + ".ittools.zip";
    }

    @Transactional(readOnly = true)
    public void export(Long teacherId, Long courseId, OutputStream out) throws IOException {
        Map<String, Object> c = courses.getReadable(teacherId, courseId);
        List<Map<String, Object>> pages = jdbc.queryForList("SELECT p.id, p.seq, p.title, p.page_type, p.content, p.link_url, p.file_path, p.file_name, "
                + "p.student_visible, p.score, p.ref_id, p.config, "
                + "CASE WHEN p.page_type = 'QUIZ' THEN (SELECT name FROM paper WHERE id = p.ref_id) "
                + "     WHEN p.page_type = 'PYTHON' THEN (SELECT title FROM py_problem WHERE id = p.ref_id) END AS ref_title "
                + "FROM course_page p WHERE p.course_id = :c ORDER BY p.seq, p.id", p("c", courseId));
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", FORMAT);
        manifest.put("version", 1);
        manifest.put("exportedAt", OffsetDateTime.now().toString());
        Map<String, Object> course = new LinkedHashMap<>();
        course.put("name", c.get("name"));
        course.put("category", c.get("category"));
        course.put("description", c.get("description"));
        manifest.put("course", course);
        List<Map<String, Object>> outPages = new ArrayList<>();
        List<String[]> files = new ArrayList<>();          // [zip 路径, 存储相对路径]
        List<String> packages = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> pg : pages) {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", pg.get("title"));
            m.put("pageType", pg.get("page_type"));
            m.put("content", pg.get("content"));
            m.put("linkUrl", pg.get("link_url"));
            m.put("studentVisible", pg.get("student_visible"));
            m.put("score", pg.get("score"));
            if (pg.get("ref_id") != null) {
                m.put("refId", pg.get("ref_id"));
                m.put("refTitle", pg.get("ref_title"));
            }
            Map<String, Object> cfg = parse((String) pg.get("config"));
            if (cfg != null) {
                m.put("config", cfg);
                Object pkg = cfg.get("package");
                if (pkg != null && CoursewareService.PACKAGE_ID.matcher(String.valueOf(pkg)).matches() && !packages.contains(String.valueOf(pkg))) {
                    packages.add(String.valueOf(pkg));
                }
            }
            if (pg.get("file_path") != null && Files.isRegularFile(storage.resolve((String) pg.get("file_path")))) {
                String zipPath = "files/" + i + "/" + safeName((String) pg.get("file_name"));
                m.put("file", zipPath);
                m.put("fileName", pg.get("file_name"));
                files.add(new String[]{zipPath, (String) pg.get("file_path")});
            }
            Map<String, Object> ext = new LinkedHashMap<>();
            for (PageExtension e : courses.pageExtensions()) {
                Map<String, Object> d = e.export(((Number) pg.get("id")).longValue());
                if (d != null) ext.put(e.key(), d);
            }
            if (!ext.isEmpty()) m.put("extensions", ext);
            outPages.add(m);
        }
        manifest.put("pages", outPages);

        ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8);
        z.putNextEntry(new ZipEntry("course.json"));
        z.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        z.closeEntry();
        for (String[] f : files) {
            z.putNextEntry(new ZipEntry(f[0]));
            Files.copy(storage.resolve(f[1]), z);
            z.closeEntry();
        }
        for (String pkg : packages) {
            Path dir = courseware.base().resolve(pkg);
            if (!Files.isDirectory(dir)) continue;
            List<Path> all;
            try (Stream<Path> s = Files.walk(dir)) {
                all = s.filter(Files::isRegularFile).collect(Collectors.toList());
            }
            for (Path f : all) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                if (rel.equals(CoursewareService.TRUSTED_MARK)) continue;
                z.putNextEntry(new ZipEntry("courseware/" + pkg + "/" + rel));
                Files.copy(f, z);
                z.closeEntry();
            }
        }
        z.finish();
        z.flush();
    }

    private static String safeName(String n) {
        String s = n == null ? "file" : n.replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isEmpty() ? "file" : s;
    }

    private Map<String, Object> parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return json.readValue(s, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException e) {
            return null;
        }
    }

    // ================= 导入 =================

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> importPack(Long teacherId, MultipartFile file, Long termId) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".zip")) throw new IllegalArgumentException("请选择课程包（.zip）");
        File tmp = null;
        List<Path> createdDirs = new ArrayList<>();
        try {
            tmp = File.createTempFile("coursepack", ".zip");
            file.transferTo(tmp);
            try (ZipFile zf = new ZipFile(tmp, StandardCharsets.UTF_8)) {
                Map<String, ZipEntry> entries = new LinkedHashMap<>();
                Enumeration<? extends ZipEntry> en = zf.entries();
                long total = 0;
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (entries.size() >= MAX_ENTRIES) throw new IllegalArgumentException("课程包文件过多");
                    if (e.getSize() > 0) total += e.getSize();
                    if (total > MAX_TOTAL) throw new IllegalArgumentException("课程包解压后超过 1GB");
                    entries.put(e.getName().replace('\\', '/'), e);
                }
                ZipEntry manifestEntry = entries.get("course.json");
                if (manifestEntry == null) throw new IllegalArgumentException("不是有效的课程包（缺少 course.json）");
                Map<String, Object> manifest;
                try (InputStream in = zf.getInputStream(manifestEntry)) {
                    manifest = json.readValue(readLimited(in, 20 * 1024 * 1024), new TypeReference<LinkedHashMap<String, Object>>() { });
                }
                if (!FORMAT.equals(manifest.get("format"))) throw new IllegalArgumentException("不是有效的课程包");
                Map<String, Object> course = manifest.get("course") instanceof Map ? (Map<String, Object>) manifest.get("course") : new LinkedHashMap<>();
                String cname = str(course.get("name"), 100);
                if (cname == null || cname.trim().isEmpty()) cname = "导入的课程";
                Long tid = termId != null ? termId : jdbc.queryForList("SELECT id FROM teaching_term WHERE is_current", p(), Long.class).stream().findFirst().orElse(null);
                Long courseId = jdbc.queryForObject("INSERT INTO course(teacher_id, term_id, name, category, description, shared) "
                        + "VALUES (:t, :term, :n, :c, :d, FALSE) RETURNING id",
                        p("t", teacherId, "term", tid, "n", cname.trim(), "c", str(course.get("category"), 50), "d", str(course.get("description"), 2000)), Long.class);
                List<String> warnings = new ArrayList<>();
                Map<String, String> pkgMap = new LinkedHashMap<>();
                List<Object> pages = manifest.get("pages") instanceof List ? (List<Object>) manifest.get("pages") : new ArrayList<>();
                if (pages.size() > MAX_PAGES) throw new IllegalArgumentException("课程页面过多（超过 " + MAX_PAGES + " 页）");
                int seq = 0;
                for (Object o : pages) {
                    if (!(o instanceof Map)) continue;
                    Map<String, Object> pg = (Map<String, Object>) o;
                    String type = String.valueOf(pg.get("pageType"));
                    String title = str(pg.get("title"), 128);
                    if (title == null || title.trim().isEmpty()) title = "页面";
                    if (!CourseService.PAGE_TYPES.contains(type)) {
                        warnings.add("《" + title + "》的页面类型 " + type + " 本平台不支持，已跳过");
                        continue;
                    }
                    seq++;
                    Long ref = null;
                    if (pg.get("refId") instanceof Number && ("QUIZ".equals(type) || "PYTHON".equals(type))) {
                        String table = "QUIZ".equals(type) ? "paper" : "py_problem";
                        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = :id AND (teacher_id = :t OR shared)",
                                p("id", ((Number) pg.get("refId")).longValue(), "t", teacherId), Long.class);
                        if (n != null && n > 0) ref = ((Number) pg.get("refId")).longValue();
                        else warnings.add("《" + title + "》关联的" + ("QUIZ".equals(type) ? "试卷" : "Python 题目")
                                + (pg.get("refTitle") == null ? "" : "「" + pg.get("refTitle") + "」") + "在本平台不可用，请重新选择");
                    }
                    String filePath = null;
                    String fileName = null;
                    Object fz = pg.get("file");
                    if (fz != null && entries.containsKey(String.valueOf(fz)) && String.valueOf(fz).startsWith("files/")) {
                        ZipEntry fe = entries.get(String.valueOf(fz));
                        fileName = str(pg.get("fileName"), 255);
                        if (fileName == null) fileName = String.valueOf(fz).substring(String.valueOf(fz).lastIndexOf('/') + 1);
                        try (InputStream in = zf.getInputStream(fe)) {
                            StoredFile sf = storage.store(in, fe.getSize(), "course", fileName);
                            filePath = sf.getRelPath();
                        }
                    }
                    String config = null;
                    if (pg.get("config") instanceof Map) {
                        Map<String, Object> cfg = new LinkedHashMap<>((Map<String, Object>) pg.get("config"));
                        Object pkg = cfg.get("package");
                        if (pkg != null) {
                            String old = String.valueOf(pkg);
                            String neu = pkgMap.get(old);
                            if (neu == null && CoursewareService.PACKAGE_ID.matcher(old).matches()) {
                                neu = UUID.randomUUID().toString().replace("-", "");
                                Path dir = courseware.base().resolve(neu);
                                createdDirs.add(dir);
                                int n = extractPrefix(zf, entries, "courseware/" + old + "/", dir);
                                if (n == 0) neu = null;
                                else pkgMap.put(old, neu);
                            }
                            if (neu == null) {
                                cfg.remove("package");
                                warnings.add("《" + title + "》的课件包内容缺失，请重新上传");
                            } else {
                                cfg.put("package", neu);
                            }
                        }
                        config = json.writeValueAsString(cfg);
                    }
                    Object score = pg.get("score");
                    Long pageId = jdbc.queryForObject("INSERT INTO course_page(course_id, seq, title, page_type, content, link_url, file_path, file_name, "
                                    + "student_visible, score, ref_id, config) VALUES (:c, :s, :t, :ty, :ct, :l, :fp, :fn, :v, :sc, :r, :cfg) RETURNING id",
                            p("c", courseId, "s", seq, "t", title, "ty", type, "ct", pg.get("content") == null ? null : String.valueOf(pg.get("content")),
                                    "l", str(pg.get("linkUrl"), 500), "fp", filePath, "fn", fileName, "v", !Boolean.FALSE.equals(pg.get("studentVisible")),
                                    "sc", score instanceof Number ? Math.max(0, Math.min(1000, ((Number) score).intValue())) : 0, "r", ref, "cfg", config), Long.class);
                    Map<String, Object> ext = pg.get("extensions") instanceof Map ? (Map<String, Object>) pg.get("extensions") : null;
                    if (ext != null) {
                        for (PageExtension e : courses.pageExtensions()) {
                            if (ext.get(e.key()) instanceof Map) e.importInto(pageId, teacherId, (Map<String, Object>) ext.get(e.key()));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("course", courses.get(courseId));
                out.put("pages", seq);
                out.put("warnings", warnings);
                createdDirs.clear();
                return out;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("课程包读取失败：" + e.getMessage());
        } finally {
            for (Path d : createdDirs) deleteDir(d);   // 导入失败时清理已解压的课件
            if (tmp != null && !tmp.delete()) tmp.deleteOnExit();
        }
    }

    private int extractPrefix(ZipFile zf, Map<String, ZipEntry> entries, String prefix, Path dir) throws IOException {
        int n = 0;
        Path base = dir.toAbsolutePath().normalize();
        for (Map.Entry<String, ZipEntry> e : entries.entrySet()) {
            if (!e.getKey().startsWith(prefix) || e.getValue().isDirectory()) continue;
            String rel = e.getKey().substring(prefix.length());
            if (rel.isEmpty() || rel.equals(CoursewareService.TRUSTED_MARK)) continue;
            Path target = base.resolve(rel).normalize();
            if (!target.startsWith(base)) throw new IllegalArgumentException("课程包中包含非法路径：" + e.getKey());
            Files.createDirectories(target.getParent());
            try (InputStream in = zf.getInputStream(e.getValue())) {
                Files.copy(in, target);
            }
            n++;
        }
        return n;
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            bos.write(buf, 0, r);
            if (bos.size() > max) throw new IllegalArgumentException("course.json 过大");
        }
        return bos.toByteArray();
    }

    private static String str(Object o, int max) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void deleteDir(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }
}
