package com.ittools.platform.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/** 登入记录（原 stulog）：登录时写入，退出时补记退出时间。 */
@Service
public class LoginLogService {
    private final NamedParameterJdbcTemplate jdbc;

    public LoginLogService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Long recordLogin(Long userId, String ip) {
        return jdbc.queryForObject("INSERT INTO login_log(user_id, ip_address) VALUES (:u, :ip) RETURNING id", p("u", userId, "ip", ip), Long.class);
    }

    @Transactional
    public void recordLogout(Long logId) {
        if (logId != null) jdbc.update("UPDATE login_log SET logout_at = now() WHERE id = :id AND logout_at IS NULL", p("id", logId));
    }

    /** 某班学生在时间段内的登入记录（含在线分钟数；未退出的按“至今”计）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> classLogs(Long classId, OffsetDateTime from, OffsetDateTime to) {
        return jdbc.queryForList("SELECT u.id AS \"studentId\", u.student_no AS \"studentNo\", u.name, g.ip_address AS \"ip\", g.login_at AS \"loginAt\", "
                + "g.logout_at AS \"logoutAt\", ROUND(EXTRACT(EPOCH FROM (COALESCE(g.logout_at, now()) - g.login_at)) / 60.0, 1) AS minutes "
                + "FROM login_log g JOIN users u ON u.id = g.user_id WHERE u.class_id = :k AND u.role = 'STUDENT' "
                + "AND g.login_at >= :f AND g.login_at < :t ORDER BY g.login_at DESC LIMIT 2000", p("k", classId, "f", from, "t", to));
    }
}
