package com.ittools.lab.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ittools.platform.db.Sql.p;

/**
 * 机房模型（原 it_ipclassname）与座位布局：每个模型是一个 rows x cols 网格，
 * 每个格子可放置一台机位（lab_workstation）。
 */
@Service
public class LabRoomService {
    private final NamedParameterJdbcTemplate jdbc;

    public LabRoomService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT r.id, r.name, r.rows_count AS \"rows\", r.cols_count AS \"cols\", "
                        + "(SELECT COUNT(*) FROM lab_seat s WHERE s.room_id = r.id AND s.workstation_id IS NOT NULL) AS \"assigned\" "
                        + "FROM lab_room r ORDER BY r.id", p());
    }

    @Transactional
    public Map<String, Object> create(String name, int rows, int cols) {
        validate(name, rows, cols);
        if (exists("SELECT COUNT(*) FROM lab_room WHERE name = :n", p("n", name))) {
            throw new IllegalArgumentException("机房模型名称已存在");
        }
        Long id = jdbc.queryForObject("INSERT INTO lab_room(name, rows_count, cols_count) VALUES (:n, :r, :c) RETURNING id",
                p("n", name, "r", rows, "c", cols), Long.class);
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(Long id, String name, int rows, int cols) {
        validate(name, rows, cols);
        get(id);
        if (exists("SELECT COUNT(*) FROM lab_room WHERE name = :n AND id <> :id", p("n", name, "id", id))) {
            throw new IllegalArgumentException("机房模型名称已存在");
        }
        jdbc.update("UPDATE lab_room SET name = :n, rows_count = :r, cols_count = :c WHERE id = :id",
                p("n", name, "r", rows, "c", cols, "id", id));
        // 缩小网格时删除越界格子
        jdbc.update("DELETE FROM lab_seat WHERE room_id = :id AND (row_no > :r OR col_no > :c)", p("id", id, "r", rows, "c", cols));
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        jdbc.update("DELETE FROM lab_room WHERE id = :id", p("id", id));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList(
                "SELECT id, name, rows_count AS \"rows\", cols_count AS \"cols\" FROM lab_room WHERE id = :id", p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("机房模型不存在");
        return l.get(0);
    }

    /** 布局：所有已放置机位的格子。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> layout(Long roomId) {
        get(roomId);
        return jdbc.queryForList(
                "SELECT s.id AS \"seatId\", s.row_no AS \"row\", s.col_no AS \"col\", s.workstation_id AS \"workstationId\", "
                        + "w.pc_name AS \"pcName\", w.ip_address AS \"ipAddress\" "
                        + "FROM lab_seat s LEFT JOIN lab_workstation w ON w.id = s.workstation_id "
                        + "WHERE s.room_id = :r ORDER BY s.row_no, s.col_no", p("r", roomId));
    }

    /**
     * 保存布局（整体替换）：cells = [{row, col, workstationId|null}]。
     * 保留原有格子 id（学生座位绑定引用格子），仅更新其机位。
     */
    @Transactional
    public List<Map<String, Object>> saveLayout(Long roomId, List<Map<String, Object>> cells) {
        Map<String, Object> room = get(roomId);
        int rows = ((Number) room.get("rows")).intValue();
        int cols = ((Number) room.get("cols")).intValue();
        Set<Long> used = new HashSet<>();
        Set<String> pos = new HashSet<>();
        for (Map<String, Object> c : cells) {
            int r = ((Number) c.get("row")).intValue();
            int col = ((Number) c.get("col")).intValue();
            if (r < 1 || r > rows || col < 1 || col > cols) {
                throw new IllegalArgumentException("座位超出机房范围：" + r + "行" + col + "列");
            }
            if (!pos.add(r + ":" + col)) {
                throw new IllegalArgumentException("座位重复：" + r + "行" + col + "列");
            }
            Object ws = c.get("workstationId");
            if (ws != null && !used.add(((Number) ws).longValue())) {
                throw new IllegalArgumentException("同一台机位不能放在两个座位");
            }
        }
        // 先清空所有格子的机位，避免唯一约束在逐条更新时冲突
        jdbc.update("UPDATE lab_seat SET workstation_id = NULL WHERE room_id = :r", p("r", roomId));
        for (Map<String, Object> c : cells) {
            int r = ((Number) c.get("row")).intValue();
            int col = ((Number) c.get("col")).intValue();
            Object ws = c.get("workstationId");
            Long wsId = ws == null ? null : ((Number) ws).longValue();
            int n = jdbc.update("UPDATE lab_seat SET workstation_id = :w WHERE room_id = :r AND row_no = :row AND col_no = :col",
                    p("w", wsId, "r", roomId, "row", r, "col", col));
            if (n == 0) {
                jdbc.update("INSERT INTO lab_seat(room_id, row_no, col_no, workstation_id) VALUES (:r, :row, :col, :w)",
                        p("r", roomId, "row", r, "col", col, "w", wsId));
            }
        }
        return layout(roomId);
    }

    /** 按计算机名顺序依次填满网格（从第 1 行第 1 列开始逐行）。 */
    @Transactional
    public List<Map<String, Object>> autoFill(Long roomId) {
        Map<String, Object> room = get(roomId);
        int rows = ((Number) room.get("rows")).intValue();
        int cols = ((Number) room.get("cols")).intValue();
        List<Long> ws = jdbc.queryForList("SELECT id FROM lab_workstation ORDER BY pc_name", p(), Long.class);
        List<Map<String, Object>> cells = new java.util.ArrayList<>();
        int i = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                Map<String, Object> cell = new java.util.HashMap<>();
                cell.put("row", r);
                cell.put("col", c);
                cell.put("workstationId", i < ws.size() ? ws.get(i) : null);
                cells.add(cell);
                i++;
            }
        }
        return saveLayout(roomId, cells);
    }

    private boolean exists(String sql, org.springframework.jdbc.core.namedparam.MapSqlParameterSource ps) {
        Long n = jdbc.queryForObject(sql, ps, Long.class);
        return n != null && n > 0;
    }

    private static void validate(String name, int rows, int cols) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("名称不能为空");
        if (rows < 1 || rows > 30 || cols < 1 || cols > 30) throw new IllegalArgumentException("行列数需在 1-30 之间");
    }
}
