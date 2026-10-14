package com.ittools.platform.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ittools.platform.db.Sql.p;

/**
 * 用表格批量修改学生信息（合并原 studentrename 改名、studentxjh 改学籍号、studentPlacement 重新分班、studentinsc 改入学年份）：
 * 下载某年级的学生信息表 → 在表格中修改 → 上传预览改动 → 确认后一次性写入。以“编号”列对应学生。
 */
@Service
public class BatchEditService {
    static final String[] HEAD = {"编号", "学籍号", "学号", "姓名", "性别", "年级", "班级", "入学年份", "状态"};
    static final int MAX_ROWS = 5000;

    private final NamedParameterJdbcTemplate jdbc;
    private final SpreadsheetReader reader = new SpreadsheetReader();

    public BatchEditService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "SELECT u.id, u.xjh, u.student_no AS \"studentNo\", u.name, u.gender, u.class_id AS \"classId\", "
            + "y.label AS \"yearLabel\", k.name AS \"className\", u.enroll_year_id AS \"enrollYearId\", ey.label AS \"enrollLabel\", u.graduated "
            + "FROM users u LEFT JOIN klass k ON k.id = u.class_id LEFT JOIN school_year y ON y.id = k.school_year_id "
            + "LEFT JOIN school_year ey ON ey.id = u.enroll_year_id WHERE u.role = 'STUDENT' ";

    @Transactional(readOnly = true)
    public byte[] export(Long yearId) throws IOException {
        List<Map<String, Object>> rows = jdbc.queryForList(COLS + "AND (k.school_year_id = :y OR u.enroll_year_id = :y) "
                + "ORDER BY k.display_order, k.name, u.student_no NULLS LAST, u.name", p("y", yearId));
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("学生信息");
            Row h = sh.createRow(0);
            for (int i = 0; i < HEAD.length; i++) h.createCell(i).setCellValue(HEAD[i]);
            int r = 1;
            for (Map<String, Object> m : rows) {
                Row x = sh.createRow(r++);
                x.createCell(0).setCellValue(((Number) m.get("id")).longValue());
                x.createCell(1).setCellValue(s(m.get("xjh")));
                x.createCell(2).setCellValue(s(m.get("studentNo")));
                x.createCell(3).setCellValue(s(m.get("name")));
                x.createCell(4).setCellValue(s(m.get("gender")));
                x.createCell(5).setCellValue(s(m.get("yearLabel")));
                x.createCell(6).setCellValue(s(m.get("className")));
                x.createCell(7).setCellValue(s(m.get("enrollLabel")));
                x.createCell(8).setCellValue(Boolean.TRUE.equals(m.get("graduated")) ? "毕业" : "在读");
            }
            for (int i = 0; i < HEAD.length; i++) sh.setColumnWidth(i, i == 1 ? 6000 : 3200);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 解析表格并与数据库比较，返回改动与错误，不写入。 */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(MultipartFile file) {
        return analyze(file).result;
    }

    @Transactional
    public Map<String, Object> apply(MultipartFile file) {
        Analysis a = analyze(file);
        @SuppressWarnings("unchecked") List<Object> errors = (List<Object>) a.result.get("errors");
        if (!errors.isEmpty()) throw new IllegalArgumentException("表格中有 " + errors.size() + " 处错误，请先修正");
        // 先把要改学籍号的学生临时置空，避免互换学籍号时触发唯一约束
        for (Map<String, Object> u : a.finals.values()) {
            if (u.containsKey("xjhChanged")) jdbc.update("UPDATE users SET xjh = NULL WHERE id = :id", p("id", u.get("id")));
        }
        for (Map<String, Object> u : a.finals.values()) {
            jdbc.update("UPDATE users SET xjh = :xjh, student_no = :no, name = :name, gender = :g, class_id = :k, enroll_year_id = :ey, "
                            + "graduated = :gr, updated_at = now() WHERE id = :id",
                    p("xjh", u.get("xjh"), "no", u.get("studentNo"), "name", u.get("name"), "g", u.get("gender"), "k", u.get("classId"),
                            "ey", u.get("enrollYearId"), "gr", u.get("graduated"), "id", u.get("id")));
        }
        Map<String, Object> out = new LinkedHashMap<>(a.result);
        out.put("applied", a.finals.size());
        return out;
    }

    private static final class Analysis {
        Map<String, Object> result;
        Map<Long, Map<String, Object>> finals = new LinkedHashMap<>();
    }

    private Analysis analyze(MultipartFile file) {
        List<List<String>> rows;
        try (InputStream in = file.getInputStream()) {
            rows = reader.read(in, file.getOriginalFilename());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取表格：" + e.getMessage());
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("表格是空的");
        Map<String, Integer> col = new HashMap<>();
        List<String> head = rows.get(0);
        for (int i = 0; i < head.size(); i++) col.put(head.get(i).trim(), i);
        if (!col.containsKey("编号")) throw new IllegalArgumentException("表格缺少“编号”列，请使用下载的学生信息表修改");
        if (rows.size() - 1 > MAX_ROWS) throw new IllegalArgumentException("一次最多修改 " + MAX_ROWS + " 名学生");

        Map<String, Long> yearByLabel = new HashMap<>();
        for (Map<String, Object> y : jdbc.queryForList("SELECT id, label FROM school_year", p())) yearByLabel.put(s(y.get("label")), ((Number) y.get("id")).longValue());
        Map<String, Long> classByKey = new HashMap<>();
        Map<Long, String> classLabel = new HashMap<>();
        for (Map<String, Object> k : jdbc.queryForList("SELECT k.id, k.name, y.label FROM klass k JOIN school_year y ON y.id = k.school_year_id", p())) {
            classByKey.put(s(k.get("label")) + "|" + s(k.get("name")), ((Number) k.get("id")).longValue());
            classLabel.put(((Number) k.get("id")).longValue(), s(k.get("label")) + s(k.get("name")));
        }

        Analysis a = new Analysis();
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String idText = cell(row, col, "编号");
            if (idText.isEmpty() && String.join("", row).trim().isEmpty()) continue;
            int line = r + 1;
            long id;
            try {
                id = (long) Double.parseDouble(idText);
            } catch (NumberFormatException e) {
                errors.add(err(line, "编号无效：" + idText));
                continue;
            }
            if (!seen.add(id)) {
                errors.add(err(line, "编号 " + id + " 重复出现"));
                continue;
            }
            List<Map<String, Object>> cur = jdbc.queryForList(COLS + "AND u.id = :id", p("id", id));
            if (cur.isEmpty()) {
                errors.add(err(line, "找不到编号为 " + id + " 的学生"));
                continue;
            }
            Map<String, Object> old = cur.get(0);
            Map<String, Object> neu = new LinkedHashMap<>(old);
            List<Map<String, Object>> items = new ArrayList<>();
            if (col.containsKey("姓名")) {
                String v = cell(row, col, "姓名");
                if (v.isEmpty() || v.length() > 64) errors.add(err(line, "姓名不能为空且不超过 64 字"));
                else diff(items, neu, "name", "姓名", old.get("name"), v);
            }
            if (col.containsKey("学籍号")) {
                String v = cell(row, col, "学籍号");
                if (v.length() > 32) errors.add(err(line, "学籍号过长"));
                else if (diff(items, neu, "xjh", "学籍号", old.get("xjh"), v.isEmpty() ? null : v)) neu.put("xjhChanged", true);
            }
            if (col.containsKey("学号")) {
                String v = cell(row, col, "学号");
                if (v.length() > 32) errors.add(err(line, "学号过长"));
                else diff(items, neu, "studentNo", "学号", old.get("studentNo"), v.isEmpty() ? null : v);
            }
            if (col.containsKey("性别")) {
                String v = cell(row, col, "性别");
                if (!v.isEmpty() && !"男".equals(v) && !"女".equals(v)) errors.add(err(line, "性别只能是 男 或 女"));
                else diff(items, neu, "gender", "性别", old.get("gender"), v.isEmpty() ? null : v);
            }
            if (col.containsKey("班级")) {
                String yl = col.containsKey("年级") ? cell(row, col, "年级") : s(old.get("yearLabel"));
                String kn = cell(row, col, "班级");
                if (kn.isEmpty()) {
                    errors.add(err(line, "班级不能为空"));
                } else {
                    Long k = classByKey.get(yl + "|" + kn);
                    if (k == null) errors.add(err(line, "找不到班级：" + yl + " " + kn));
                    else if (!Objects.equals(k, old.get("classId") == null ? null : ((Number) old.get("classId")).longValue())) {
                        neu.put("classId", k);
                        items.add(item("班级", classLabel.get(old.get("classId") == null ? -1L : ((Number) old.get("classId")).longValue()), classLabel.get(k)));
                    }
                }
            }
            if (col.containsKey("入学年份")) {
                String v = cell(row, col, "入学年份");
                Long ey = v.isEmpty() ? null : yearByLabel.get(v);
                if (!v.isEmpty() && ey == null) errors.add(err(line, "找不到入学年份：" + v));
                else if (!Objects.equals(ey, old.get("enrollYearId") == null ? null : ((Number) old.get("enrollYearId")).longValue())) {
                    neu.put("enrollYearId", ey);
                    items.add(item("入学年份", s(old.get("enrollLabel")), v));
                }
            }
            if (col.containsKey("状态")) {
                String v = cell(row, col, "状态");
                if (!v.isEmpty() && !"在读".equals(v) && !"毕业".equals(v)) errors.add(err(line, "状态只能是 在读 或 毕业"));
                else if (!v.isEmpty()) {
                    boolean g = "毕业".equals(v);
                    if (g != Boolean.TRUE.equals(old.get("graduated"))) {
                        neu.put("graduated", g);
                        items.add(item("状态", Boolean.TRUE.equals(old.get("graduated")) ? "毕业" : "在读", v));
                    }
                }
            }
            if (!items.isEmpty()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("row", line);
                c.put("id", id);
                c.put("name", old.get("name"));
                c.put("items", items);
                changes.add(c);
                a.finals.put(id, neu);
            }
        }
        checkUnique(a.finals, errors);
        a.result = new LinkedHashMap<>();
        a.result.put("rows", seen.size());
        a.result.put("changes", changes);
        a.result.put("errors", errors);
        return a;
    }

    /** 最终状态下：学籍号全局不重复；同一班级在读学生学号不重复（原“防止同班同学号”）。 */
    private void checkUnique(Map<Long, Map<String, Object>> finals, List<Map<String, Object>> errors) {
        if (finals.isEmpty()) return;
        List<Long> ids = new ArrayList<>(finals.keySet());
        Map<String, Long> xjhOwner = new HashMap<>();
        for (Map<String, Object> u : finals.values()) {
            Object x = u.get("xjh");
            if (x == null) continue;
            Long prev = xjhOwner.put(String.valueOf(x), (Long) u.get("id"));
            if (prev != null) errors.add(err(0, "学籍号 " + x + " 在表格中重复"));
            List<Long> other = jdbc.queryForList("SELECT id FROM users WHERE xjh = :x AND id NOT IN (:ids)", p("x", x, "ids", ids), Long.class);
            if (!other.isEmpty()) errors.add(err(0, "学籍号 " + x + " 已被其他学生使用"));
        }
        Set<Long> classes = new HashSet<>();
        for (Map<String, Object> u : finals.values()) if (u.get("classId") != null) classes.add(((Number) u.get("classId")).longValue());
        for (Long k : classes) {
            Map<String, String> byNo = new HashMap<>();
            for (Map<String, Object> o : jdbc.queryForList("SELECT student_no, name FROM users WHERE class_id = :k AND role = 'STUDENT' AND NOT graduated "
                    + "AND id NOT IN (:ids) AND student_no IS NOT NULL", p("k", k, "ids", ids))) {
                byNo.put(s(o.get("student_no")), s(o.get("name")));
            }
            for (Map<String, Object> u : finals.values()) {
                if (u.get("classId") == null || ((Number) u.get("classId")).longValue() != k || Boolean.TRUE.equals(u.get("graduated")) || u.get("studentNo") == null) continue;
                String prev = byNo.put(s(u.get("studentNo")), s(u.get("name")));
                if (prev != null) errors.add(err(0, "同一班级中学号 " + u.get("studentNo") + " 重复（" + prev + "、" + u.get("name") + "）"));
            }
        }
    }

    private static boolean diff(List<Map<String, Object>> items, Map<String, Object> neu, String key, String label, Object oldV, String newV) {
        String o = oldV == null ? null : String.valueOf(oldV);
        if (Objects.equals(o, newV)) return false;
        neu.put(key, newV);
        items.add(item(label, o, newV));
        return true;
    }

    private static Map<String, Object> item(String label, Object o, Object n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", label);
        m.put("old", o == null ? "" : o);
        m.put("new", n == null ? "" : n);
        return m;
    }

    private static Map<String, Object> err(int row, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("message", msg);
        return m;
    }

    private static String cell(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i >= row.size() || row.get(i) == null) return "";
        String v = row.get(i).trim();
        return v.endsWith(".0") && v.matches("\\d+\\.0") ? v.substring(0, v.length() - 2) : v;
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
