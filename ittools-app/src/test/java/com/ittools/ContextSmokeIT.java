package com.ittools;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Was ContextSmokeTest: originally a fast, Docker-free boot check that excluded
// DataSource/Hibernate autoconfiguration (see git history, commit e80fd65),
// from before the platform-core module had any DB-backed beans in the main
// component scan. Task 5 added MultiLoginAuthenticationProvider, a @Component
// under "com.ittools" (IttoolsApplication's scanBasePackages) with a hard
// constructor dependency on UserRepository, so a JPA-less context can no
// longer wire the real app config. Renamed to the *IT convention (see
// ittools-app/pom.xml's failsafe comment) and backed by the same Testcontainers
// Postgres as the other *IT classes, so it now verifies the full, real context
// -- arguably a stronger smoke check than the DB-less variant it replaces.
@SpringBootTest
class ContextSmokeIT extends AbstractPostgresIT {
    @Test
    void contextLoads() {
    }
}
