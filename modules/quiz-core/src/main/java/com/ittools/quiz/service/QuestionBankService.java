package com.ittools.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.ittools.platform.db.Sql.p;

/**
 * 图片式题库（原 tikujpg.aspx）并入现有题库：题干图片、批量传图入库（去重）、标签/星级维护、
 * 回收站、题库统计、题库包导入导出。
 */
@Service
public class QuestionBankService {
    private static final List<String> IMAGE_EXT = Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final int MAX_IMAGE = 5 * 1024 * 1024;
    private static final int MAX_IMAGES_PER_ZIP = 1000;
    private static final String PACK_FORMAT = "ittools-question-pack";

    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionService questions;
    private final FileStorageService storage;
    private final ObjectMapper json = new ObjectMapper();

    public QuestionBankService(NamedParameterJdbcTemplate jdbc, QuestionService questions, FileStorageService storage) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.storage = storage;
    }

    // ================= 题干图片 =================

    /** 学生只能看到本班测验试卷里的题、或练习题库（共享且可用）里的题；教师可看全部。 */
    @Transactional(readOnly = true)
    public Map<String, Object> imageFor(Long userId, String role, Long questionId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT image_path FROM question WHERE id = :id AND image_path IS NOT NULL", p("id", questionId));
        if (l.isEmpty()) throw new IllegalArgumentException("题目没有图片");
        if ("STUDENT".equals(role)) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM question q WHERE q.id = :q AND ("
                    + "(q.shared AND " + QuestionService.USABLE + ") OR EXISTS (SELECT 1 FROM quiz_test t JOIN paper_question pq ON pq.paper_id = t.paper_id "
                    + "JOIN users s ON s.class_id = t.class_id WHERE pq.question_id = q.id AND s.id = :s))", p("q", questionId, "s", userId), Long.class);
            if (n == null || n == 0) throw new IllegalArgumentException("无权查看该题");
        }
        return l.get(0);
    }

    @Transactional
    public Map<String, Object> setImage(Long teacherId, Long questionId, MultipartFile file) throws IOException {
        questions.requireOwner(teacherId, questionId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择图片");
        String ext = FileStorageService.extensionOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXT.contains(ext)) throw new IllegalArgumentException("只支持 jpg/png/gif/webp/bmp 图片");
        if (file.getSize() > MAX_IMAGE) throw new IllegalArgumentException("图片不超过 5MB");
        byte[] data = file.getBytes();
        String old = jdbc.queryForObject("SELECT image_path FROM question WHERE id = :id", p("id", questionId), String.class);
        StoredFile sf = storage.storeBytes(data, "qimg", file.getOriginalFilename());
        jdbc.update("UPDATE question SET image_path = :p, image_hash = :h WHERE id = :id", p("p", sf.getRelPath(), "h", sha256(data), "id", questionId));
        deleteImageIfUnused(old);
        return questions.get(questionId);
    }

    @Transactional
    public Map<String, Object> removeImage(Long teacherId, Long questionId) {
        questions.requireOwner(teacherId, questionId);
        Map<String, Object> q = jdbc.queryForMap("SELECT stem, image_path FROM question WHERE id = :id", p("id", questionId));
        String stem = (String) q.get("stem");
        if (stem == null || stem.trim().isEmpty()) throw new IllegalArgumentException("题干文字为空时不能删除图片，请先填写题干");
        jdbc.update("UPDATE question SET image_path = NULL, image_hash = NULL WHERE id = :id", p("id", questionId));
        deleteImageIfUnused((String) q.get("image_path"));
        return questions.get(questionId);
    }

    private void deleteImageIfUnused(String path) {
        if (path == null) return;
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE image_path = :p", p("p", path), Long.class);
        if (n == null || n == 0) storage.delete(path);
    }

    // ================= 批量传图入库 =================

    /**
     * 压缩包（zip）中的每张图片生成一道“待设置答案”的草稿题（原“压缩包导入”，新上传自动 1 星）。
     * 与本人已有题目（含回收站）图片完全相同的视为重复：不新增，只合并标签。
     */
    @Transactional
    public Map<String, Object> importImages(Long teacherId, MultipartFile zip, String qtype, int optionCount, String category,
                                            List<String> tags, int score) throws IOException {
        if (!QuestionService.TYPES.contains(qtype)) throw new IllegalArgumentException("请选择题型");
        boolean choice = "SINGLE".equals(qtype) || "MULTI".equals(qtype);
        if (choice && (optionCount < 2 || optionCount > 8)) throw new IllegalArgumentException("选项数目须在 2~8 之间");
        if (zip == null || zip.isEmpty()) throw new IllegalArgumentException("请选择压缩包");
        Map<String, byte[]> images = readZip(zip.getBytes(), true);
        int imported = 0, duplicates = 0;
        List<String> names = new ArrayList<>(images.keySet());
        names.sort(QuestionBankService::naturalCompare);
        for (String name : names) {
            byte[] data = images.get(name);
            String hash = sha256(data);
            List<Long> existing = jdbc.queryForList("SELECT id FROM question WHERE teacher_id = :t AND image_hash = :h", p("t", teacherId, "h", hash), Long.class);
            if (!existing.isEmpty()) {
                for (Long id : existing) addTags(id, tags);
                duplicates++;
                continue;
            }
            StoredFile sf = storage.storeBytes(data, "qimg", name);
            List<String> opts = new ArrayList<>(Collections.nCopies(choice ? optionCount : 0, ""));
            Long id = jdbc.queryForObject("INSERT INTO question(teacher_id, category, qtype, stem, options, answer, score, stars, needs_answer, image_path, image_hash) "
                            + "VALUES (:t, :c, :qt, '', :o, '', :sc, 1, TRUE, :ip, :h) RETURNING id",
                    p("t", teacherId, "c", blankToNull(category), "qt", qtype, "o", choice ? questions.toJson(opts) : null,
                            "sc", score <= 0 ? 5 : score, "ip", sf.getRelPath(), "h", hash), Long.class);
            addTags(id, tags);
            imported++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("images", names.size());
        r.put("imported", imported);
        r.put("duplicates", duplicates);
        return r;
    }

    /** 读取 zip 中的文件；onlyImages 时只取图片（跳过目录和 __MACOSX）。Windows 压缩的中文文件名为 GBK，UTF-8 失败时回退。 */
    private Map<String, byte[]> readZip(byte[] bytes, boolean onlyImages) throws IOException {
        try {
            return readZip(bytes, onlyImages, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("压缩包")) throw e;
            return readZip(bytes, onlyImages, Charset.forName("GBK"));
        }
    }

    private Map<String, byte[]> readZip(byte[] bytes, boolean onlyImages, Charset cs) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes), cs)) {
            ZipEntry e;
            boolean any = false;
            while ((e = zin.getNextEntry()) != null) {
                any = true;
                String name = e.getName().replace('\\', '/');
                if (e.isDirectory() || name.startsWith("__MACOSX/") || name.contains("/.")) continue;
                String base = name.substring(name.lastIndexOf('/') + 1);
                if (onlyImages && !IMAGE_EXT.contains(FileStorageService.extensionOf(base).toLowerCase(Locale.ROOT))) continue;
                byte[] data = readLimited(zin, MAX_IMAGE + 1);
                if (onlyImages && data.length > MAX_IMAGE) throw new IllegalArgumentException("压缩包中的图片 " + base + " 超过 5MB");
                out.put(onlyImages ? base : name, data);
                if (out.size() > MAX_IMAGES_PER_ZIP) throw new IllegalArgumentException("压缩包一次最多 " + MAX_IMAGES_PER_ZIP + " 张图片");
            }
            if (!any) throw new IllegalArgumentException("压缩包为空或不是 zip 格式（请用 zip 格式压缩）");
        }
        if (onlyImages && out.isEmpty()) throw new IllegalArgumentException("压缩包中没有图片（支持 jpg/png/gif/webp/bmp）");
        return out;
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            buf.write(b, 0, n);
            if (buf.size() > limit) break;
        }
        return buf.toByteArray();
    }

    private static final Pattern NUM = Pattern.compile("\\d+|\\D+");

    /** 自然排序：2.jpg 排在 10.jpg 前面，保持截图顺序。 */
    static int naturalCompare(String a, String b) {
        Matcher ma = NUM.matcher(a), mb = NUM.matcher(b);
        while (ma.find() && mb.find()) {
            String x = ma.group(), y = mb.group();
            int c;
            if (Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))) {
                c = Long.compare(Long.parseLong(x.length() > 18 ? x.substring(0, 18) : x), Long.parseLong(y.length() > 18 ? y.substring(0, 18) : y));
            } else {
                c = x.compareTo(y);
            }
            if (c != 0) return c;
        }
        return a.length() - b.length();
    }

    // ================= 标签 / 星级 / 批量操作 =================

    private void addTags(Long questionId, List<String> tags) {
        for (String t : tags) {
            String v = t == null ? "" : t.trim();
            if (v.isEmpty() || v.length() > 32) continue;
            jdbc.update("INSERT INTO question_tag(question_id, tag) VALUES (:q, :t) ON CONFLICT DO NOTHING", p("q", questionId, "t", v));
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tags(Long teacherId) {
        return jdbc.queryForList("SELECT g.tag, COUNT(*) AS count FROM question_tag g JOIN question q ON q.id = g.question_id "
                + "WHERE q.teacher_id = :t AND q.deleted_at IS NULL GROUP BY g.tag ORDER BY g.tag", p("t", teacherId));
    }

    /** 标签改名（与已有标签同名时合并）。 */
    @Transactional
    public void renameTag(Long teacherId, String from, String to) {
        String target = to == null ? "" : to.trim();
        if (target.isEmpty() || target.length() > 32) throw new IllegalArgumentException("新标签名为 1~32 字");
        jdbc.update("INSERT INTO question_tag(question_id, tag) SELECT g.question_id, :to FROM question_tag g JOIN question q ON q.id = g.question_id "
                + "WHERE q.teacher_id = :t AND g.tag = :from ON CONFLICT DO NOTHING", p("to", target, "t", teacherId, "from", from));
        deleteTag(teacherId, from);
    }

    @Transactional
    public void deleteTag(Long teacherId, String tag) {
        jdbc.update("DELETE FROM question_tag g USING question q WHERE q.id = g.question_id AND q.teacher_id = :t AND g.tag = :tag",
                p("t", teacherId, "tag", tag));
    }

    /** 批量修改：stars / category / addTags / removeTags / shared，以及 trash=true 移入回收站。只作用于本人的题。 */
    @Transactional
    public int batch(Long teacherId, List<Long> ids, Map<String, Object> f) {
        List<Long> mine = ownIds(teacherId, ids);
        for (Long id : mine) {
            if (f.get("stars") != null) {
                int st = ((Number) f.get("stars")).intValue();
                if (st < 0 || st > 5) throw new IllegalArgumentException("星级为 0~5");
                jdbc.update("UPDATE question SET stars = :s WHERE id = :id", p("s", st, "id", id));
            }
            if (f.containsKey("category")) jdbc.update("UPDATE question SET category = :c WHERE id = :id", p("c", blankToNull(String.valueOf(f.get("category"))), "id", id));
            if (f.get("shared") != null) jdbc.update("UPDATE question SET shared = :s WHERE id = :id", p("s", Boolean.TRUE.equals(f.get("shared")), "id", id));
            if (f.get("addTags") instanceof List) addTags(id, strList(f.get("addTags")));
            if (f.get("removeTags") instanceof List) {
                for (String t : strList(f.get("removeTags"))) jdbc.update("DELETE FROM question_tag WHERE question_id = :q AND tag = :t", p("q", id, "t", t));
            }
            if (Boolean.TRUE.equals(f.get("trash"))) jdbc.update("UPDATE question SET deleted_at = now() WHERE id = :id AND deleted_at IS NULL", p("id", id));
        }
        return mine.size();
    }

    private List<Long> ownIds(Long teacherId, List<Long> ids) {
        if (ids.isEmpty()) throw new IllegalArgumentException("请先勾选题目");
        return jdbc.queryForList("SELECT id FROM question WHERE id IN (:ids) AND teacher_id = :t", p("ids", ids, "t", teacherId), Long.class);
    }

    // ================= 回收站 =================

    @Transactional
    public int restore(Long teacherId, List<Long> ids) {
        return jdbc.update("UPDATE question SET deleted_at = NULL WHERE id IN (:ids) AND teacher_id = :t AND deleted_at IS NOT NULL",
                p("ids", ids, "t", teacherId));
    }

    /** 彻底删除：只删回收站里未被试卷、作答、练习记录引用的题；其余保留在回收站并计入 kept。 */
    @Transactional
    public Map<String, Object> purge(Long teacherId, List<Long> ids) {
        int removed = 0, kept = 0;
        for (Map<String, Object> q : jdbc.queryForList("SELECT id, image_path FROM question WHERE id IN (:ids) AND teacher_id = :t AND deleted_at IS NOT NULL",
                p("ids", ids, "t", teacherId))) {
            Long id = ((Number) q.get("id")).longValue();
            Long refs = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM paper_question WHERE question_id = :q) + (SELECT COUNT(*) FROM quiz_answer WHERE question_id = :q) "
                    + "+ (SELECT COUNT(*) FROM practice_log WHERE question_id = :q)", p("q", id), Long.class);
            if (refs != null && refs > 0) {
                kept++;
                continue;
            }
            jdbc.update("DELETE FROM question WHERE id = :q", p("q", id));
            deleteImageIfUnused((String) q.get("image_path"));
            removed++;
        }
        Map<String, Object> r = new HashMap<>();
        r.put("removed", removed);
        r.put("kept", kept);
        return r;
    }

    // ================= 题库统计 =================

    @Transactional(readOnly = true)
    public Map<String, Object> stats(Long teacherId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", count("teacher_id = :t AND deleted_at IS NULL", teacherId));
        r.put("drafts", count("teacher_id = :t AND deleted_at IS NULL AND needs_answer", teacherId));
        r.put("withImage", count("teacher_id = :t AND deleted_at IS NULL AND image_path IS NOT NULL", teacherId));
        r.put("trash", count("teacher_id = :t AND deleted_at IS NOT NULL", teacherId));
        r.put("shared", count("teacher_id = :t AND deleted_at IS NULL AND shared", teacherId));
        r.put("byType", jdbc.queryForList("SELECT qtype, COUNT(*) AS count FROM question WHERE teacher_id = :t AND deleted_at IS NULL GROUP BY qtype ORDER BY qtype", p("t", teacherId)));
        r.put("byStars", jdbc.queryForList("SELECT stars, COUNT(*) AS count FROM question WHERE teacher_id = :t AND deleted_at IS NULL GROUP BY stars ORDER BY stars", p("t", teacherId)));
        r.put("tags", tags(teacherId));
        return r;
    }

    private long count(String where, Long teacherId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE " + where, p("t", teacherId), Long.class);
        return n == null ? 0 : n;
    }

    // ================= 题库包（原“题库打包 / 题库包导入”） =================

    /** 导出为 zip：manifest.json + images/。可导出本人的题和共享题。 */
    @Transactional(readOnly = true)
    public byte[] exportPack(Long teacherId, List<Long> ids) throws IOException {
        if (ids.isEmpty()) throw new IllegalArgumentException("请先勾选要打包的题目");
        List<Map<String, Object>> rows = questions.decorate(jdbc.queryForList("SELECT " + QuestionService.COLS + ", q.image_path AS \"imagePath\", q.image_hash AS \"imageHash\" "
                + "FROM question q JOIN users u ON u.id = q.teacher_id WHERE q.id IN (:ids) AND (q.teacher_id = :t OR q.shared) AND q.deleted_at IS NULL ORDER BY q.id",
                p("ids", ids, "t", teacherId)));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(buf, StandardCharsets.UTF_8)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> q : rows) {
                Map<String, Object> it = new LinkedHashMap<>();
                for (String k : new String[]{"qtype", "stem", "options", "answer", "analysis", "score", "category", "stars", "tags", "needsAnswer"}) it.put(k, q.get(k));
                String path = (String) q.get("imagePath");
                if (path != null) {
                    String name = "images/" + q.get("imageHash") + "." + FileStorageService.extensionOf(path);
                    it.put("image", name);
                    zout.putNextEntry(new ZipEntry(name));
                    zout.write(Files.readAllBytes(storage.resolve(path)));
                    zout.closeEntry();
                }
                items.add(it);
            }
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", PACK_FORMAT);
            manifest.put("version", 1);
            manifest.put("questions", items);
            zout.putNextEntry(new ZipEntry("manifest.json"));
            zout.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zout.closeEntry();
        }
        return buf.toByteArray();
    }

    /**
     * 导入题库包。去重：有图片按图片内容、无图片按 题型+题干，比对本人已有的题（含回收站）。
     * 重复题只合并标签；overwrite = NONE / ANSWER / ANALYSIS / ALL 决定是否用包里的答案、解析覆盖。
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> importPack(Long teacherId, MultipartFile file, String overwrite) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择题库包");
        Map<String, byte[]> entries = readZip(file.getBytes(), false);
        byte[] mf = entries.get("manifest.json");
        if (mf == null) throw new IllegalArgumentException("不是有效的题库包（缺少 manifest.json）");
        Map<String, Object> manifest = json.readValue(mf, new TypeReference<Map<String, Object>>() {});
        if (!PACK_FORMAT.equals(manifest.get("format"))) throw new IllegalArgumentException("不是 ITtools 题库包");
        boolean ovAnswer = "ANSWER".equals(overwrite) || "ALL".equals(overwrite);
        boolean ovAnalysis = "ANALYSIS".equals(overwrite) || "ALL".equals(overwrite);
        int added = 0, merged = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        int i = 0;
        for (Object o : (List<Object>) manifest.get("questions")) {
            i++;
            Map<String, Object> q = (Map<String, Object>) o;
            try {
                String qtype = (String) q.get("qtype");
                if (!QuestionService.TYPES.contains(qtype)) throw new IllegalArgumentException("题型无效");
                String stem = q.get("stem") == null ? "" : String.valueOf(q.get("stem"));
                List<String> options = strList(q.get("options"));
                boolean draft = Boolean.TRUE.equals(q.get("needsAnswer"));
                byte[] img = q.get("image") == null ? null : entries.get(String.valueOf(q.get("image")));
                if (q.get("image") != null && img == null) throw new IllegalArgumentException("缺少图片 " + q.get("image"));
                if (img == null && stem.trim().isEmpty()) throw new IllegalArgumentException("题干为空");
                String hash = img == null ? null : sha256(img);
                String answer = draft ? "" : questions.normalizeAnswer(qtype, q.get("answer"), options);
                String analysis = q.get("analysis") == null ? null : String.valueOf(q.get("analysis"));
                List<Long> existing = hash != null
                        ? jdbc.queryForList("SELECT id FROM question WHERE teacher_id = :t AND image_hash = :h", p("t", teacherId, "h", hash), Long.class)
                        : jdbc.queryForList("SELECT id FROM question WHERE teacher_id = :t AND image_hash IS NULL AND qtype = :qt AND stem = :s",
                        p("t", teacherId, "qt", qtype, "s", stem), Long.class);
                List<String> tags = strList(q.get("tags"));
                if (!existing.isEmpty()) {
                    for (Long id : existing) {
                        addTags(id, tags);
                        if (ovAnswer && !draft) {
                            jdbc.update("UPDATE question SET qtype = :qt, options = :o, answer = :a, needs_answer = FALSE WHERE id = :id",
                                    p("qt", qtype, "o", choiceJson(qtype, options), "a", answer, "id", id));
                        }
                        if (ovAnalysis && analysis != null) jdbc.update("UPDATE question SET analysis = :an WHERE id = :id", p("an", analysis, "id", id));
                    }
                    merged++;
                    continue;
                }
                String path = null;
                if (img != null) path = storage.storeBytes(img, "qimg", String.valueOf(q.get("image"))).getRelPath();
                int stars = q.get("stars") == null ? 0 : Math.max(0, Math.min(5, ((Number) q.get("stars")).intValue()));
                int score = q.get("score") == null ? 5 : ((Number) q.get("score")).intValue();
                MapSqlParameterSource ps = p("t", teacherId, "c", blankToNull(q.get("category") == null ? null : String.valueOf(q.get("category"))),
                        "qt", qtype, "s", stem, "o", choiceJson(qtype, options), "a", answer, "an", analysis, "sc", score, "st", stars,
                        "na", draft, "ip", path, "h", hash);
                Long id = jdbc.queryForObject("INSERT INTO question(teacher_id, category, qtype, stem, options, answer, analysis, score, stars, needs_answer, image_path, image_hash) "
                        + "VALUES (:t, :c, :qt, :s, :o, :a, :an, :sc, :st, :na, :ip, :h) RETURNING id", ps, Long.class);
                addTags(id, tags);
                added++;
            } catch (RuntimeException e) {
                failed++;
                if (errors.size() < 20) errors.add("第 " + i + " 题：" + e.getMessage());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("added", added);
        r.put("merged", merged);
        r.put("failed", failed);
        r.put("errors", errors);
        return r;
    }

    private String choiceJson(String qtype, List<String> options) {
        return ("SINGLE".equals(qtype) || "MULTI".equals(qtype)) ? questions.toJson(options) : null;
    }

    // ================= 工具 =================

    static String sha256(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() || "null".equals(s) ? null : s.trim();
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) for (Object x : (List<?>) o) if (x != null) out.add(String.valueOf(x));
        return out;
    }
}
