package com.ittools.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.platform.service.SpreadsheetReader;
import com.ittools.platform.service.dto.ImportResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 题库（原 editcoursetest* / testlist.xls 导入 / tikujpg）。题型：JUDGE 判断、SINGLE 单选、MULTI 多选、FILL 填空。
 */
@Service
public class QuestionService {
    public static final List<String> TYPES = Arrays.asList("JUDGE", "SINGLE", "MULTI", "FILL");
    private static final Map<String, String> TYPE_LABEL = new HashMap<>();
    static {
        TYPE_LABEL.put("判断题", "JUDGE");
        TYPE_LABEL.put("判断", "JUDGE");
        TYPE_LABEL.put("单选题", "SINGLE");
        TYPE_LABEL.put("单选", "SINGLE");
        TYPE_LABEL.put("多选题", "MULTI");
        TYPE_LABEL.put("多选", "MULTI");
        TYPE_LABEL.put("填空题", "FILL");
        TYPE_LABEL.put("填空", "FILL");
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public QuestionService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static final String COLS = "q.id, q.teacher_id AS \"teacherId\", u.name AS \"teacherName\", q.category, q.qtype, q.stem, "
            + "q.options, q.answer, q.analysis, q.score, q.shared, q.created_at AS \"createdAt\", "
            + "(q.image_path IS NOT NULL) AS \"hasImage\", q.stars, q.needs_answer AS \"needsAnswer\", q.deleted_at AS \"deletedAt\" ";

    /** 可用于组卷/练习的题目：未删除且已设置答案。 */
    public static final String USABLE = "q.deleted_at IS NULL AND NOT q.needs_answer ";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long teacherId, String category, String qtype, String keyword, boolean includeShared) {
        return list(teacherId, category, qtype, keyword, includeShared, null, null, "active");
    }

    /**
     * 题库列表。status：active（默认，未删除）/ draft（待设置答案）/ trash（回收站，仅本人）；
     * tag 按标签筛选；minStars 按星级下限筛选。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long teacherId, String category, String qtype, String keyword, boolean includeShared,
                                          String tag, Integer minStars, String status) {
        StringBuilder sql = new StringBuilder("SELECT " + COLS + "FROM question q JOIN users u ON u.id = q.teacher_id WHERE ");
        boolean trash = "trash".equals(status);
        sql.append(includeShared && !trash ? "(q.teacher_id = :t OR (q.shared AND " + USABLE + ")) " : "q.teacher_id = :t ");
        sql.append(trash ? "AND q.deleted_at IS NOT NULL " : "AND q.deleted_at IS NULL ");
        if ("draft".equals(status)) sql.append("AND q.needs_answer ");
        MapSqlParameterSource ps = p("t", teacherId);
        if (tag != null && !tag.isEmpty()) {
            sql.append("AND EXISTS (SELECT 1 FROM question_tag g WHERE g.question_id = q.id AND g.tag = :tag) ");
            ps.addValue("tag", tag);
        }
        if (minStars != null && minStars > 0) {
            sql.append("AND q.stars >= :st ");
            ps.addValue("st", minStars);
        }
        if (category != null && !category.isEmpty()) {
            sql.append("AND q.category = :c ");
            ps.addValue("c", category);
        }
        if (qtype != null && !qtype.isEmpty()) {
            sql.append("AND q.qtype = :qt ");
            ps.addValue("qt", qtype);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND q.stem ILIKE :k ");
            ps.addValue("k", "%" + keyword + "%");
        }
        sql.append("ORDER BY q.id DESC LIMIT 1000");
        return decorate(jdbc.queryForList(sql.toString(), ps));
    }

    @Transactional(readOnly = true)
    public List<String> categories(Long teacherId) {
        return jdbc.queryForList("SELECT DISTINCT category FROM question WHERE (teacher_id = :t OR shared) AND deleted_at IS NULL AND category IS NOT NULL "
                + "AND category <> '' ORDER BY category", p("t", teacherId), String.class);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + COLS + "FROM question q JOIN users u ON u.id = q.teacher_id WHERE q.id = :id",
                p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("题目不存在");
        return decorate(l).get(0);
    }

    @Transactional
    public Map<String, Object> save(Long teacherId, Long id, Map<String, Object> f) {
        String qtype = (String) f.get("qtype");
        if (!TYPES.contains(qtype)) throw new IllegalArgumentException("无效题型");
        String stem = str(f.get("stem"));
        boolean hasImage = id != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT image_path IS NOT NULL FROM question WHERE id = :id", p("id", id), Boolean.class));
        if ((stem == null || stem.isEmpty()) && !hasImage) throw new IllegalArgumentException("题干不能为空");
        if (stem == null) stem = "";
        int stars = f.get("stars") == null ? 0 : ((Number) f.get("stars")).intValue();
        if (stars < 0 || stars > 5) throw new IllegalArgumentException("星级为 0~5");
        List<String> options = toStrList(f.get("options"));
        String answer = normalizeAnswer(qtype, f.get("answer"), options);
        Object sc = f.get("score");
        MapSqlParameterSource ps = p("t", teacherId, "c", str(f.get("category")), "qt", qtype, "s", stem,
                "o", ("SINGLE".equals(qtype) || "MULTI".equals(qtype)) ? toJson(options) : null, "a", answer,
                "an", str(f.get("analysis")), "sc", sc == null ? 5 : ((Number) sc).intValue(),
                "sh", Boolean.TRUE.equals(f.get("shared")), "st", stars);
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO question(teacher_id, category, qtype, stem, options, answer, analysis, score, shared, stars) "
                    + "VALUES (:t, :c, :qt, :s, :o, :a, :an, :sc, :sh, :st) RETURNING id", ps, Long.class);
        } else {
            requireOwner(teacherId, id);
            ps.addValue("id", id);
            // 保存即视为已设置答案（草稿题入库）
            jdbc.update("UPDATE question SET category = :c, qtype = :qt, stem = :s, options = :o, answer = :a, analysis = :an, "
                    + "score = :sc, shared = :sh, stars = :st, needs_answer = FALSE WHERE id = :id", ps);
        }
        if (f.containsKey("tags")) setTags(id, toStrList(f.get("tags")));
        return get(id);
    }

    @Transactional
    public void delete(Long teacherId, Long id) {
        requireOwner(teacherId, id);
        // 删除进回收站：已组卷/已作答的记录不受影响，回收站里的图片继续参与去重
        jdbc.update("UPDATE question SET deleted_at = now() WHERE id = :id", p("id", id));
    }

    /** 标签整体替换（去空白、去重，单个最长 32 字，最多 10 个）。 */
    public void setTags(Long questionId, List<String> tags) {
        java.util.LinkedHashSet<String> clean = new java.util.LinkedHashSet<>();
        for (String t : tags) {
            String v = t == null ? "" : t.trim();
            if (v.isEmpty()) continue;
            if (v.length() > 32) throw new IllegalArgumentException("标签不超过 32 字");
            clean.add(v);
        }
        if (clean.size() > 10) throw new IllegalArgumentException("每题最多 10 个标签");
        jdbc.update("DELETE FROM question_tag WHERE question_id = :q", p("q", questionId));
        for (String t : clean) jdbc.update("INSERT INTO question_tag(question_id, tag) VALUES (:q, :t)", p("q", questionId, "t", t));
    }

    public void requireOwner(Long teacherId, Long id) {
        List<Long> l = jdbc.queryForList("SELECT teacher_id FROM question WHERE id = :id", p("id", id), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("题目不存在");
        if (!l.get(0).equals(teacherId)) throw new IllegalArgumentException("只能修改自己的题目");
    }

    /**
     * Excel 批量导入（原 testlist.xls）：表头 题型、题干、选项A..选项F、答案、解析、分值、分类。
     * 填空题的答案：多个空用 ## 分隔，每空多个可接受答案用 | 分隔。
     */
    @Transactional
    public ImportResult importFile(Long teacherId, InputStream in, String filename) {
        List<List<String>> rows;
        try {
            rows = new SpreadsheetReader().read(in, filename);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取文件：" + e.getMessage());
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("文件为空");
        List<String> h = rows.get(0);
        int cType = h.indexOf("题型"), cStem = h.indexOf("题干"), cAns = h.indexOf("答案");
        if (cType < 0 || cStem < 0 || cAns < 0) throw new IllegalArgumentException("表头需包含：题型、题干、答案");
        int cAna = h.indexOf("解析"), cScore = h.indexOf("分值"), cCat = h.indexOf("分类");
        ImportResult r = new ImportResult();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String stem = cell(row, cStem);
            if (stem.isEmpty() && cell(row, cType).isEmpty()) continue;
            r.incrementTotal();
            try {
                String qtype = TYPE_LABEL.getOrDefault(cell(row, cType), cell(row, cType).toUpperCase(Locale.ROOT));
                List<String> options = new ArrayList<>();
                for (char c = 'A'; c <= 'F'; c++) {
                    int idx = h.indexOf("选项" + c);
                    if (idx >= 0 && !cell(row, idx).isEmpty()) options.add(cell(row, idx));
                }
                Object answer = cell(row, cAns);
                if ("FILL".equals(qtype)) answer = Arrays.asList(cell(row, cAns).split("##"));
                Map<String, Object> f = new HashMap<>();
                f.put("qtype", qtype);
                f.put("stem", stem);
                f.put("options", options);
                f.put("answer", answer);
                f.put("analysis", cAna < 0 ? null : cell(row, cAna));
                String sc = cScore < 0 ? "" : cell(row, cScore);
                f.put("score", sc.isEmpty() ? 5 : (int) Double.parseDouble(sc));
                f.put("category", cCat < 0 ? null : cell(row, cCat));
                save(teacherId, null, f);
                r.incrementImported();
            } catch (RuntimeException e) {
                r.addError(i, e.getMessage());
            }
        }
        return r;
    }

    public byte[] template() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet s = wb.createSheet("题目");
            String[] head = {"题型", "题干", "选项A", "选项B", "选项C", "选项D", "答案", "解析", "分值", "分类"};
            String[][] ex = {
                    {"单选题", "IP 地址由多少位二进制数组成？", "16", "32", "64", "128", "B", "IPv4 地址为 32 位", "5", "网络基础"},
                    {"多选题", "以下属于输入设备的是？", "键盘", "鼠标", "显示器", "扫描仪", "ABD", "", "5", "硬件"},
                    {"判断题", "CPU 是计算机的核心部件。", "", "", "", "", "对", "", "2", "硬件"},
                    {"填空题", "二进制数 1010 转换为十进制是____。", "", "", "", "", "10", "", "4", "数制"}};
            org.apache.poi.ss.usermodel.Row r0 = s.createRow(0);
            for (int i = 0; i < head.length; i++) r0.createCell(i).setCellValue(head[i]);
            for (int j = 0; j < ex.length; j++) {
                org.apache.poi.ss.usermodel.Row r = s.createRow(j + 1);
                for (int i = 0; i < ex[j].length; i++) r.createCell(i).setCellValue(ex[j][i]);
            }
            s.setColumnWidth(1, 40 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 判分 ----

    /** 客观判分，返回得分（满分 fullScore）。填空题按空比例给分。 */
    public double score(String qtype, String standard, String given, double fullScore) {
        if (given == null || given.trim().isEmpty()) return 0;
        switch (qtype) {
            case "JUDGE":
                return normalizeJudge(given).equals(standard) ? fullScore : 0;
            case "SINGLE":
                return given.trim().toUpperCase(Locale.ROOT).equals(standard) ? fullScore : 0;
            case "MULTI":
                return sortLetters(given).equals(standard) ? fullScore : 0;
            case "FILL": {
                List<String> std = fromJsonList(standard);
                List<String> ans = fromJsonList(given);
                if (std.isEmpty()) return 0;
                int ok = 0;
                for (int i = 0; i < std.size(); i++) {
                    String a = i < ans.size() ? ans.get(i).trim() : "";
                    for (String acc : std.get(i).split("\\|")) {
                        if (!acc.trim().isEmpty() && acc.trim().equalsIgnoreCase(a)) {
                            ok++;
                            break;
                        }
                    }
                }
                return Math.round(fullScore * ok * 100.0 / std.size()) / 100.0;
            }
            default:
                return 0;
        }
    }

    public String normalizeAnswer(String qtype, Object raw, List<String> options) {
        switch (qtype) {
            case "JUDGE": {
                String v = normalizeJudge(raw == null ? "" : String.valueOf(raw));
                if (!"T".equals(v) && !"F".equals(v)) throw new IllegalArgumentException("判断题答案须为 对/错");
                return v;
            }
            case "SINGLE": {
                if (options.size() < 2) throw new IllegalArgumentException("单选题至少需要 2 个选项");
                String v = raw == null ? "" : String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
                if (v.length() != 1 || v.charAt(0) < 'A' || v.charAt(0) >= 'A' + options.size()) {
                    throw new IllegalArgumentException("单选题答案须为选项字母");
                }
                return v;
            }
            case "MULTI": {
                if (options.size() < 2) throw new IllegalArgumentException("多选题至少需要 2 个选项");
                String v = sortLetters(raw == null ? "" : String.valueOf(raw));
                if (v.isEmpty()) throw new IllegalArgumentException("多选题答案不能为空");
                for (char c : v.toCharArray()) {
                    if (c >= 'A' + options.size()) throw new IllegalArgumentException("多选题答案超出选项范围");
                }
                return v;
            }
            case "FILL": {
                List<String> blanks = toStrList(raw);
                if (blanks.isEmpty() && raw instanceof String) blanks = Arrays.asList(((String) raw).split("##"));
                List<String> clean = new ArrayList<>();
                for (String b : blanks) if (!b.trim().isEmpty()) clean.add(b.trim());
                if (clean.isEmpty()) throw new IllegalArgumentException("填空题答案不能为空");
                return toJson(clean);
            }
            default:
                throw new IllegalArgumentException("无效题型");
        }
    }

    private static String normalizeJudge(String v) {
        String s = v.trim().toUpperCase(Locale.ROOT);
        if (Arrays.asList("T", "TRUE", "对", "正确", "√", "Y", "YES", "1").contains(s)) return "T";
        if (Arrays.asList("F", "FALSE", "错", "错误", "×", "X", "N", "NO", "0").contains(s)) return "F";
        return s;
    }

    private static String sortLetters(String v) {
        char[] cs = v.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "").toCharArray();
        Arrays.sort(cs);
        StringBuilder b = new StringBuilder();
        for (char c : cs) if (b.indexOf(String.valueOf(c)) < 0) b.append(c);
        return b.toString();
    }

    // ---- JSON helpers ----

    List<Map<String, Object>> decorate(List<Map<String, Object>> rows) {
        Map<Long, List<String>> tags = new HashMap<>();
        if (!rows.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> r : rows) ids.add(((Number) r.get("id")).longValue());
            for (Map<String, Object> t : jdbc.queryForList("SELECT question_id, tag FROM question_tag WHERE question_id IN (:ids) ORDER BY tag",
                    p("ids", ids))) {
                tags.computeIfAbsent(((Number) t.get("question_id")).longValue(), k -> new ArrayList<>()).add((String) t.get("tag"));
            }
        }
        for (Map<String, Object> r : rows) {
            r.put("options", fromJsonList((String) r.get("options")));
            if ("FILL".equals(r.get("qtype"))) r.put("answer", fromJsonList((String) r.get("answer")));
            r.put("tags", tags.getOrDefault(((Number) r.get("id")).longValue(), new ArrayList<>()));
        }
        return rows;
    }

    public List<String> fromJsonList(String s) {
        if (s == null || s.isEmpty()) return new ArrayList<>();
        try {
            return json.readValue(s, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>(Arrays.asList(s));
        }
    }

    public String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStrList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object x : (List<Object>) o) if (x != null) out.add(String.valueOf(x));
        }
        return out;
    }

    private static String cell(List<String> row, int i) {
        return i < 0 || i >= row.size() ? "" : row.get(i).trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
