package com.ittools.lab.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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

    // ================= 机房方案（文本排版 + 规则生成机位） =================

    static final java.util.regex.Pattern IPV4 = java.util.regex.Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    @Transactional(readOnly = true)
    public Map<String, Object> scheme(Long roomId) {
        get(roomId);
        return jdbc.queryForMap("SELECT id, name, rows_count AS \"rows\", cols_count AS \"cols\", prefix, pad, base_ip AS \"baseIp\", "
                + "ip_step AS \"step\", layout_text AS \"layoutText\" FROM lab_room WHERE id = :id", p("id", roomId));
    }

    static long ipToLong(String ip) {
        java.util.regex.Matcher m = IPV4.matcher(ip == null ? "" : ip.trim());
        if (!m.matches()) throw new IllegalArgumentException("IP 格式不对：" + ip);
        long v = 0;
        for (int i = 1; i <= 4; i++) {
            int n = Integer.parseInt(m.group(i));
            if (n > 255) throw new IllegalArgumentException("IP 格式不对：" + ip);
            v = v * 256 + n;
        }
        return v;
    }

    static String longToIp(long v) {
        return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    /** 解析文本排版：一行一排，座位号空格隔开，过道/空位写 . 或 0。返回每行的座位号（0 = 空位）。 */
    static List<List<Integer>> parseLayout(String text) {
        List<List<Integer>> rows = new java.util.ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (String line : (text == null ? "" : text).split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            List<Integer> row = new java.util.ArrayList<>();
            for (String tok : line.trim().split("\\s+")) {
                if (".".equals(tok) || "0".equals(tok)) {
                    row.add(0);
                    continue;
                }
                int n;
                try {
                    n = Integer.parseInt(tok);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("排版里有无法识别的内容：" + tok + "（座位号写数字，过道写 .）");
                }
                if (n < 1 || n > 999) throw new IllegalArgumentException("座位号要在 1~999：" + n);
                if (!seen.add(n)) throw new IllegalArgumentException("座位号 " + n + " 重复了");
                row.add(n);
            }
            if (row.size() > 40) throw new IllegalArgumentException("每排最多 40 个位置");
            rows.add(row);
        }
        if (rows.isEmpty() || seen.isEmpty()) throw new IllegalArgumentException("排版里没有座位");
        if (rows.size() > 40) throw new IllegalArgumentException("最多 40 排");
        return rows;
    }

    /**
     * 保存机房方案：按 前缀+编号 生成计算机名、按 1号IP ± (编号-1) 生成IP，新增或更新机位，并按排版重建座位格子。
     * roomId 为空时新建机房。已存在的同名/同IP机位会被复用（改名或改IP）。
     */
    @Transactional
    public Map<String, Object> saveScheme(Long roomId, Map<String, Object> b) {
        String name = b.get("name") == null ? "" : String.valueOf(b.get("name")).trim();
        String prefix = b.get("prefix") == null ? "" : String.valueOf(b.get("prefix")).trim();
        if (prefix.length() > 12) throw new IllegalArgumentException("座位名前缀最多 12 个字符");
        int pad = b.get("pad") instanceof Number ? ((Number) b.get("pad")).intValue() : 2;
        if (pad < 0 || pad > 3) throw new IllegalArgumentException("编号位数为 0~3");
        int step = b.get("step") instanceof Number ? ((Number) b.get("step")).intValue() : 1;
        if (step != 1 && step != -1) throw new IllegalArgumentException("IP 只能递增或递减");
        String baseIp = b.get("baseIp") == null ? "" : String.valueOf(b.get("baseIp")).trim();
        long base = ipToLong(baseIp);
        String text = b.get("layoutText") == null ? "" : String.valueOf(b.get("layoutText"));
        List<List<Integer>> layout = parseLayout(text);
        int rows = layout.size(), cols = 0;
        for (List<Integer> r : layout) cols = Math.max(cols, r.size());
        validate(name, rows, cols);

        // 目标机位：座位号 → (计算机名, IP)
        Map<Integer, String[]> target = new java.util.LinkedHashMap<>();
        for (List<Integer> r : layout) {
            for (Integer n : r) {
                if (n == 0) continue;
                long ip = base + (long) (n - 1) * step;
                if (ip < 0 || ip > 0xFFFFFFFFL || (ip & 255) == 0 || (ip & 255) == 255) {
                    throw new IllegalArgumentException("座位 " + n + " 算出的 IP 不可用（超出网段或以 .0/.255 结尾），请检查 1 号座位 IP 和递增方向");
                }
                String num = pad == 0 ? String.valueOf(n) : String.format("%0" + pad + "d", n);
                target.put(n, new String[]{prefix + num, longToIp(ip)});
            }
        }

        if (roomId == null) {
            roomId = ((Number) create(name, rows, cols).get("id")).longValue();
        } else {
            update(roomId, name, rows, cols);
        }
        Map<Integer, Long> wsOf = upsertWorkstations(target);
        // 重建格子：保留排版里有座位的格子（学生座位绑定引用格子 id），删除其余
        List<Map<String, Object>> cells = new java.util.ArrayList<>();
        Set<String> keep = new HashSet<>();
        for (int r = 0; r < rows; r++) {
            List<Integer> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                int n = row.get(c);
                if (n == 0) continue;
                Map<String, Object> cell = new java.util.HashMap<>();
                cell.put("row", r + 1);
                cell.put("col", c + 1);
                cell.put("workstationId", wsOf.get(n));
                cells.add(cell);
                keep.add((r + 1) + ":" + (c + 1));
            }
        }
        for (Map<String, Object> s : jdbc.queryForList("SELECT id, row_no, col_no FROM lab_seat WHERE room_id = :r", p("r", roomId))) {
            if (!keep.contains(s.get("row_no") + ":" + s.get("col_no"))) jdbc.update("DELETE FROM lab_seat WHERE id = :id", p("id", s.get("id")));
        }
        saveLayout(roomId, cells);
        jdbc.update("UPDATE lab_room SET prefix = :pf, pad = :pad, base_ip = :ip, ip_step = :st, layout_text = :t WHERE id = :id",
                p("pf", prefix, "pad", pad, "ip", baseIp, "st", step, "t", text.trim(), "id", roomId));
        return scheme(roomId);
    }

    /**
     * 机位名与 IP 都唯一：先把有冲突的机位改成临时值，再逐个复用（同 IP 优先，其次同名）或新增；
     * 没被复用的冲突机位删除（座位引用随之置空）。返回 座位号 → 机位 id。
     */
    private Map<Integer, Long> upsertWorkstations(Map<Integer, String[]> target) {
        List<String> names = new java.util.ArrayList<>(), ips = new java.util.ArrayList<>();
        for (String[] t : target.values()) {
            names.add(t[0]);
            ips.add(t[1]);
        }
        List<Map<String, Object>> conflict = jdbc.queryForList("SELECT id, pc_name, ip_address FROM lab_workstation WHERE pc_name IN (:n) OR ip_address IN (:i)",
                p("n", names, "i", ips));
        Map<String, Long> byIp = new HashMap<>(), byName = new HashMap<>();
        for (Map<String, Object> w : conflict) {
            long id = ((Number) w.get("id")).longValue();
            byIp.put((String) w.get("ip_address"), id);
            byName.put((String) w.get("pc_name"), id);
            jdbc.update("UPDATE lab_workstation SET pc_name = :n, ip_address = :i WHERE id = :id", p("n", "__tmp_" + id, "i", "__tmp_" + id, "id", id));
        }
        Set<Long> used = new HashSet<>();
        Map<Integer, Long> out = new HashMap<>();
        for (Map.Entry<Integer, String[]> e : target.entrySet()) {
            String[] t = e.getValue();
            Long id = byIp.get(t[1]);
            if (id == null || used.contains(id)) id = byName.get(t[0]);
            if (id != null && used.contains(id)) id = null;
            if (id == null) {
                id = jdbc.queryForObject("INSERT INTO lab_workstation(pc_name, ip_address) VALUES (:n, :i) RETURNING id", p("n", t[0], "i", t[1]), Long.class);
            } else {
                jdbc.update("UPDATE lab_workstation SET pc_name = :n, ip_address = :i WHERE id = :id", p("n", t[0], "i", t[1], "id", id));
            }
            used.add(id);
            out.put(e.getKey(), id);
        }
        for (Map<String, Object> w : conflict) {
            long id = ((Number) w.get("id")).longValue();
            if (!used.contains(id)) jdbc.update("DELETE FROM lab_workstation WHERE id = :id", p("id", id));
        }
        return out;
    }

    /** 交换两台机位的 IP（原“交换这两个座位的IP”：两台电脑的网线或 IP 对调时使用）。 */
    @Transactional
    public void swapIp(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) throw new IllegalArgumentException("请选择两台不同的机位");
        List<Map<String, Object>> l = jdbc.queryForList("SELECT id, ip_address FROM lab_workstation WHERE id IN (:a, :b)", p("a", a, "b", b));
        if (l.size() != 2) throw new IllegalArgumentException("机位不存在");
        String ipA = null, ipB = null;
        for (Map<String, Object> w : l) {
            if (((Number) w.get("id")).longValue() == a) ipA = (String) w.get("ip_address");
            else ipB = (String) w.get("ip_address");
        }
        jdbc.update("UPDATE lab_workstation SET ip_address = :t WHERE id = :id", p("t", "__swap_" + a, "id", a));
        jdbc.update("UPDATE lab_workstation SET ip_address = :ip WHERE id = :id", p("ip", ipA, "id", b));
        jdbc.update("UPDATE lab_workstation SET ip_address = :ip WHERE id = :id", p("ip", ipB, "id", a));
    }

    private boolean exists(String sql, org.springframework.jdbc.core.namedparam.MapSqlParameterSource ps) {
        Long n = jdbc.queryForObject(sql, ps, Long.class);
        return n != null && n > 0;
    }

    private static void validate(String name, int rows, int cols) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("名称不能为空");
        if (rows < 1 || rows > 40 || cols < 1 || cols > 40) throw new IllegalArgumentException("行列数需在 1-40 之间");
    }
}
