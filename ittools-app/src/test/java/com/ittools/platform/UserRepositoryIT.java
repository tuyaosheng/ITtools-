package com.ittools.platform;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIT extends AbstractPostgresIT {
    @Autowired UserRepository users;
    @Autowired SchoolYearRepository years;
    @Autowired KlassRepository classes;

    @Test
    void studentLookupByClassAndName() {
        SchoolYear y = years.save(new SchoolYear("2021", "2021级", true));
        Klass k = classes.save(new Klass(y, "1班", 1));
        User s = new User();
        s.setRole(Role.STUDENT); s.setName("张三"); s.setPasswordHash("h");
        s.setKlass(k); s.setEnrollYear(y);
        users.save(s);

        Optional<User> found = users.findByKlass_IdAndNameAndRole(k.getId(), "张三", Role.STUDENT);
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void seedAdminFoundByLoginName() {
        assertThat(users.findByLoginName("admin")).isPresent();
    }
}
