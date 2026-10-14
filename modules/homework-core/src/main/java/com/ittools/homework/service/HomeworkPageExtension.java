package com.ittools.homework.service;

import com.ittools.classroom.service.PageExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/** 作品提交页面的设置（允许的文件类型、互评、自动评分、量规）与自动评分规则。 */
@Component
public class HomeworkPageExtension implements PageExtension {
    private final NamedParameterJdbcTemplate jdbc;

    public HomeworkPageExtension(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String key() {
        return "homework";
    }

    @Override
    public void copy(long fromPage, long toPage) {
        jdbc.update("INSERT INTO homework_config(page_id, allowed_exts, peer_review, show_classmates, auto_grade, rubric_id, auto_visibility, practice) "
                + "SELECT :to, allowed_exts, peer_review, show_classmates, auto_grade, rubric_id, auto_visibility, practice FROM homework_config WHERE page_id = :from",
                p("from", fromPage, "to", toPage));
        jdbc.update("INSERT INTO grading_rule(page_id, seq, rule_type, param1, param2, param3, score, note) "
                + "SELECT :to, seq, rule_type, param1, param2, param3, score, note FROM grading_rule WHERE page_id = :from ORDER BY seq",
                p("from", fromPage, "to", toPage));
    }

    @Override
    public Map<String, Object> export(long pageId) {
        List<Map<String, Object>> cfg = jdbc.queryForList("SELECT allowed_exts AS \"allowedExts\", peer_review AS \"peerReview\", "
                + "show_classmates AS \"showClassmates\", auto_grade AS \"autoGrade\", auto_visibility AS \"autoVisibility\", practice "
                + "FROM homework_config WHERE page_id = :p", p("p", pageId));
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT seq, rule_type AS \"ruleType\", param1, param2, param3, score, note "
                + "FROM grading_rule WHERE page_id = :p ORDER BY seq", p("p", pageId));
        if (cfg.isEmpty() && rules.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        if (!cfg.isEmpty()) out.put("config", cfg.get(0));
        out.put("rules", rules);
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void importInto(long pageId, long teacherId, Map<String, Object> data) {
        Object c = data.get("config");
        if (c instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) c;
            Object vis = m.get("autoVisibility");
            jdbc.update("INSERT INTO homework_config(page_id, allowed_exts, peer_review, show_classmates, auto_grade, auto_visibility, practice) "
                            + "VALUES (:p, :e, :pr, :sc, :ag, :v, :pc)",
                    p("p", pageId, "e", cut(m.get("allowedExts"), 256), "pr", Boolean.TRUE.equals(m.get("peerReview")),
                            "sc", Boolean.TRUE.equals(m.get("showClassmates")), "ag", !Boolean.FALSE.equals(m.get("autoGrade")),
                            "v", "SCORE".equals(vis) || "NONE".equals(vis) ? vis : "ALL", "pc", Boolean.TRUE.equals(m.get("practice"))));
        }
        Object r = data.get("rules");
        if (r instanceof List) {
            int seq = 0;
            for (Object o : new ArrayList<>((List<Object>) r)) {
                if (!(o instanceof Map) || seq >= 100) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                Object type = m.get("ruleType");
                if (type == null || !com.ittools.grading.service.GradingEngine.CATALOG.containsKey(String.valueOf(type))) continue;
                int score = m.get("score") instanceof Number ? Math.max(0, ((Number) m.get("score")).intValue()) : 0;
                jdbc.update("INSERT INTO grading_rule(page_id, seq, rule_type, param1, param2, param3, score, note) VALUES (:p, :s, :t, :a, :b, :c, :sc, :n)",
                        p("p", pageId, "s", ++seq, "t", cut(type, 32), "a", cut(m.get("param1"), 256), "b", cut(m.get("param2"), 256),
                                "c", cut(m.get("param3"), 256), "sc", score, "n", cut(m.get("note"), 128)));
            }
        }
    }

    private static String cut(Object o, int max) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.length() > max ? s.substring(0, max) : s;
    }
}
