package com.ittools.grading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 子项目 4 对外服务：评分规则维护、评价量规维护、对作品执行自动评分。
 */
@Service
public class GradingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public GradingService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> catalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String[]> e : GradingEngine.CATALOG.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getKey());
            m.put("name", e.getValue()[0]);
            m.put("param1", e.getValue()[1]);
            m.put("param2", e.getValue()[2]);
            m.put("param3", e.getValue()[3]);
            m.put("exts", e.getValue()[4]);
            out.add(m);
        }
        return out;
    }

    /** 课程页面是否属于该教师（评分规则只能由课程所属教师维护）。 */
    public void requirePageOwner(Long teacherId, Long pageId) {
        List<Long> l = jdbc.queryForList(
                "SELECT c.teacher_id FROM course_page p JOIN course c ON c.id = p.course_id WHERE p.id = :p", p("p", pageId), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("页面不存在");
        if (!l.get(0).equals(teacherId)) throw new IllegalArgumentException("只能修改自己课程的评分规则");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rules(Long pageId) {
        return jdbc.queryForList(
                "SELECT id, seq, rule_type AS \"type\", param1, param2, param3, score, note FROM grading_rule "
                        + "WHERE page_id = :p ORDER BY seq, id", p("p", pageId));
    }

    @Transactional
    public List<Map<String, Object>> saveRules(Long teacherId, Long pageId, List<Map<String, Object>> rules) {
        requirePageOwner(teacherId, pageId);
        jdbc.update("DELETE FROM grading_rule WHERE page_id = :p", p("p", pageId));
        int seq = 1;
        for (Map<String, Object> r : rules) {
            String type = (String) r.get("type");
            if (!GradingEngine.CATALOG.containsKey(type)) throw new IllegalArgumentException("未知规则类型：" + type);
            Object sc = r.get("score");
            int score = sc == null ? 0 : ((Number) sc).intValue();
            if (score < 0) throw new IllegalArgumentException("分值不能为负");
            jdbc.update("INSERT INTO grading_rule(page_id, seq, rule_type, param1, param2, param3, score, note) "
                            + "VALUES (:p, :s, :t, :a, :b, :c, :sc, :n)",
                    p("p", pageId, "s", seq++, "t", type, "a", str(r.get("param1")), "b", str(r.get("param2")),
                            "c", str(r.get("param3")), "sc", score, "n", str(r.get("note"))));
        }
        return rules(pageId);
    }

    /** 对作品执行该页面的全部规则；无规则时返回 null。 */
    @Transactional(readOnly = true)
    public Map<String, Object> evaluate(Long pageId, Path file, String fileName) {
        List<Map<String, Object>> rs = rules(pageId);
        if (rs.isEmpty()) return null;
        List<GradingEngine.Rule> rules = new ArrayList<>();
        for (Map<String, Object> r : rs) {
            rules.add(new GradingEngine.Rule((String) r.get("type"), (String) r.get("param1"), (String) r.get("param2"),
                    (String) r.get("param3"), ((Number) r.get("score")).doubleValue(), (String) r.get("note")));
        }
        GradingEngine.Result res = GradingEngine.grade(file, fileName, rules);
        Map<String, Object> m = new HashMap<>();
        m.put("score", res.score);
        m.put("max", res.max);
        try {
            m.put("detail", json.writeValueAsString(res.details));
        } catch (Exception e) {
            m.put("detail", "[]");
        }
        return m;
    }

    // ---- 阅卷参数读取 / 用标准答案检验评分规则 ----

    static final long MAX_SAMPLE = 30L * 1024 * 1024;

    private static java.nio.file.Path temp(org.springframework.web.multipart.MultipartFile f) throws java.io.IOException {
        if (f.getSize() > MAX_SAMPLE) throw new IllegalArgumentException("文件不能超过 30MB");
        String ext = GradingEngine.ext(f.getOriginalFilename());
        java.nio.file.Path t = java.nio.file.Files.createTempFile("grading", "." + (ext.isEmpty() ? "bin" : ext));
        f.transferTo(t.toFile());
        return t;
    }

    public Map<String, Object> inspect(org.springframework.web.multipart.MultipartFile file) {
        java.nio.file.Path t = null;
        try {
            t = temp(file);
            return OfficeInspector.inspect(t, file.getOriginalFilename());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取该文件：" + e.getMessage());
        } finally {
            if (t != null) t.toFile().delete();
        }
    }

    /** 用教师上传的标准答案（或学生样例）检验评分规则，返回每条规则的检测结果。规则可以是尚未保存的。 */
    public Map<String, Object> testRules(org.springframework.web.multipart.MultipartFile file, List<Map<String, Object>> rules) {
        if (rules == null || rules.isEmpty()) throw new IllegalArgumentException("请先添加评分规则");
        List<GradingEngine.Rule> list = new ArrayList<>();
        for (Map<String, Object> r : rules) {
            String type = (String) r.get("type");
            if (!GradingEngine.CATALOG.containsKey(type)) throw new IllegalArgumentException("未知规则类型：" + type);
            Object sc = r.get("score");
            list.add(new GradingEngine.Rule(type, str(r.get("param1")), str(r.get("param2")), str(r.get("param3")),
                    sc == null ? 0 : ((Number) sc).doubleValue(), str(r.get("note"))));
        }
        java.nio.file.Path t = null;
        try {
            t = temp(file);
            GradingEngine.Result res = GradingEngine.grade(t, file.getOriginalFilename(), list);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("score", res.score);
            m.put("max", res.max);
            m.put("details", res.details);
            return m;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("文件读取失败");
        } finally {
            if (t != null) t.toFile().delete();
        }
    }

    // ---- 评价量规 ----

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rubrics(Long teacherId) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT r.id, r.name, r.shared, r.teacher_id AS \"teacherId\", u.name AS \"teacherName\", "
                        + "(SELECT COALESCE(SUM(max_score), 0) FROM rubric_item i WHERE i.rubric_id = r.id) AS \"total\" "
                        + "FROM rubric r JOIN users u ON u.id = r.teacher_id WHERE r.teacher_id = :t OR r.shared ORDER BY r.id DESC",
                p("t", teacherId));
        for (Map<String, Object> r : list) r.put("items", items(((Number) r.get("id")).longValue()));
        return list;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> items(Long rubricId) {
        return jdbc.queryForList("SELECT id, seq, name, max_score AS \"maxScore\", description FROM rubric_item "
                + "WHERE rubric_id = :r ORDER BY seq, id", p("r", rubricId));
    }

    @Transactional
    public Long saveRubric(Long teacherId, Long id, String name, boolean shared, List<Map<String, Object>> items) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("量规名称不能为空");
        if (items.isEmpty()) throw new IllegalArgumentException("至少需要一个评价项");
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO rubric(teacher_id, name, shared) VALUES (:t, :n, :s) RETURNING id",
                    p("t", teacherId, "n", name.trim(), "s", shared), Long.class);
        } else {
            requireRubricOwner(teacherId, id);
            jdbc.update("UPDATE rubric SET name = :n, shared = :s WHERE id = :id", p("n", name.trim(), "s", shared, "id", id));
            jdbc.update("DELETE FROM rubric_item WHERE rubric_id = :id", p("id", id));
        }
        int seq = 1;
        for (Map<String, Object> it : items) {
            String n = str(it.get("name"));
            if (n == null || n.isEmpty()) throw new IllegalArgumentException("评价项名称不能为空");
            Object ms = it.get("maxScore");
            jdbc.update("INSERT INTO rubric_item(rubric_id, seq, name, max_score, description) VALUES (:r, :s, :n, :m, :d)",
                    p("r", id, "s", seq++, "n", n, "m", ms == null ? 0 : ((Number) ms).intValue(), "d", str(it.get("description"))));
        }
        return id;
    }

    @Transactional
    public void deleteRubric(Long teacherId, Long id) {
        requireRubricOwner(teacherId, id);
        jdbc.update("DELETE FROM rubric WHERE id = :id", p("id", id));
    }

    private void requireRubricOwner(Long teacherId, Long id) {
        List<Long> l = jdbc.queryForList("SELECT teacher_id FROM rubric WHERE id = :id", p("id", id), Long.class);
        if (l.isEmpty()) throw new IllegalArgumentException("量规不存在");
        if (!l.get(0).equals(teacherId)) throw new IllegalArgumentException("只能修改自己的量规");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
