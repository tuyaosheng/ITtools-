package com.ittools.resource.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.classroom.service.ClassBindingService;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.ittools.platform.db.Sql.p;

/**
 * 指法练习（原 st_zhifa / paiming）、教师管理学生网盘（原 teacher/webdisc）、教案打包与导入（原 Lessonplans 打包/导入教案）。
 */
@Service
public class ResourceToolsService {
    public static final List<String> TYPING_LEVELS = Arrays.asList("PRIMARY", "MIDDLE", "HIGH", "KEYS");

    private final NamedParameterJdbcTemplate jdbc;
    private final ClassBindingService bindings;
    private final FileStorageService storage;
    private final ObjectMapper json = new ObjectMapper();

    public ResourceToolsService(NamedParameterJdbcTemplate jdbc, ClassBindingService bindings, FileStorageService storage) {
        this.jdbc = jdbc;
        this.bindings = bindings;
        this.storage = storage;
    }

    // ================= 指法练习 =================

    /** 保存一次练习成绩。速度 = 每分钟正确字符数；明显不合理的数据拒绝（防止刷榜）。 */
    @Transactional
    public Map<String, Object> saveTyping(Long studentId, Map<String, Object> b) {
        String level = String.valueOf(b.get("level"));
        if (!TYPING_LEVELS.contains(level)) throw new IllegalArgumentException("练习级别无效");
        int duration = num(b.get("durationSec")), correctChars = num(b.get("correctChars")), typedChars = num(b.get("typedChars")), words = num(b.get("words"));
        if (duration < 30 || duration > 1800) throw new IllegalArgumentException("练习时间应在 30 秒到 30 分钟之间");
        if (typedChars <= 0 || correctChars < 0 || correctChars > typedChars) throw new IllegalArgumentException("成绩数据无效");
        int speed = (int) Math.round(correctChars * 60.0 / duration);
        if (speed > 900) throw new IllegalArgumentException("成绩数据无效");
        double accuracy = Math.round(correctChars * 1000.0 / typedChars) / 10.0;
        jdbc.update("INSERT INTO typing_record(student_id, level, speed, accuracy, words, duration_sec) VALUES (:s, :l, :sp, :a, :w, :d)",
                p("s", studentId, "l", level, "sp", speed, "a", accuracy, "w", Math.max(0, words), "d", duration));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("speed", speed);
        out.put("accuracy", accuracy);
        out.put("best", jdbc.queryForObject("SELECT MAX(speed) FROM typing_record WHERE student_id = :s AND level = :l AND accuracy >= 90",
                p("s", studentId, "l", level), Integer.class));
        out.put("rank", rankOf(studentId, level));
        return out;
    }

    private Integer rankOf(Long studentId, String level) {
        List<Integer> l = jdbc.queryForList("SELECT rk FROM (SELECT student_id, RANK() OVER (ORDER BY MAX(speed) DESC) AS rk FROM typing_record "
                + "WHERE level = :l AND accuracy >= 90 GROUP BY student_id) x WHERE student_id = :s", p("l", level, "s", studentId), Integer.class);
        return l.isEmpty() ? null : l.get(0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myTyping(Long studentId) {
        return jdbc.queryForList("SELECT level, speed, accuracy, words, duration_sec AS \"durationSec\", created_at AS \"createdAt\" FROM typing_record "
                + "WHERE student_id = :s ORDER BY id DESC LIMIT 50", p("s", studentId));
    }

    /** 全校指法英雄榜：每人该级别最好速度（正确率 ≥ 90%），可按年级/班级筛选。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> leaderboard(String level, Long yearId, Long classId) {
        if (!TYPING_LEVELS.contains(level)) throw new IllegalArgumentException("练习级别无效");
        return jdbc.queryForList("SELECT RANK() OVER (ORDER BY b.speed DESC) AS rank, u.id AS \"studentId\", u.name, y.label AS \"yearLabel\", "
                + "k.name AS \"className\", b.speed, b.at AS \"date\" FROM (SELECT student_id, MAX(speed) AS speed, MAX(created_at) AS at FROM typing_record "
                + "WHERE level = :l AND accuracy >= 90 GROUP BY student_id) b JOIN users u ON u.id = b.student_id LEFT JOIN klass k ON k.id = u.class_id "
                + "LEFT JOIN school_year y ON y.id = k.school_year_id WHERE NOT u.graduated "
                + (classId != null ? "AND u.class_id = :k " : "") + (yearId != null ? "AND k.school_year_id = :y " : "")
                + "ORDER BY b.speed DESC LIMIT 50", p("l", level, "k", classId, "y", yearId));
    }

    private static int num(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    // ================= 教师管理学生网盘 =================

    private Long requireStudentOfTeacher(Long teacherId, Long studentId) {
        Long k = jdbc.queryForList("SELECT class_id FROM users WHERE id = :u AND role = 'STUDENT'", p("u", studentId), Long.class)
                .stream().findFirst().orElse(null);
        if (k == null || !bindings.teacherCanUseClass(teacherId, k)) throw new IllegalArgumentException("无权管理该学生的网盘");
        return k;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> classDisks(Long teacherId, Long classId) {
        if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权查看该班级");
        return jdbc.queryForList("SELECT u.id AS \"studentId\", u.student_no AS \"studentNo\", u.name, "
                + "(SELECT COUNT(*) FROM disk_file d WHERE d.owner_id = u.id) AS files, "
                + "(SELECT COALESCE(SUM(file_size), 0) FROM disk_file d WHERE d.owner_id = u.id) AS used, "
                + "COALESCE(dp.disabled, FALSE) AS disabled, COALESCE(dp.penalty_bytes, 0) AS \"penaltyBytes\" "
                + "FROM users u LEFT JOIN disk_policy dp ON dp.user_id = u.id WHERE u.class_id = :k AND u.role = 'STUDENT' AND NOT u.graduated "
                + "ORDER BY u.student_no NULLS LAST, u.name", p("k", classId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentFiles(Long teacherId, Long studentId) {
        requireStudentOfTeacher(teacherId, studentId);
        return jdbc.queryForList("SELECT id, title, note, file_name AS \"fileName\", file_size AS \"fileSize\", created_at AS \"createdAt\" "
                + "FROM disk_file WHERE owner_id = :o ORDER BY id DESC", p("o", studentId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentFile(Long teacherId, Long studentId, Long fileId) {
        requireStudentOfTeacher(teacherId, studentId);
        List<Map<String, Object>> l = jdbc.queryForList("SELECT file_path, file_name FROM disk_file WHERE id = :id AND owner_id = :o", p("id", fileId, "o", studentId));
        if (l.isEmpty()) throw new IllegalArgumentException("文件不存在");
        return l.get(0);
    }

    /** 删除学生网盘中与学习无关的文件；penalty=true 时该文件大小计入扣除空间（原“删除时扣除网盘空间”作为惩罚）。 */
    @Transactional
    public void deleteStudentFile(Long teacherId, Long studentId, Long fileId, boolean penalty) {
        requireStudentOfTeacher(teacherId, studentId);
        List<Map<String, Object>> l = jdbc.queryForList("SELECT file_path, file_size FROM disk_file WHERE id = :id AND owner_id = :o", p("id", fileId, "o", studentId));
        if (l.isEmpty()) throw new IllegalArgumentException("文件不存在");
        jdbc.update("DELETE FROM disk_file WHERE id = :id", p("id", fileId));
        if (penalty) {
            jdbc.update("INSERT INTO disk_policy(user_id, penalty_bytes) VALUES (:u, :b) ON CONFLICT (user_id) DO UPDATE SET penalty_bytes = disk_policy.penalty_bytes + :b",
                    p("u", studentId, "b", ((Number) l.get(0).get("file_size")).longValue()));
        }
        storage.delete((String) l.get(0).get("file_path"));
    }

    /** 单独关闭/开启某学生网盘，或清除扣除的空间。 */
    @Transactional
    public void setPolicy(Long teacherId, Long studentId, Boolean disabled, boolean resetPenalty) {
        requireStudentOfTeacher(teacherId, studentId);
        jdbc.update("INSERT INTO disk_policy(user_id) VALUES (:u) ON CONFLICT (user_id) DO NOTHING", p("u", studentId));
        if (disabled != null) jdbc.update("UPDATE disk_policy SET disabled = :d WHERE user_id = :u", p("d", disabled, "u", studentId));
        if (resetPenalty) jdbc.update("UPDATE disk_policy SET penalty_bytes = 0 WHERE user_id = :u", p("u", studentId));
    }

    // ================= 教案打包 / 导入 =================

    /** 教案包：plan.json（标题、内容）+ 教学设计文档 + 课件。 */
    @Transactional(readOnly = true)
    public Map<String, Object> planPackInfo(Long teacherId, Long planId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT teacher_id, shared, title, content, doc_path, doc_name, ppt_path, ppt_name FROM lesson_plan WHERE id = :id",
                p("id", planId));
        if (l.isEmpty()) throw new IllegalArgumentException("教案不存在");
        Map<String, Object> r = l.get(0);
        if (!teacherId.equals(((Number) r.get("teacher_id")).longValue()) && !Boolean.TRUE.equals(r.get("shared"))) throw new IllegalArgumentException("无权导出该教案");
        return r;
    }

    public void writePlanPack(Map<String, Object> r, OutputStream out) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("format", "ittools-lesson-plan");
        meta.put("version", 1);
        meta.put("title", r.get("title"));
        meta.put("content", r.get("content"));
        ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8);
        for (String kind : new String[]{"doc", "ppt"}) {
            String path = (String) r.get(kind + "_path");
            if (path == null || !Files.isRegularFile(storage.resolve(path))) continue;
            String entry = kind + "/" + r.get(kind + "_name");
            meta.put(kind, entry);
            z.putNextEntry(new ZipEntry(entry));
            Files.copy(storage.resolve(path), z);
            z.closeEntry();
        }
        z.putNextEntry(new ZipEntry("plan.json"));
        z.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
        z.closeEntry();
        z.finish();
    }

    @Transactional
    public Long importPlanPack(Long teacherId, MultipartFile file, Long termId) throws IOException {
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".zip")) throw new IllegalArgumentException("请选择教案包（.zip）");
        File tmp = File.createTempFile("plan", ".zip");
        try {
            file.transferTo(tmp);
            try (ZipFile zf = new ZipFile(tmp, StandardCharsets.UTF_8)) {
                ZipEntry m = zf.getEntry("plan.json");
                if (m == null) throw new IllegalArgumentException("不是有效的教案包（缺少 plan.json）");
                Map<String, Object> meta;
                try (InputStream in = zf.getInputStream(m)) {
                    meta = json.readValue(readLimited(in, 5 * 1024 * 1024), new TypeReference<LinkedHashMap<String, Object>>() { });
                }
                if (!"ittools-lesson-plan".equals(meta.get("format"))) throw new IllegalArgumentException("不是有效的教案包");
                String title = meta.get("title") == null ? "导入的教案" : String.valueOf(meta.get("title"));
                if (title.length() > 200) title = title.substring(0, 200);
                String[] doc = store(zf, meta.get("doc")), ppt = store(zf, meta.get("ppt"));
                return jdbc.queryForObject("INSERT INTO lesson_plan(teacher_id, title, term_id, content, doc_path, doc_name, ppt_path, ppt_name) "
                                + "VALUES (:t, :ti, :term, :c, :dp, :dn, :pp, :pn) RETURNING id",
                        p("t", teacherId, "ti", title, "term", termId, "c", meta.get("content") == null ? null : String.valueOf(meta.get("content")),
                                "dp", doc[0], "dn", doc[1], "pp", ppt[0], "pn", ppt[1]), Long.class);
            }
        } finally {
            if (!tmp.delete()) tmp.deleteOnExit();
        }
    }

    private String[] store(ZipFile zf, Object entryName) throws IOException {
        if (entryName == null) return new String[]{null, null};
        String n = String.valueOf(entryName);
        ZipEntry e = zf.getEntry(n);
        if (e == null || e.isDirectory()) return new String[]{null, null};
        String name = n.substring(n.lastIndexOf('/') + 1);
        try (InputStream in = zf.getInputStream(e)) {
            StoredFile sf = storage.store(in, e.getSize(), "plan", name);
            return new String[]{sf.getRelPath(), sf.getOriginalName()};
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            bos.write(buf, 0, r);
            if (bos.size() > max) throw new IllegalArgumentException("plan.json 过大");
        }
        return bos.toByteArray();
    }
}
