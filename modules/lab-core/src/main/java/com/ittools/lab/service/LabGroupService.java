package com.ittools.lab.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 分组模型（原 it_ipgroupname / it_ipgroup）：把机房里的机位划分为若干小组，
 * 开课时教师选择分组模型，学生按所坐机位自动归入小组。
 */
@Service
public class LabGroupService {
    private final NamedParameterJdbcTemplate jdbc;

    public LabGroupService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT g.id, g.name, "
                        + "(SELECT COUNT(DISTINCT group_no) FROM lab_group_member m WHERE m.model_id = g.id) AS \"groupCount\", "
                        + "(SELECT COUNT(*) FROM lab_group_member m WHERE m.model_id = g.id) AS \"memberCount\" "
                        + "FROM lab_group_model g ORDER BY g.id", p());
    }

    @Transactional
    public Map<String, Object> create(String name) {
        requireName(name);
        if (count("SELECT COUNT(*) FROM lab_group_model WHERE name = :n", "n", name, null, null) > 0) {
            throw new IllegalArgumentException("分组模型名称已存在");
        }
        Long id = jdbc.queryForObject("INSERT INTO lab_group_model(name) VALUES (:n) RETURNING id", p("n", name), Long.class);
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    @Transactional
    public void rename(Long id, String name) {
        requireName(name);
        if (count("SELECT COUNT(*) FROM lab_group_model WHERE name = :n AND id <> :id", "n", name, "id", id) > 0) {
            throw new IllegalArgumentException("分组模型名称已存在");
        }
        if (jdbc.update("UPDATE lab_group_model SET name = :n WHERE id = :id", p("n", name, "id", id)) == 0) {
            throw new IllegalArgumentException("分组模型不存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        jdbc.update("DELETE FROM lab_group_model WHERE id = :id", p("id", id));
    }

    /** 所有机位及其在该模型中的组号（未分组为 null）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> members(Long modelId) {
        return jdbc.queryForList(
                "SELECT w.id AS \"workstationId\", w.pc_name AS \"pcName\", w.ip_address AS \"ipAddress\", m.group_no AS \"groupNo\" "
                        + "FROM lab_workstation w LEFT JOIN lab_group_member m ON m.workstation_id = w.id AND m.model_id = :m "
                        + "ORDER BY w.pc_name", p("m", modelId));
    }

    /** 整体保存：assignments = [{workstationId, groupNo|null}]。 */
    @Transactional
    public List<Map<String, Object>> saveMembers(Long modelId, List<Map<String, Object>> assignments) {
        jdbc.update("DELETE FROM lab_group_member WHERE model_id = :m", p("m", modelId));
        for (Map<String, Object> a : assignments) {
            Object g = a.get("groupNo");
            if (g == null || "".equals(g)) continue;
            int groupNo = ((Number) g).intValue();
            if (groupNo < 1) throw new IllegalArgumentException("组号必须 >= 1");
            jdbc.update("INSERT INTO lab_group_member(model_id, workstation_id, group_no) VALUES (:m, :w, :g)",
                    p("m", modelId, "w", ((Number) a.get("workstationId")).longValue(), "g", groupNo));
        }
        return members(modelId);
    }

    /** 自动分组：按计算机名顺序，每 size 台一组。 */
    @Transactional
    public List<Map<String, Object>> autoGroup(Long modelId, int size) {
        if (size < 1) throw new IllegalArgumentException("每组人数至少 1");
        List<Long> ws = jdbc.queryForList("SELECT id FROM lab_workstation ORDER BY pc_name", p(), Long.class);
        jdbc.update("DELETE FROM lab_group_member WHERE model_id = :m", p("m", modelId));
        for (int i = 0; i < ws.size(); i++) {
            jdbc.update("INSERT INTO lab_group_member(model_id, workstation_id, group_no) VALUES (:m, :w, :g)",
                    p("m", modelId, "w", ws.get(i), "g", i / size + 1));
        }
        return members(modelId);
    }

    private long count(String sql, String k1, Object v1, String k2, Object v2) {
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource ps = p(k1, v1);
        if (k2 != null) ps.addValue(k2, v2);
        Long n = jdbc.queryForObject(sql, ps, Long.class);
        return n == null ? 0 : n;
    }

    private static void requireName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("名称不能为空");
    }
}
