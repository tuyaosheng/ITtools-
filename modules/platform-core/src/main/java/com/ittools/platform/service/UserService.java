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
import com.ittools.platform.service.dto.UserDtos.UserView;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    public UserService(UserRepository u, KlassRepository c, SchoolYearRepository y, PasswordEncoder e) {
        this.users = u;
        this.classes = c;
        this.years = y;
        this.encoder = e;
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
        return users.findAll().stream()
                .filter(u -> roleFilter == null || u.getRole().name().equals(roleFilter))
                .filter(u -> classId == null || (u.getKlass() != null && u.getKlass().getId().equals(classId)))
                .map(this::view)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserView create(CreateUserCommand c) {
        User u = new User();
        u.setRole(Role.valueOf(c.role()));
        u.setName(c.name());
        u.setLoginName(c.loginName());
        u.setStudentNo(c.studentNo());
        u.setXjh(c.xjh());
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
        u.setStudentNo(c.studentNo());
        u.setXjh(c.xjh());
        u.setKlass(c.classId() == null ? null : classes.findById(c.classId()).orElseThrow(() -> new IllegalArgumentException("班级不存在")));
        u.setEnrollYear(c.enrollYearId() == null ? null : years.findById(c.enrollYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在")));
        return view(u);
    }

    @Transactional
    public void delete(Long id) {
        users.deleteById(id);
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
                u.isGraduated(), u.isEnabled());
    }
}
