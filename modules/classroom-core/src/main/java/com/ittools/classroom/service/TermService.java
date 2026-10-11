package com.ittools.classroom.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/** 学期维护（原 it_yearlist + it_termlist）。 */
@Service
public class TermService {
    private final NamedParameterJdbcTemplate jdbc;

    public TermService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT id, year_name AS \"yearName\", term_name AS \"termName\", year_name || ' ' || term_name AS \"label\", "
                        + "is_current AS \"current\", display_order AS \"displayOrder\" FROM teaching_term ORDER BY display_order, id", p());
    }

    @Transactional(readOnly = true)
    public Long currentTermId() {
        List<Long> l = jdbc.queryForList("SELECT id FROM teaching_term WHERE is_current", p(), Long.class);
        return l.isEmpty() ? null : l.get(0);
    }

    @Transactional
    public Long create(String yearName, String termName, int order) {
        require(yearName, termName);
        try {
            return jdbc.queryForObject("INSERT INTO teaching_term(year_name, term_name, display_order) VALUES (:y, :t, :o) RETURNING id",
                    p("y", yearName, "t", termName, "o", order), Long.class);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("该学期已存在");
        }
    }

    @Transactional
    public void update(Long id, String yearName, String termName, int order) {
        require(yearName, termName);
        try {
            jdbc.update("UPDATE teaching_term SET year_name = :y, term_name = :t, display_order = :o WHERE id = :id",
                    p("y", yearName, "t", termName, "o", order, "id", id));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("该学期已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        jdbc.update("DELETE FROM teaching_term WHERE id = :id", p("id", id));
    }

    @Transactional
    public void setCurrent(Long id) {
        jdbc.update("UPDATE teaching_term SET is_current = FALSE WHERE is_current", p());
        if (jdbc.update("UPDATE teaching_term SET is_current = TRUE WHERE id = :id", p("id", id)) == 0) {
            throw new IllegalArgumentException("学期不存在");
        }
    }

    private static void require(String y, String t) {
        if (y == null || y.trim().isEmpty() || t == null || t.trim().isEmpty()) {
            throw new IllegalArgumentException("学年和学期名称不能为空");
        }
    }
}
