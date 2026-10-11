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
 * 班级机房配置 + 学生固定机位（原"班级+姓名+固定机位模式"及"绑定座位"）。
 */
@Service
public class LabClassSeatService {
    private final NamedParameterJdbcTemplate jdbc;

    public LabClassSeatService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 所有班级及其机房配置。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> classConfigs() {
        return jdbc.queryForList(
                "SELECT k.id AS \"classId\", sy.label || k.name AS \"className\", c.room_id AS \"roomId\", "
                        + "r.name AS \"roomName\", COALESCE(c.fixed_seat, FALSE) AS \"fixedSeat\", "
                        + "(SELECT COUNT(*) FROM users u WHERE u.class_id = k.id AND u.role = 'STUDENT' AND NOT u.graduated) AS \"studentCount\", "
                        + "(SELECT COUNT(*) FROM users u JOIN lab_student_seat ss ON ss.user_id = u.id "
                        + "   WHERE u.class_id = k.id AND u.role = 'STUDENT' AND NOT u.graduated) AS \"boundCount\" "
                        + "FROM klass k JOIN school_year sy ON sy.id = k.school_year_id "
                        + "LEFT JOIN lab_class_config c ON c.class_id = k.id LEFT JOIN lab_room r ON r.id = c.room_id "
                        + "WHERE sy.active ORDER BY sy.year_code DESC, k.display_order, k.name", p());
    }

    @Transactional
    public void saveConfig(Long classId, Long roomId, boolean fixedSeat) {
        if (fixedSeat && roomId == null) {
            throw new IllegalArgumentException("启用固定机位前请先选择机房");
        }
        Long oldRoom = currentRoom(classId);
        jdbc.update("INSERT INTO lab_class_config(class_id, room_id, fixed_seat) VALUES (:c, :r, :f) "
                        + "ON CONFLICT (class_id) DO UPDATE SET room_id = EXCLUDED.room_id, fixed_seat = EXCLUDED.fixed_seat",
                p("c", classId, "r", roomId, "f", fixedSeat));
        if (oldRoom != null && !oldRoom.equals(roomId)) {
            // 换机房后原座位绑定失效
            clearBindings(classId);
        }
    }

    /** 班级学生及其绑定的座位。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> students(Long classId) {
        return jdbc.queryForList(
                "SELECT u.id AS \"userId\", u.name, u.student_no AS \"studentNo\", ss.seat_id AS \"seatId\", "
                        + "s.row_no AS \"row\", s.col_no AS \"col\", w.pc_name AS \"pcName\", w.ip_address AS \"ipAddress\" "
                        + "FROM users u LEFT JOIN lab_student_seat ss ON ss.user_id = u.id "
                        + "LEFT JOIN lab_seat s ON s.id = ss.seat_id LEFT JOIN lab_workstation w ON w.id = s.workstation_id "
                        + "WHERE u.class_id = :c AND u.role = 'STUDENT' AND NOT u.graduated "
                        + "ORDER BY u.student_no NULLS LAST, u.name", p("c", classId));
    }

    /** 保存绑定：bindings = [{userId, seatId|null}]，同班内座位不可重复。 */
    @Transactional
    public List<Map<String, Object>> saveBindings(Long classId, List<Map<String, Object>> bindings) {
        Long roomId = currentRoom(classId);
        if (roomId == null) throw new IllegalArgumentException("请先为班级选择机房");
        Set<Long> seats = new HashSet<>();
        Set<Long> classStudents = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM users WHERE class_id = :c AND role = 'STUDENT'", p("c", classId), Long.class));
        Set<Long> roomSeats = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM lab_seat WHERE room_id = :r AND workstation_id IS NOT NULL", p("r", roomId), Long.class));
        for (Map<String, Object> b : bindings) {
            Long uid = ((Number) b.get("userId")).longValue();
            if (!classStudents.contains(uid)) throw new IllegalArgumentException("学生不属于该班级");
            Object s = b.get("seatId");
            if (s == null) continue;
            Long seatId = ((Number) s).longValue();
            if (!roomSeats.contains(seatId)) throw new IllegalArgumentException("座位不属于该班级的机房或未放置机位");
            if (!seats.add(seatId)) throw new IllegalArgumentException("同一座位不能绑定两名学生");
        }
        clearBindings(classId);
        for (Map<String, Object> b : bindings) {
            Object s = b.get("seatId");
            if (s == null) continue;
            jdbc.update("INSERT INTO lab_student_seat(user_id, seat_id) VALUES (:u, :s)",
                    p("u", ((Number) b.get("userId")).longValue(), "s", ((Number) s).longValue()));
        }
        return students(classId);
    }

    /** 按学号顺序依次分配座位（逐行逐列）。 */
    @Transactional
    public List<Map<String, Object>> autoAssign(Long classId) {
        Long roomId = currentRoom(classId);
        if (roomId == null) throw new IllegalArgumentException("请先为班级选择机房");
        List<Long> seatIds = jdbc.queryForList(
                "SELECT id FROM lab_seat WHERE room_id = :r AND workstation_id IS NOT NULL ORDER BY row_no, col_no",
                p("r", roomId), Long.class);
        List<Map<String, Object>> studs = students(classId);
        List<Map<String, Object>> bindings = new java.util.ArrayList<>();
        for (int i = 0; i < studs.size(); i++) {
            Map<String, Object> b = new HashMap<>();
            b.put("userId", studs.get(i).get("userId"));
            b.put("seatId", i < seatIds.size() ? seatIds.get(i) : null);
            bindings.add(b);
        }
        return saveBindings(classId, bindings);
    }

    /** 固定机位登录校验用：学生所在班级是否启用固定机位，以及其绑定机位的 IP。 */
    @Transactional(readOnly = true)
    public Map<String, Object> fixedSeatInfo(Long userId) {
        List<Map<String, Object>> l = jdbc.queryForList(
                "SELECT COALESCE(c.fixed_seat, FALSE) AS fixed, w.ip_address AS ip, w.pc_name AS pc "
                        + "FROM users u LEFT JOIN lab_class_config c ON c.class_id = u.class_id "
                        + "LEFT JOIN lab_student_seat ss ON ss.user_id = u.id LEFT JOIN lab_seat s ON s.id = ss.seat_id "
                        + "LEFT JOIN lab_workstation w ON w.id = s.workstation_id WHERE u.id = :u", p("u", userId));
        return l.isEmpty() ? new HashMap<String, Object>() : l.get(0);
    }

    private Long currentRoom(Long classId) {
        List<Long> l = jdbc.queryForList("SELECT room_id FROM lab_class_config WHERE class_id = :c", p("c", classId), Long.class);
        return l.isEmpty() ? null : l.get(0);
    }

    private void clearBindings(Long classId) {
        jdbc.update("DELETE FROM lab_student_seat WHERE user_id IN (SELECT id FROM users WHERE class_id = :c)", p("c", classId));
    }
}
