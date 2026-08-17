package com.ittools.platform.security;

import com.ittools.platform.domain.Role;
import com.ittools.platform.domain.User;
import com.ittools.platform.repository.KlassRepository;
import com.ittools.platform.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class MultiLoginAuthenticationProvider {
    private final UserRepository users;
    private final KlassRepository classes;
    private final PasswordEncoder encoder;

    public MultiLoginAuthenticationProvider(UserRepository users, KlassRepository classes, PasswordEncoder encoder) {
        this.users = users;
        this.classes = classes;
        this.encoder = encoder;
    }

    /**
     * Read-only transactional boundary: STUDENT_NAME resolution walks
     * classes.findAll() and touches the lazily-fetched Klass.schoolYear
     * proxy, and the User row itself may carry lazy associations. Without an
     * open session spanning the whole resolve+verify flow, that access
     * throws LazyInitializationException once the per-repository-call
     * transaction from findAll() has already closed.
     */
    @Transactional(readOnly = true)
    public AppUserDetails authenticate(LoginRequest req) throws AuthenticationException {
        User u = resolve(req).orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        if (!u.isEnabled()) {
            throw new BadCredentialsException("账号已禁用");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new BadCredentialsException("密码错误");
        }
        String uname = u.getLoginName() != null ? u.getLoginName()
                : (u.getXjh() != null ? u.getXjh() : ("uid:" + u.getId()));
        return new AppUserDetails(u.getId(), uname, u.getPasswordHash(), u.getRole(), u.isEnabled());
    }

    private Optional<User> resolve(LoginRequest req) {
        String loginType = req.loginType();
        switch (loginType) {
            case "STUDENT_NAME":
                return classes.findAll().stream()
                        .filter(k -> k.getName().equals(req.className())
                                && k.getSchoolYear().getYearCode().equals(req.yearCode()))
                        .findFirst()
                        .flatMap(k -> users.findByKlass_IdAndNameAndRole(k.getId(), req.name(), Role.STUDENT));
            case "STUDENT_XJH":
                return users.findByXjh(req.xjh())
                        .filter(x -> x.getRole() == Role.STUDENT);
            case "TEACHER":
                return users.findByLoginName(req.loginName())
                        .filter(x -> x.getRole() == Role.TEACHER);
            case "ADMIN":
                return users.findByLoginName(req.loginName())
                        .filter(x -> x.getRole() == Role.ADMIN);
            default:
                return Optional.empty();
        }
    }
}
