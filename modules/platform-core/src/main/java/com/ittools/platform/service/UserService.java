package com.ittools.platform.service;

import com.ittools.platform.domain.Klass;
import com.ittools.platform.domain.Role;
import com.ittools.platform.domain.User;
import com.ittools.platform.repository.KlassRepository;
import com.ittools.platform.repository.SchoolYearRepository;
import com.ittools.platform.repository.UserRepository;
import com.ittools.platform.service.dto.UserDtos.CreateUserCommand;
import com.ittools.platform.service.dto.UserDtos.ResetPasswordCommand;
import com.ittools.platform.service.dto.UserDtos.UpdateUserCommand;
import com.ittools.platform.service.dto.UserDtos.UserPage;
import com.ittools.platform.service.dto.UserDtos.UserView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Java 8 note: the brief uses .stream()...toList() (Java 16+), which is
 * unavailable here - Collectors.toList() is used instead, matching
 * KlassService (Task 9) / SchoolYearService (Task 8).
 */
@Service
public class UserService {
    private static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository users;
    private final KlassRepository classes;
    private final SchoolYearRepository years;
    private final PasswordEncoder encoder;
    private final NamedParameterJdbcTemplate jdbc;

    public UserService(UserRepository u, KlassRepository c, SchoolYearRepository y, PasswordEncoder e,
                       NamedParameterJdbcTemplate jdbc) {
        this.users = u;
        this.classes = c;
        this.years = y;
        this.encoder = e;
        this.jdbc = jdbc;
    }

    // The list used to load every User entity and touch its lazy class one by one
    // (N+1 queries); with thousands of imported students the page froze. Plain SQL
    // with a join and DB-side filtering/paging instead.
    private static final String LIST_FROM = " FROM users u LEFT JOIN klass k ON k.id = u.class_id "
            + "LEFT JOIN school_year sy ON sy.id = k.school_year_id";
    private static final String LIST_COLS = "SELECT u.id, u.role, u.name, u.login_name, u.student_no, u.xjh, "
            + "u.class_id, k.name AS class_name, u.graduated, u.enabled, u.enroll_year_id";
    /** 管理员、教师在前；学生按年级（新的在前）、班级排序、班内学号（按数值）排。 */
    private static final String LIST_ORDER = " ORDER BY CASE u.role WHEN 'ADMIN' THEN 0 WHEN 'TEACHER' THEN 1 ELSE 2 END, "
            + "sy.year_code DESC NULLS LAST, k.display_order NULLS LAST, k.name NULLS LAST, "
            + "LENGTH(u.student_no) NULLS LAST, u.student_no, u.name, u.id";

    private static final RowMapper<UserView> VIEW_ROW = (rs, i) -> new UserView(rs.getLong("id"), rs.getString("role"),
            rs.getString("name"), rs.getString("login_name"), rs.getString("student_no"), rs.getString("xjh"),
            rs.getObject("class_id", Long.class), rs.getString("class_name"), rs.getBoolean("graduated"),
            rs.getBoolean("enabled"), rs.getObject("enroll_year_id", Long.class));

    private static String where(String role, Long classId, Long yearId, String keyword, MapSqlParameterSource ps) {
        List<String> c = new ArrayList<String>();
        if (role != null && !role.isEmpty()) { c.add("u.role = :role"); ps.addValue("role", role); }
        if (classId != null) { c.add("u.class_id = :classId"); ps.addValue("classId", classId); }
        if (yearId != null) { c.add("k.school_year_id = :yearId"); ps.addValue("yearId", yearId); }
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            c.add("(u.name ILIKE :kw OR u.xjh ILIKE :kw OR u.login_name ILIKE :kw)");
            ps.addValue("kw", "%" + kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        return c.isEmpty() ? "" : " WHERE " + String.join(" AND ", c);
    }

    /**
     * @Transactional(readOnly = true) is required here (the brief's snippet
     * omits it): User.klass is @ManyToOne(fetch = LAZY), and
     * application.yml sets spring.jpa.open-in-view: false, so the Hibernate
     * session closes as soon as the (otherwise plain, non-transactional)
     * repository call returns. view() then dereferences
     * u.getKlass().getName() outside any session, throwing
     * LazyInitializationException (hit in Tasks 5 and 9). Keeping the
     * session open across the map() call fixes it.
     */
    @Transactional(readOnly = true)
    public List<UserView> list(String roleFilter, Long classId) {
        MapSqlParameterSource ps = new MapSqlParameterSource();
        String w = where(roleFilter, classId, null, null, ps);
        return jdbc.query(LIST_COLS + LIST_FROM + w + LIST_ORDER, ps, VIEW_ROW);
    }

    /** Admin user list, one page at a time (page is 1-based). */
    @Transactional(readOnly = true)
    public UserPage page(String role, Long classId, Long yearId, String keyword, int page, int size) {
        int sz = Math.max(1, Math.min(size, 200));
        int pg = Math.max(1, page);
        MapSqlParameterSource ps = new MapSqlParameterSource();
        String w = where(role, classId, yearId, keyword, ps);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + LIST_FROM + w, ps, Long.class);
        ps.addValue("limit", sz).addValue("offset", (long) (pg - 1) * sz);
        List<UserView> items = jdbc.query(LIST_COLS + LIST_FROM + w + LIST_ORDER + " LIMIT :limit OFFSET :offset", ps, VIEW_ROW);
        return new UserPage(items, total == null ? 0 : total);
    }

    @Transactional
    public UserView create(CreateUserCommand c) {
        User u = new User();
        u.setRole(Role.valueOf(c.role()));
        u.setName(c.name());
        u.setLoginName(c.loginName());
        setNumbers(u, c.studentNo(), c.xjh());
        if (c.classId() != null) {
            u.setKlass(classes.findById(c.classId()).orElseThrow(() -> new IllegalArgumentException("班级不存在")));
        }
        if (c.enrollYearId() != null) {
            u.setEnrollYear(years.findById(c.enrollYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在")));
        }
        u.setPasswordHash(encoder.encode(c.password() == null ? DEFAULT_PASSWORD : c.password()));
        return view(users.save(u));
    }

    @Transactional
    public UserView update(Long id, UpdateUserCommand c) {
        User u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        u.setName(c.name());
        u.setLoginName(c.loginName());
        setNumbers(u, c.studentNo(), c.xjh());
        u.setKlass(c.classId() == null ? null : classes.findById(c.classId()).orElseThrow(() -> new IllegalArgumentException("班级不存在")));
        u.setEnrollYear(c.enrollYearId() == null ? null : years.findById(c.enrollYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在")));
        return view(u);
    }

    /** 学生：学号必填、班内学号为正整数；教师和管理员不用这两个编号。 */
    private void setNumbers(User u, String studentNo, String xjh) {
        if (u.getRole() == Role.STUDENT) {
            String x = StudentNumbers.requireXjh(xjh);
            users.findByXjh(x).filter(o -> !o.getId().equals(u.getId())).ifPresent(o -> {
                throw new IllegalArgumentException("学号 " + x + " 已被其他学生使用");
            });
            u.setXjh(x);
            u.setStudentNo(StudentNumbers.classNo(studentNo));
        } else {
            u.setXjh(xjh == null || xjh.trim().isEmpty() ? null : xjh.trim());
            u.setStudentNo(studentNo == null || studentNo.trim().isEmpty() ? null : studentNo.trim());
        }
    }

    @Transactional
    public void delete(Long id) {
        try {
            users.deleteById(id);
            users.flush();
        } catch (DataIntegrityViolationException e) {
            // 原系统删除教师会连带丢失其全部教学数据；新库用外键保护，已有课程/作业等记录的账号只能停用
            throw new IllegalStateException("该用户已有课程、作业等教学记录，不能删除，可改为停用账号");
        }
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordCommand c) {
        User u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        u.setPasswordHash(encoder.encode(c.password()));
    }

    @Transactional
    public void setGraduated(Long id, boolean v) {
        users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在")).setGraduated(v);
    }

    @Transactional
    public void setEnabled(Long id, boolean v) {
        users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在")).setEnabled(v);
    }

    private UserView view(User u) {
        Klass k = u.getKlass();
        return new UserView(u.getId(), u.getRole().name(), u.getName(), u.getLoginName(), u.getStudentNo(),
                u.getXjh(), k == null ? null : k.getId(), k == null ? null : k.getName(),
                u.isGraduated(), u.isEnabled(),
                u.getEnrollYear() == null ? null : u.getEnrollYear().getId());
    }
}
