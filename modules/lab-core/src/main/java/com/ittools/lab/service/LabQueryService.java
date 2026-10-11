package com.ittools.lab.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 机房模块对其他子项目暴露的只读查询接口（课堂/互动模块通过它获取机位名、座位图、分组）。
 */
@Service
public class LabQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public LabQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 根据客户端 IP 解析机位（原系统学生顶栏"机器名"）。未登记返回 null 名称。 */
    @Transactional(readOnly = true)
    public Map<String, Object> workstationByIp(String ip) {
        Map<String, Object> m = new HashMap<>();
        m.put("ip", ip);
        List<Map<String, Object>> l = jdbc.queryForList(
                "SELECT id, pc_name FROM lab_workstation WHERE ip_address = :ip", p("ip", ip));
        m.put("workstationId", l.isEmpty() ? null : l.get(0).get("id"));
        m.put("pcName", l.isEmpty() ? null : l.get(0).get("pc_name"));
        return m;
    }

    /** 班级所用机房的座位图（含每格机位名/IP），班级未配置机房时返回 null。 */
    @Transactional(readOnly = true)
    public Map<String, Object> seatMapForClass(Long classId) {
        List<Map<String, Object>> rooms = jdbc.queryForList(
                "SELECT r.id, r.name, r.rows_count AS \"rows\", r.cols_count AS \"cols\" "
                        + "FROM lab_class_config c JOIN lab_room r ON r.id = c.room_id WHERE c.class_id = :c", p("c", classId));
        if (rooms.isEmpty()) return null;
        Map<String, Object> room = new HashMap<>(rooms.get(0));
        room.put("seats", jdbc.queryForList(
                "SELECT s.row_no AS \"row\", s.col_no AS \"col\", w.pc_name AS \"pcName\", w.ip_address AS \"ipAddress\" "
                        + "FROM lab_seat s JOIN lab_workstation w ON w.id = s.workstation_id WHERE s.room_id = :r",
                p("r", room.get("id"))));
        return room;
    }

    /** 分组模型下各机位名对应的组号（pcName -> groupNo）。 */
    @Transactional(readOnly = true)
    public Map<String, Integer> groupNoByPcName(Long groupModelId) {
        Map<String, Integer> out = new HashMap<>();
        if (groupModelId == null) return out;
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT w.pc_name, m.group_no FROM lab_group_member m JOIN lab_workstation w ON w.id = m.workstation_id "
                        + "WHERE m.model_id = :m", p("m", groupModelId))) {
            out.put((String) r.get("pc_name"), ((Number) r.get("group_no")).intValue());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> groupModels() {
        return jdbc.queryForList("SELECT id, name FROM lab_group_model ORDER BY id", p());
    }
}
