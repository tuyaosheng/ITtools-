package com.ittools.platform;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.platform.security.*;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

// @Transactional here (unlike UserRepositoryIT, which deliberately avoids it to
// exercise a DB trigger across separate commits) wraps each test method in one
// rolled-back transaction: it keeps the Hibernate session open across
// seedStudent() + provider.authenticate() so the lazily-fetched
// Klass.schoolYear proxy used by STUDENT_NAME resolution stays initializable,
// and it prevents the per-test seed data (e.g. SchoolYear "2030") from
// colliding with the next test method against the shared Testcontainers DB.
@Transactional
class AuthenticationProviderIT extends AbstractPostgresIT {
    @Autowired MultiLoginAuthenticationProvider provider;
    @Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;
    @Autowired SchoolYearRepository years;
    @Autowired KlassRepository classes;

    private void seedStudent() {
        SchoolYear y = years.save(new SchoolYear("2030","2030级",true));
        Klass k = classes.save(new Klass(y,"3班",1));
        User s = new User();
        s.setRole(Role.STUDENT); s.setName("李四"); s.setXjh("X100");
        s.setKlass(k); s.setPasswordHash(encoder.encode("pw123"));
        users.save(s);
    }

    @Test
    void studentByClassAndNameSucceeds() {
        seedStudent();
        LoginRequest req = new LoginRequest("STUDENT_NAME","2030","3班","李四",null,null,"pw123");
        AppUserDetails ud = provider.authenticate(req);
        assertThat(ud.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void studentByXjhSucceeds() {
        seedStudent();
        LoginRequest req = new LoginRequest("STUDENT_XJH",null,null,null,"X100",null,"pw123");
        assertThat(provider.authenticate(req).role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void wrongPasswordFails() {
        seedStudent();
        LoginRequest req = new LoginRequest("STUDENT_XJH",null,null,null,"X100",null,"nope");
        assertThatThrownBy(() -> provider.authenticate(req))
            .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void adminSeedLoginSucceeds() {
        LoginRequest req = new LoginRequest("ADMIN",null,null,null,null,"admin","admin123");
        assertThat(provider.authenticate(req).role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void teacherLoginSucceeds() {
        User t = new User();
        t.setRole(Role.TEACHER); t.setName("王老师"); t.setLoginName("teacher1");
        t.setPasswordHash(encoder.encode("tpw123"));
        users.save(t);

        LoginRequest req = new LoginRequest("TEACHER",null,null,null,null,"teacher1","tpw123");
        assertThat(provider.authenticate(req).role()).isEqualTo(Role.TEACHER);
    }

    @Test
    void disabledAccountRejected() {
        User u = new User();
        u.setRole(Role.TEACHER); u.setName("已禁用"); u.setLoginName("disabled1");
        u.setPasswordHash(encoder.encode("dpw123")); u.setEnabled(false);
        users.save(u);

        LoginRequest req = new LoginRequest("TEACHER",null,null,null,null,"disabled1","dpw123");
        assertThatThrownBy(() -> provider.authenticate(req))
            .isInstanceOf(org.springframework.security.authentication.DisabledException.class)
            .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void unknownUserRejected() {
        LoginRequest req = new LoginRequest("STUDENT_XJH",null,null,null,"NOSUCHXJH",null,"whatever");
        assertThatThrownBy(() -> provider.authenticate(req))
            .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void nullLoginTypeRejected() {
        LoginRequest req = new LoginRequest(null,null,null,null,null,null,"whatever");
        assertThatThrownBy(() -> provider.authenticate(req))
            .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }
}
