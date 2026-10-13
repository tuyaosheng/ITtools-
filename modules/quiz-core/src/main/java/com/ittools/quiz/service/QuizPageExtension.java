package com.ittools.quiz.service;

import com.ittools.classroom.service.PageExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/** 课堂测验页面的考试方式、限时、是否公布答案。 */
@Component
public class QuizPageExtension implements PageExtension {
    private final NamedParameterJdbcTemplate jdbc;

    public QuizPageExtension(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String key() {
        return "quiz";
    }

    @Override
    public void copy(long fromPage, long toPage) {
        jdbc.update("INSERT INTO quiz_page_config(page_id, mode, time_limit, show_answer) "
                + "SELECT :to, mode, time_limit, show_answer FROM quiz_page_config WHERE page_id = :from", p("from", fromPage, "to", toPage));
    }

    @Override
    public Map<String, Object> export(long pageId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT mode, time_limit AS \"timeLimit\", show_answer AS \"showAnswer\" "
                + "FROM quiz_page_config WHERE page_id = :p", p("p", pageId));
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    public void importInto(long pageId, long teacherId, Map<String, Object> data) {
        Object mode = data.get("mode");
        if (!Arrays.asList("CONTROL", "AUTO", "INSTANT").contains(mode)) return;
        Object tl = data.get("timeLimit");
        Integer limit = tl instanceof Number && ((Number) tl).intValue() > 0 ? Math.min(300, ((Number) tl).intValue()) : null;
        jdbc.update("INSERT INTO quiz_page_config(page_id, mode, time_limit, show_answer) VALUES (:p, :m, :t, :s)",
                p("p", pageId, "m", mode, "t", limit, "s", !Boolean.FALSE.equals(data.get("showAnswer"))));
    }
}
