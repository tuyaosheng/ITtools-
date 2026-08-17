package com.ittools.platform;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.OffsetDateTime;
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

    @Test
    void updatedAtIsMaintainedByDbTriggerOnUpdate() throws InterruptedException {
        // Insert in its own transaction (JpaRepository.save() is @Transactional per-call;
        // this test class carries no class-level @Transactional, so each repository call
        // below commits independently).
        User s = new User();
        s.setRole(Role.STUDENT); s.setName("李四"); s.setPasswordHash("h");
        Long id = users.save(s).getId();

        User afterInsert = users.findById(id).orElseThrow(() -> new AssertionError("user not found after insert"));
        OffsetDateTime createdAt = afterInsert.getCreatedAt();
        assertThat(createdAt).isNotNull();

        // Postgres now() is transaction-start time; sleep to guarantee the update below
        // runs in a later transaction with a measurably later now(), so the trigger's
        // NEW.updated_at = now() write is distinguishable from the insert's created_at.
        Thread.sleep(1000);

        // Update in a separate, later transaction.
        User toUpdate = users.findById(id).orElseThrow(() -> new AssertionError("user not found before update"));
        toUpdate.setName("李四-updated");
        users.save(toUpdate);

        // updated_at is insertable=false, updatable=false (fully DB-managed), so the
        // Hibernate-side entity never carries the new value directly -- reload from the DB.
        User reloaded = users.findById(id).orElseThrow(() -> new AssertionError("user not found after update"));
        assertThat(reloaded.getName()).isEqualTo("李四-updated");
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isAfter(createdAt);
    }
}
