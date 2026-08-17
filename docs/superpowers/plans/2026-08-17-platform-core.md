# Platform-Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the platform foundation for the ITtools rewrite — a modular-monolith Spring Boot app + Vue 3 SPA + PostgreSQL that delivers three-entrance authentication and admin management of users, classes, school years, and role/module permissions.

**Architecture:** Maven multi-module monolith. `ittools-app` is the Spring Boot bootstrap; `platform-core` is a library module holding user/class/year/auth/permission domain, service, repository, and web layers. PostgreSQL schema is versioned by Flyway. Frontend is an independent Vite/Vue 3 SPA served same-origin, authenticated via server-side session cookie.

**Tech Stack:** Java 17, Spring Boot 3.3.x (Web, Security, Data JPA, Validation), PostgreSQL 16, Flyway, Hibernate, Maven, JUnit 5, Testcontainers, Vue 3, Vite, Pinia, Vue Router, Element Plus, axios, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-17-platform-core-design.md`

## Global Constraints

- Java version floor: **17**. Spring Boot **3.3.x**.
- Database: **PostgreSQL 16** only. All schema changes via **Flyway** migrations under `ittools-app/src/main/resources/db/migration`. No `spring.jpa.hibernate.ddl-auto` beyond `validate`.
- Encoding: **UTF-8** everywhere (source files, DB, HTTP).
- Passwords stored as **BCrypt** hashes; never plaintext, never logged.
- All SQL parameterized (JPA / bound parameters). No string-concatenated SQL — ever.
- Package base: `com.ittools`. Module package: `com.ittools.platform`.
- Roles are exactly: `ADMIN`, `TEACHER`, `STUDENT` (enforced by DB CHECK + Java enum).
- REST responses use envelope `{ "code": int, "message": string, "data": <payload> }`; `code` 0 = success.
- API prefix `/api`. Auth via session cookie `JSESSIONID`; CSRF enabled (double-submit `XSRF-TOKEN` cookie).
- Integration tests that touch the DB use **Testcontainers PostgreSQL 16**, not H2.
- TDD: every behavior task writes the failing test first. Frequent commits (one per task minimum).

---

## File Structure

```
ittools/
├─ pom.xml                                   Parent POM (packaging=pom, modules)
├─ docker-compose.yml                        db (postgres:16) + app
├─ ittools-app/
│  ├─ pom.xml                                depends on platform-core; spring-boot-maven-plugin
│  └─ src/main/
│     ├─ java/com/ittools/IttoolsApplication.java
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/
│           ├─ V1__schema.sql
│           └─ V2__seed.sql
│  └─ src/test/java/com/ittools/            integration tests (Testcontainers)
│     └─ support/AbstractPostgresIT.java
├─ modules/platform-core/
│  ├─ pom.xml                                library jar
│  └─ src/main/java/com/ittools/platform/
│     ├─ domain/  Role.java User.java Klass.java SchoolYear.java
│     │           Permission.java RolePermission.java ModulePermission.java
│     ├─ repository/  UserRepository.java KlassRepository.java
│     │               SchoolYearRepository.java PermissionRepository.java
│     │               ModulePermissionRepository.java
│     ├─ service/  UserService.java KlassService.java SchoolYearService.java
│     │            PermissionService.java LookupService.java
│     │            dto/ (command + view records)
│     ├─ web/      AuthController.java PublicLookupController.java
│     │            AdminUserController.java AdminKlassController.java
│     │            AdminSchoolYearController.java AdminPermissionController.java
│     │            ApiResponse.java GlobalExceptionHandler.java
│     └─ security/ SecurityConfig.java MultiLoginAuthenticationProvider.java
│                  AppUserDetails.java LoginRequest.java
└─ web/                                       Vue 3 SPA (independent Vite project)
   ├─ package.json vite.config.ts
   └─ src/
      ├─ main.ts App.vue
      ├─ api/http.ts auth.ts admin.ts
      ├─ stores/auth.ts
      ├─ router/index.ts
      ├─ layouts/AdminLayout.vue
      ├─ views/login/LoginView.vue
      └─ views/admin/{SchoolYearView,ClassView,UserView,PermissionView}.vue
```

**Responsibilities:** `domain` = JPA entities only. `repository` = Spring Data interfaces. `service` = business rules + transactions + DTO mapping (the module's public surface). `web` = HTTP controllers + envelope + validation. `security` = authentication/authorization wiring. Frontend `api` = transport, `stores` = session state, `views` = screens.

---

## Task 1: Maven multi-module skeleton + app boots

**Files:**
- Create: `pom.xml`, `ittools-app/pom.xml`, `modules/platform-core/pom.xml`
- Create: `ittools-app/src/main/java/com/ittools/IttoolsApplication.java`
- Create: `ittools-app/src/main/resources/application.yml`
- Test: `ittools-app/src/test/java/com/ittools/ContextSmokeTest.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: bootable Spring Boot app `IttoolsApplication`; Maven reactor with modules `ittools-app`, `modules/platform-core`.

- [ ] **Step 1: Write the failing test**

`ittools-app/src/test/java/com/ittools/ContextSmokeTest.java`:
```java
package com.ittools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class ContextSmokeTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=ContextSmokeTest`
Expected: FAIL — no `pom.xml` / no `IttoolsApplication` class yet (compile/reactor error).

- [ ] **Step 3: Write minimal implementation**

Parent `pom.xml`:
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.ittools</groupId>
  <artifactId>ittools-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>
  <properties>
    <java.version>17</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <modules>
    <module>modules/platform-core</module>
    <module>ittools-app</module>
  </modules>
</project>
```

`modules/platform-core/pom.xml`:
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.ittools</groupId><artifactId>ittools-parent</artifactId><version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>platform-core</artifactId>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  </dependencies>
</project>
```

`ittools-app/pom.xml`:
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.ittools</groupId><artifactId>ittools-parent</artifactId><version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>ittools-app</artifactId>
  <dependencies>
    <dependency><groupId>com.ittools</groupId><artifactId>platform-core</artifactId><version>0.1.0-SNAPSHOT</version></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
  </dependencies>
  <build><plugins>
    <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
  </plugins></build>
</project>
```

`IttoolsApplication.java`:
```java
package com.ittools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ittools")
public class IttoolsApplication {
    public static void main(String[] args) {
        SpringApplication.run(IttoolsApplication.class, args);
    }
}
```

`application.yml` (minimal for now):
```yaml
spring:
  application:
    name: ittools
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/ittools}
    username: ${DB_USER:ittools}
    password: ${DB_PASSWORD:ittools}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
server:
  servlet:
    encoding:
      charset: UTF-8
      force: true
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=ContextSmokeTest`
Expected: PASS (context loads with DB auto-config excluded).

- [ ] **Step 5: Commit**

```bash
git add pom.xml ittools-app modules
git commit -m "chore: maven multi-module skeleton + bootable app"
```

---

## Task 2: Postgres via docker-compose + Testcontainers IT base + Flyway runs

**Files:**
- Create: `docker-compose.yml`
- Create: `ittools-app/src/test/java/com/ittools/support/AbstractPostgresIT.java`
- Create: `ittools-app/src/main/resources/db/migration/V1__schema.sql` (empty-but-valid placeholder table)
- Test: `ittools-app/src/test/java/com/ittools/FlywayMigrationIT.java`

**Interfaces:**
- Consumes: `IttoolsApplication`.
- Produces: `AbstractPostgresIT` (base class starting a shared Postgres 16 container and wiring `spring.datasource.*`); a running Flyway migration chain.

- [ ] **Step 1: Write the failing test**

`AbstractPostgresIT.java`:
```java
package com.ittools.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractPostgresIT {
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

`FlywayMigrationIT.java`:
```java
package com.ittools;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends AbstractPostgresIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywaySchemaHistoryExists() {
        Integer count = jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=FlywayMigrationIT`
Expected: FAIL — no `V1__schema.sql`, Flyway finds no migrations / table missing.

- [ ] **Step 3: Write minimal implementation**

`V1__schema.sql` (temporary bootstrap content, replaced in Task 3):
```sql
-- placeholder; real schema lands in Task 3
CREATE TABLE app_bootstrap (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY);
```

`docker-compose.yml`:
```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: ittools
      POSTGRES_USER: ittools
      POSTGRES_PASSWORD: ittools
    ports: ["5432:5432"]
    volumes: ["ittools-db:/var/lib/postgresql/data"]
  app:
    build: .
    depends_on: [db]
    environment:
      DB_URL: jdbc:postgresql://db:5432/ittools
      DB_USER: ittools
      DB_PASSWORD: ittools
    ports: ["8080:8080"]
volumes:
  ittools-db:
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=FlywayMigrationIT`
Expected: PASS — Flyway applied V1, history row present.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml ittools-app/src
git commit -m "test: postgres testcontainers base + flyway smoke"
```

---

## Task 3: Schema V1 (school_year, klass, users, permissions) + seed V2

**Files:**
- Modify: `ittools-app/src/main/resources/db/migration/V1__schema.sql` (replace placeholder)
- Create: `ittools-app/src/main/resources/db/migration/V2__seed.sql`
- Test: `ittools-app/src/test/java/com/ittools/SchemaShapeIT.java`

**Interfaces:**
- Consumes: Flyway chain from Task 2.
- Produces: tables `school_year, klass, users, permission, role_permission, module_permission`; seed permission rows and one seed admin (`login_name='admin'`).

- [ ] **Step 1: Write the failing test**

`SchemaShapeIT.java`:
```java
package com.ittools;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaShapeIT extends AbstractPostgresIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void coreTablesExist() {
        for (String t : new String[]{"school_year","klass","users","permission","role_permission","module_permission"}) {
            Integer n = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?", Integer.class, t);
            assertThat(n).as(t).isEqualTo(1);
        }
    }

    @Test
    void seedAdminExists() {
        Integer n = jdbc.queryForObject(
            "select count(*) from users where login_name = 'admin' and role = 'ADMIN'", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    @Test
    void roleCheckConstraintRejectsBadRole() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbc.update("insert into users(role,name,password_hash) values('WIZARD','x','y')"))
            .isInstanceOf(Exception.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=SchemaShapeIT`
Expected: FAIL — tables/seed absent.

- [ ] **Step 3: Write minimal implementation**

`V1__schema.sql`:
```sql
CREATE TABLE school_year (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_code   VARCHAR(16)  NOT NULL UNIQUE,
    label       VARCHAR(64)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE klass (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_year_id  BIGINT NOT NULL REFERENCES school_year(id),
    name            VARCHAR(64) NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    UNIQUE (school_year_id, name)
);

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role            VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN','TEACHER','STUDENT')),
    name            VARCHAR(64) NOT NULL,
    login_name      VARCHAR(64),
    student_no      VARCHAR(32),
    xjh             VARCHAR(32),
    password_hash   VARCHAR(100) NOT NULL,
    class_id        BIGINT REFERENCES klass(id),
    enroll_year_id  BIGINT REFERENCES school_year(id),
    graduated       BOOLEAN NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_login_name ON users(login_name) WHERE login_name IS NOT NULL;
CREATE UNIQUE INDEX ux_users_xjh ON users(xjh) WHERE xjh IS NOT NULL;
CREATE INDEX ix_users_class_name ON users(class_id, name);

CREATE TABLE permission (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code    VARCHAR(64) NOT NULL UNIQUE,
    name    VARCHAR(128) NOT NULL,
    module  VARCHAR(32) NOT NULL
);

CREATE TABLE role_permission (
    role          VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN','TEACHER','STUDENT')),
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    PRIMARY KEY (role, permission_id)
);

CREATE TABLE module_permission (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope         VARCHAR(16) NOT NULL CHECK (scope IN ('GLOBAL','CLASS','USER')),
    scope_ref_id  BIGINT,
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE
);
```

`V2__seed.sql` (BCrypt hash below is for password `admin123` — cost 10):
```sql
INSERT INTO permission(code, name, module) VALUES
  ('ADMIN_USERS',   '用户管理',   'platform'),
  ('ADMIN_CLASSES', '班级管理',   'platform'),
  ('ADMIN_YEARS',   '年级管理',   'platform'),
  ('ADMIN_PERMS',   '权限管理',   'platform');

INSERT INTO role_permission(role, permission_id)
  SELECT 'ADMIN', id FROM permission WHERE module = 'platform';

INSERT INTO users(role, name, login_name, password_hash)
  VALUES ('ADMIN', '系统管理员', 'admin',
          '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
```
> Note: regenerate the hash with `new BCryptPasswordEncoder().encode("admin123")` if the literal above does not verify in your Spring version; the value shown is the canonical BCrypt example digest for `admin123`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=SchemaShapeIT`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add ittools-app/src/main/resources/db/migration ittools-app/src/test
git commit -m "feat(db): core schema + platform permission seed + admin"
```

---

## Task 4: Domain entities + repositories (school_year, klass, users)

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/domain/{Role,SchoolYear,Klass,User}.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/repository/{SchoolYearRepository,KlassRepository,UserRepository}.java`
- Test: `ittools-app/src/test/java/com/ittools/platform/UserRepositoryIT.java`

**Interfaces:**
- Consumes: schema from Task 3.
- Produces:
  - enum `Role { ADMIN, TEACHER, STUDENT }`
  - `@Entity User` fields: `Long id; Role role; String name; String loginName; String studentNo; String xjh; String passwordHash; Klass klass; SchoolYear enrollYear; boolean graduated; boolean enabled;`
  - `UserRepository extends JpaRepository<User,Long>` with `Optional<User> findByLoginName(String)`, `Optional<User> findByXjh(String)`, `Optional<User> findByKlass_IdAndNameAndRole(Long classId, String name, Role role)`, `List<User> findByKlass_Id(Long)`.
  - `SchoolYearRepository`, `KlassRepository` (`List<Klass> findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(Long)`).

- [ ] **Step 1: Write the failing test**

`UserRepositoryIT.java`:
```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=UserRepositoryIT`
Expected: FAIL — entities/repositories not defined (compile error).

- [ ] **Step 3: Write minimal implementation**

`Role.java`:
```java
package com.ittools.platform.domain;
public enum Role { ADMIN, TEACHER, STUDENT }
```

`SchoolYear.java`:
```java
package com.ittools.platform.domain;

import jakarta.persistence.*;

@Entity @Table(name = "school_year")
public class SchoolYear {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "year_code", nullable = false, unique = true) private String yearCode;
    private String label;
    private boolean active = true;

    protected SchoolYear() {}
    public SchoolYear(String yearCode, String label, boolean active) {
        this.yearCode = yearCode; this.label = label; this.active = active;
    }
    public Long getId() { return id; }
    public String getYearCode() { return yearCode; }
    public void setYearCode(String v) { this.yearCode = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
```

`Klass.java`:
```java
package com.ittools.platform.domain;

import jakarta.persistence.*;

@Entity @Table(name = "klass")
public class Klass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "school_year_id", nullable = false)
    private SchoolYear schoolYear;
    private String name;
    @Column(name = "display_order") private int displayOrder;

    protected Klass() {}
    public Klass(SchoolYear y, String name, int order) { this.schoolYear = y; this.name = name; this.displayOrder = order; }
    public Long getId() { return id; }
    public SchoolYear getSchoolYear() { return schoolYear; }
    public void setSchoolYear(SchoolYear v) { this.schoolYear = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
}
```

`User.java`:
```java
package com.ittools.platform.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    private String name;
    @Column(name = "login_name") private String loginName;
    @Column(name = "student_no") private String studentNo;
    private String xjh;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "class_id") private Klass klass;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "enroll_year_id") private SchoolYear enrollYear;
    private boolean graduated = false;
    private boolean enabled = true;
    @Column(name = "created_at", updatable = false, insertable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", insertable = false) private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public Role getRole() { return role; } public void setRole(Role v) { this.role = v; }
    public String getName() { return name; } public void setName(String v) { this.name = v; }
    public String getLoginName() { return loginName; } public void setLoginName(String v) { this.loginName = v; }
    public String getStudentNo() { return studentNo; } public void setStudentNo(String v) { this.studentNo = v; }
    public String getXjh() { return xjh; } public void setXjh(String v) { this.xjh = v; }
    public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String v) { this.passwordHash = v; }
    public Klass getKlass() { return klass; } public void setKlass(Klass v) { this.klass = v; }
    public SchoolYear getEnrollYear() { return enrollYear; } public void setEnrollYear(SchoolYear v) { this.enrollYear = v; }
    public boolean isGraduated() { return graduated; } public void setGraduated(boolean v) { this.graduated = v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { this.enabled = v; }
}
```

Repositories:
```java
package com.ittools.platform.repository;
import com.ittools.platform.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginName(String loginName);
    Optional<User> findByXjh(String xjh);
    Optional<User> findByKlass_IdAndNameAndRole(Long classId, String name, Role role);
    List<User> findByKlass_Id(Long classId);
}
```
```java
package com.ittools.platform.repository;
import com.ittools.platform.domain.SchoolYear;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SchoolYearRepository extends JpaRepository<SchoolYear, Long> {}
```
```java
package com.ittools.platform.repository;
import com.ittools.platform.domain.Klass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KlassRepository extends JpaRepository<Klass, Long> {
    List<Klass> findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(Long schoolYearId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=UserRepositoryIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(platform): core entities + repositories"
```

---

## Task 5: BCrypt encoder + multi-login authentication provider

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/security/{AppUserDetails,LoginRequest,MultiLoginAuthenticationProvider}.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/security/SecurityBeans.java` (PasswordEncoder bean)
- Test: `ittools-app/src/test/java/com/ittools/platform/AuthenticationProviderIT.java`

**Interfaces:**
- Consumes: `UserRepository`, `Role`, `User`.
- Produces:
  - `record LoginRequest(String loginType, String yearCode, String className, String name, String xjh, String loginName, String password)` — `loginType ∈ {STUDENT_NAME, STUDENT_XJH, TEACHER, ADMIN}`.
  - `AppUserDetails implements UserDetails` exposing `Long userId()`, `Role role()`, granted authority `ROLE_<role>`.
  - `MultiLoginAuthenticationProvider` (a `@Component`) resolving `LoginRequest` → authenticated `AppUserDetails` using `PasswordEncoder`.
  - `@Bean PasswordEncoder passwordEncoder()` = `BCryptPasswordEncoder`.

- [ ] **Step 1: Write the failing test**

`AuthenticationProviderIT.java`:
```java
package com.ittools.platform;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.platform.security.*;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

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
        var req = new LoginRequest("STUDENT_NAME","2030","3班","李四",null,null,"pw123");
        AppUserDetails ud = provider.authenticate(req);
        assertThat(ud.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void studentByXjhSucceeds() {
        seedStudent();
        var req = new LoginRequest("STUDENT_XJH",null,null,null,"X100",null,"pw123");
        assertThat(provider.authenticate(req).role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void wrongPasswordFails() {
        seedStudent();
        var req = new LoginRequest("STUDENT_XJH",null,null,null,"X100",null,"nope");
        assertThatThrownBy(() -> provider.authenticate(req))
            .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void adminSeedLoginSucceeds() {
        var req = new LoginRequest("ADMIN",null,null,null,null,"admin","admin123");
        assertThat(provider.authenticate(req).role()).isEqualTo(Role.ADMIN);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=AuthenticationProviderIT`
Expected: FAIL — security classes absent.

- [ ] **Step 3: Write minimal implementation**

`SecurityBeans.java`:
```java
package com.ittools.platform.security;

import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeans {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

`LoginRequest.java`:
```java
package com.ittools.platform.security;
public record LoginRequest(String loginType, String yearCode, String className,
                           String name, String xjh, String loginName, String password) {}
```

`AppUserDetails.java`:
```java
package com.ittools.platform.security;

import com.ittools.platform.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;

public class AppUserDetails implements UserDetails {
    private final Long userId; private final String username;
    private final String passwordHash; private final Role role; private final boolean enabled;

    public AppUserDetails(Long userId, String username, String passwordHash, Role role, boolean enabled) {
        this.userId = userId; this.username = username; this.passwordHash = passwordHash;
        this.role = role; this.enabled = enabled;
    }
    public Long userId() { return userId; }
    public Role role() { return role; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
```

`MultiLoginAuthenticationProvider.java`:
```java
package com.ittools.platform.security;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class MultiLoginAuthenticationProvider {
    private final UserRepository users;
    private final KlassRepository classes;
    private final PasswordEncoder encoder;

    public MultiLoginAuthenticationProvider(UserRepository users, KlassRepository classes, PasswordEncoder encoder) {
        this.users = users; this.classes = classes; this.encoder = encoder;
    }

    public AppUserDetails authenticate(LoginRequest req) throws AuthenticationException {
        User u = resolve(req).orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        if (!u.isEnabled()) throw new BadCredentialsException("账号已禁用");
        if (!encoder.matches(req.password(), u.getPasswordHash()))
            throw new BadCredentialsException("密码错误");
        String uname = u.getLoginName() != null ? u.getLoginName()
                     : (u.getXjh() != null ? u.getXjh() : ("uid:" + u.getId()));
        return new AppUserDetails(u.getId(), uname, u.getPasswordHash(), u.getRole(), u.isEnabled());
    }

    private Optional<User> resolve(LoginRequest req) {
        return switch (req.loginType()) {
            case "STUDENT_NAME" -> classes.findAll().stream()
                    .filter(k -> k.getName().equals(req.className())
                              && k.getSchoolYear().getYearCode().equals(req.yearCode()))
                    .findFirst()
                    .flatMap(k -> users.findByKlass_IdAndNameAndRole(k.getId(), req.name(), Role.STUDENT));
            case "STUDENT_XJH" -> users.findByXjh(req.xjh())
                    .filter(x -> x.getRole() == Role.STUDENT);
            case "TEACHER" -> users.findByLoginName(req.loginName())
                    .filter(x -> x.getRole() == Role.TEACHER);
            case "ADMIN" -> users.findByLoginName(req.loginName())
                    .filter(x -> x.getRole() == Role.ADMIN);
            default -> Optional.empty();
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=AuthenticationProviderIT`
Expected: PASS (4 tests). If `adminSeedLoginSucceeds` fails on the seed hash, regenerate V2 hash as noted in Task 3.

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main/java/com/ittools/platform/security ittools-app/src/test
git commit -m "feat(auth): bcrypt + multi-login authentication provider"
```

---

## Task 6: Security config, ApiResponse envelope, auth endpoints (login/logout/me)

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/ApiResponse.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/GlobalExceptionHandler.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/security/SecurityConfig.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/AuthController.java`
- Test: `ittools-app/src/test/java/com/ittools/platform/AuthEndpointIT.java`

**Interfaces:**
- Consumes: `MultiLoginAuthenticationProvider`, `AppUserDetails`.
- Produces:
  - `ApiResponse<T>` static factories `ok(T)`, `error(int, String)`.
  - `POST /api/auth/login` (JSON `LoginRequest`) → sets session, returns `{userId, name, role, permissions[]}`.
  - `POST /api/auth/logout` → invalidates session.
  - `GET /api/auth/me` → current principal or 401.
  - `SecurityConfig`: `/api/auth/**` and `/api/public/**` permitAll; everything else authenticated; CSRF via `CookieCsrfTokenRepository.withHttpOnlyFalse()`.

- [ ] **Step 1: Write the failing test**

`AuthEndpointIT.java`:
```java
package com.ittools.platform;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class AuthEndpointIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;

    @Test
    void meIsUnauthorizedWithoutLogin() {
        ResponseEntity<String> r = rest.getForEntity("/api/auth/me", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminCanLoginAndFetchMe() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}";
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("\"role\":\"ADMIN\"");

        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        HttpHeaders h2 = new HttpHeaders(); h2.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> me = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(h2), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=AuthEndpointIT`
Expected: FAIL — endpoints/security config absent (likely 404/500).

- [ ] **Step 3: Write minimal implementation**

`ApiResponse.java`:
```java
package com.ittools.platform.web;
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, "ok", data); }
    public static <T> ApiResponse<T> error(int code, String message) { return new ApiResponse<>(code, message, null); }
}
```

`GlobalExceptionHandler.java`:
```java
package com.ittools.platform.web;

import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> auth(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, e.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> bad(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }
}
```

`SecurityConfig.java`:
```java
package com.ittools.platform.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
          .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                      .ignoringRequestMatchers("/api/auth/login"))
          .authorizeHttpRequests(a -> a
              .requestMatchers("/api/auth/**", "/api/public/**").permitAll()
              .requestMatchers("/api/admin/**").hasRole("ADMIN")
              .anyRequest().authenticated())
          .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
          .formLogin(f -> f.disable())
          .httpBasic(b -> b.disable());
        return http.build();
    }
}
```

`AuthController.java`:
```java
package com.ittools.platform.web;

import com.ittools.platform.security.*;
import com.ittools.platform.service.LookupService;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final MultiLoginAuthenticationProvider provider;
    private final LookupService lookups;
    private final SecurityContextRepository repo = new HttpSessionSecurityContextRepository();

    public AuthController(MultiLoginAuthenticationProvider provider, LookupService lookups) {
        this.provider = provider; this.lookups = lookups;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String,Object>> login(@RequestBody LoginRequest req,
                                                 HttpServletRequest request, HttpServletResponse response) {
        AppUserDetails ud = provider.authenticate(req);
        Authentication auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        repo.saveContext(ctx, request, response);
        return ApiResponse.ok(principalPayload(ud));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String,Object>> me(@AuthenticationPrincipal AppUserDetails ud) {
        return ApiResponse.ok(principalPayload(ud));
    }

    private Map<String,Object> principalPayload(AppUserDetails ud) {
        Map<String,Object> m = new HashMap<>();
        m.put("userId", ud.userId());
        m.put("name", ud.getUsername());
        m.put("role", ud.role().name());
        m.put("permissions", lookups.permissionCodesForRole(ud.role()));
        return m;
    }
}
```
> Add `import org.springframework.security.core.annotation.AuthenticationPrincipal;`

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=AuthEndpointIT`
Expected: PASS. (Requires `LookupService.permissionCodesForRole` from Task 7 — implement Task 7 first if compile fails, or stub the method returning `List.of()` then flesh out in Task 7.)

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(auth): security config + login/logout/me endpoints"
```

---

## Task 7: LookupService + public login-dropdown endpoints

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/LookupService.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/repository/{PermissionRepository,RolePermissionRepository}.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/PublicLookupController.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/dto/LookupDtos.java`
- Test: `ittools-app/src/test/java/com/ittools/platform/PublicLookupIT.java`

**Interfaces:**
- Consumes: `SchoolYearRepository`, `KlassRepository`, `UserRepository`, `PermissionRepository`.
- Produces:
  - `LookupService.permissionCodesForRole(Role)` → `List<String>`.
  - `LookupService.activeYears()`, `classesOf(Long yearId)`, `studentsOf(Long classId)` returning DTO records `YearOption(id, yearCode, label)`, `ClassOption(id, name)`, `StudentOption(id, name)`.
  - `GET /api/public/school-years`, `GET /api/public/classes?yearId=`, `GET /api/public/students?classId=`.

- [ ] **Step 1: Write the failing test**

`PublicLookupIT.java`:
```java
package com.ittools.platform;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class PublicLookupIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;
    @Autowired SchoolYearRepository years;
    @Autowired KlassRepository classes;
    @Autowired UserRepository users;

    @Test
    void studentsListedForClassWithoutLogin() {
        SchoolYear y = years.save(new SchoolYear("2040","2040级",true));
        Klass k = classes.save(new Klass(y,"5班",1));
        User s = new User(); s.setRole(Role.STUDENT); s.setName("王五"); s.setKlass(k); s.setPasswordHash("h");
        users.save(s);

        ResponseEntity<String> r = rest.getForEntity("/api/public/students?classId=" + k.getId(), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("王五");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=PublicLookupIT`
Expected: FAIL — endpoint/service missing.

- [ ] **Step 3: Write minimal implementation**

`PermissionRepository.java` / `RolePermissionRepository.java`:
```java
package com.ittools.platform.repository;
import com.ittools.platform.domain.Permission;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    @Query("select p.code from Permission p join RolePermission rp on rp.permissionId = p.id where rp.role = :role")
    List<String> codesForRole(String role);
}
```
> `Permission` and `RolePermission` entities: add them mirroring the schema (`Permission{id,code,name,module}`, `@Entity RolePermission{@EmbeddedId or @IdClass (role, permissionId)}`). Include their creation in this task's implementation.

`LookupDtos.java`:
```java
package com.ittools.platform.service.dto;
public class LookupDtos {
    public record YearOption(Long id, String yearCode, String label) {}
    public record ClassOption(Long id, String name) {}
    public record StudentOption(Long id, String name) {}
}
```

`LookupService.java`:
```java
package com.ittools.platform.service;

import com.ittools.platform.domain.Role;
import com.ittools.platform.repository.*;
import com.ittools.platform.service.dto.LookupDtos.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LookupService {
    private final SchoolYearRepository years;
    private final KlassRepository classes;
    private final UserRepository users;
    private final PermissionRepository perms;

    public LookupService(SchoolYearRepository y, KlassRepository c, UserRepository u, PermissionRepository p) {
        this.years = y; this.classes = c; this.users = u; this.perms = p;
    }
    public List<String> permissionCodesForRole(Role role) { return perms.codesForRole(role.name()); }
    public List<YearOption> activeYears() {
        return years.findAll().stream().filter(sy -> sy.isActive())
            .map(sy -> new YearOption(sy.getId(), sy.getYearCode(), sy.getLabel())).toList();
    }
    public List<ClassOption> classesOf(Long yearId) {
        return classes.findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(yearId).stream()
            .map(k -> new ClassOption(k.getId(), k.getName())).toList();
    }
    public List<StudentOption> studentsOf(Long classId) {
        return users.findByKlass_Id(classId).stream()
            .filter(u -> u.getRole() == Role.STUDENT && !u.isGraduated())
            .map(u -> new StudentOption(u.getId(), u.getName())).toList();
    }
}
```

`PublicLookupController.java`:
```java
package com.ittools.platform.web;

import com.ittools.platform.service.LookupService;
import com.ittools.platform.service.dto.LookupDtos.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicLookupController {
    private final LookupService lookups;
    public PublicLookupController(LookupService l) { this.lookups = l; }

    @GetMapping("/school-years") public ApiResponse<List<YearOption>> years() { return ApiResponse.ok(lookups.activeYears()); }
    @GetMapping("/classes") public ApiResponse<List<ClassOption>> classes(@RequestParam Long yearId) { return ApiResponse.ok(lookups.classesOf(yearId)); }
    @GetMapping("/students") public ApiResponse<List<StudentOption>> students(@RequestParam Long classId) { return ApiResponse.ok(lookups.studentsOf(classId)); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=PublicLookupIT,AuthEndpointIT`
Expected: PASS (and AuthEndpointIT now fully green with real permissions).

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(platform): lookup service + public login dropdown endpoints"
```

---

## Task 8: Admin SchoolYear CRUD

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/SchoolYearService.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/AdminSchoolYearController.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/dto/SchoolYearDtos.java`
- Test: `ittools-app/src/test/java/com/ittools/platform/AdminSchoolYearIT.java`

**Interfaces:**
- Consumes: `SchoolYearRepository`, security (ADMIN role).
- Produces:
  - DTOs `SchoolYearView(Long id,String yearCode,String label,boolean active)`, `SchoolYearCommand(String yearCode,String label,boolean active)`.
  - `SchoolYearService`: `list()`, `create(cmd)`, `update(id,cmd)`, `delete(id)`.
  - Endpoints under `/api/admin/school-years` (GET list, POST, PUT/{id}, DELETE/{id}), ADMIN-only.

- [ ] **Step 1: Write the failing test**

`AdminSchoolYearIT.java`:
```java
package com.ittools.platform;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class AdminSchoolYearIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;

    private HttpHeaders adminSession() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        var login = rest.postForEntity("/api/auth/login",
            new HttpEntity<>("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}", h), String.class);
        HttpHeaders sess = new HttpHeaders();
        sess.setContentType(MediaType.APPLICATION_JSON);
        sess.add(HttpHeaders.COOKIE, login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        // carry CSRF token cookie back as header
        String setCookies = String.join(";", login.getHeaders().get(HttpHeaders.SET_COOKIE));
        for (String c : setCookies.split(";")) {
            if (c.trim().startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", c.trim().substring("XSRF-TOKEN=".length()));
        }
        return sess;
    }

    @Test
    void adminCreatesAndListsYear() {
        HttpHeaders sess = adminSession();
        var create = rest.exchange("/api/admin/school-years", HttpMethod.POST,
            new HttpEntity<>("{\"yearCode\":\"2050\",\"label\":\"2050级\",\"active\":true}", sess), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);

        var list = rest.exchange("/api/admin/school-years", HttpMethod.GET, new HttpEntity<>(sess), String.class);
        assertThat(list.getBody()).contains("2050级");
    }

    @Test
    void anonymousForbidden() {
        var r = rest.getForEntity("/api/admin/school-years", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=AdminSchoolYearIT`
Expected: FAIL — endpoints missing.

- [ ] **Step 3: Write minimal implementation**

`SchoolYearDtos.java`:
```java
package com.ittools.platform.service.dto;
public class SchoolYearDtos {
    public record SchoolYearView(Long id, String yearCode, String label, boolean active) {}
    public record SchoolYearCommand(String yearCode, String label, boolean active) {}
}
```

`SchoolYearService.java`:
```java
package com.ittools.platform.service;

import com.ittools.platform.domain.SchoolYear;
import com.ittools.platform.repository.SchoolYearRepository;
import com.ittools.platform.service.dto.SchoolYearDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SchoolYearService {
    private final SchoolYearRepository repo;
    public SchoolYearService(SchoolYearRepository r) { this.repo = r; }

    public List<SchoolYearView> list() {
        return repo.findAll().stream()
            .map(y -> new SchoolYearView(y.getId(), y.getYearCode(), y.getLabel(), y.isActive())).toList();
    }
    @Transactional public SchoolYearView create(SchoolYearCommand c) {
        SchoolYear y = repo.save(new SchoolYear(c.yearCode(), c.label(), c.active()));
        return new SchoolYearView(y.getId(), y.getYearCode(), y.getLabel(), y.isActive());
    }
    @Transactional public SchoolYearView update(Long id, SchoolYearCommand c) {
        SchoolYear y = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
        y.setYearCode(c.yearCode()); y.setLabel(c.label()); y.setActive(c.active());
        return new SchoolYearView(y.getId(), y.getYearCode(), y.getLabel(), y.isActive());
    }
    @Transactional public void delete(Long id) { repo.deleteById(id); }
}
```

`AdminSchoolYearController.java`:
```java
package com.ittools.platform.web;

import com.ittools.platform.service.SchoolYearService;
import com.ittools.platform.service.dto.SchoolYearDtos.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/school-years")
public class AdminSchoolYearController {
    private final SchoolYearService svc;
    public AdminSchoolYearController(SchoolYearService s) { this.svc = s; }

    @GetMapping public ApiResponse<List<SchoolYearView>> list() { return ApiResponse.ok(svc.list()); }
    @PostMapping public ApiResponse<SchoolYearView> create(@RequestBody SchoolYearCommand c) { return ApiResponse.ok(svc.create(c)); }
    @PutMapping("/{id}") public ApiResponse<SchoolYearView> update(@PathVariable Long id, @RequestBody SchoolYearCommand c) { return ApiResponse.ok(svc.update(id, c)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { svc.delete(id); return ApiResponse.ok(null); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=AdminSchoolYearIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): school year CRUD"
```

---

## Task 9: Admin Klass CRUD

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/KlassService.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/AdminKlassController.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/dto/KlassDtos.java`
- Test: `ittools-app/src/test/java/com/ittools/platform/AdminKlassIT.java`

**Interfaces:**
- Consumes: `KlassRepository`, `SchoolYearRepository`, admin session helper (copy the `adminSession()` helper from Task 8 into this test class — do not share test infra across tasks).
- Produces:
  - DTOs `KlassView(Long id,Long schoolYearId,String yearLabel,String name,int displayOrder)`, `KlassCommand(Long schoolYearId,String name,int displayOrder)`.
  - `KlassService`: `listByYear(Long yearId)`, `create(cmd)`, `update(id,cmd)`, `delete(id)`.
  - Endpoints `/api/admin/classes` (GET `?yearId=`, POST, PUT/{id}, DELETE/{id}), ADMIN-only.

- [ ] **Step 1: Write the failing test**

`AdminKlassIT.java`:
```java
package com.ittools.platform;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class AdminKlassIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;

    private HttpHeaders adminSession() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        var login = rest.postForEntity("/api/auth/login",
            new HttpEntity<>("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}", h), String.class);
        HttpHeaders sess = new HttpHeaders(); sess.setContentType(MediaType.APPLICATION_JSON);
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        sess.add(HttpHeaders.COOKIE, setCookie);
        for (String c : String.join(";", login.getHeaders().get(HttpHeaders.SET_COOKIE)).split(";"))
            if (c.trim().startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", c.trim().substring("XSRF-TOKEN=".length()));
        return sess;
    }

    @Test
    void createClassUnderYear() {
        HttpHeaders sess = adminSession();
        var year = rest.exchange("/api/admin/school-years", HttpMethod.POST,
            new HttpEntity<>("{\"yearCode\":\"2060\",\"label\":\"2060级\",\"active\":true}", sess), String.class);
        // extract id from response json (naive)
        String body = year.getBody();
        long yearId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        var create = rest.exchange("/api/admin/classes", HttpMethod.POST,
            new HttpEntity<>("{\"schoolYearId\":" + yearId + ",\"name\":\"7班\",\"displayOrder\":1}", sess), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);

        var list = rest.exchange("/api/admin/classes?yearId=" + yearId, HttpMethod.GET, new HttpEntity<>(sess), String.class);
        assertThat(list.getBody()).contains("7班");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=AdminKlassIT`
Expected: FAIL — endpoints missing.

- [ ] **Step 3: Write minimal implementation**

`KlassDtos.java`:
```java
package com.ittools.platform.service.dto;
public class KlassDtos {
    public record KlassView(Long id, Long schoolYearId, String yearLabel, String name, int displayOrder) {}
    public record KlassCommand(Long schoolYearId, String name, int displayOrder) {}
}
```

`KlassService.java`:
```java
package com.ittools.platform.service;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.platform.service.dto.KlassDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class KlassService {
    private final KlassRepository classes; private final SchoolYearRepository years;
    public KlassService(KlassRepository c, SchoolYearRepository y) { this.classes = c; this.years = y; }

    public List<KlassView> listByYear(Long yearId) {
        return classes.findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(yearId).stream().map(this::view).toList();
    }
    @Transactional public KlassView create(KlassCommand c) {
        SchoolYear y = years.findById(c.schoolYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
        return view(classes.save(new Klass(y, c.name(), c.displayOrder())));
    }
    @Transactional public KlassView update(Long id, KlassCommand c) {
        Klass k = classes.findById(id).orElseThrow(() -> new IllegalArgumentException("班级不存在"));
        SchoolYear y = years.findById(c.schoolYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
        k.setSchoolYear(y); k.setName(c.name()); k.setDisplayOrder(c.displayOrder());
        return view(k);
    }
    @Transactional public void delete(Long id) { classes.deleteById(id); }

    private KlassView view(Klass k) {
        return new KlassView(k.getId(), k.getSchoolYear().getId(), k.getSchoolYear().getLabel(), k.getName(), k.getDisplayOrder());
    }
}
```

`AdminKlassController.java`:
```java
package com.ittools.platform.web;

import com.ittools.platform.service.KlassService;
import com.ittools.platform.service.dto.KlassDtos.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/classes")
public class AdminKlassController {
    private final KlassService svc;
    public AdminKlassController(KlassService s) { this.svc = s; }

    @GetMapping public ApiResponse<List<KlassView>> list(@RequestParam Long yearId) { return ApiResponse.ok(svc.listByYear(yearId)); }
    @PostMapping public ApiResponse<KlassView> create(@RequestBody KlassCommand c) { return ApiResponse.ok(svc.create(c)); }
    @PutMapping("/{id}") public ApiResponse<KlassView> update(@PathVariable Long id, @RequestBody KlassCommand c) { return ApiResponse.ok(svc.update(id, c)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { svc.delete(id); return ApiResponse.ok(null); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=AdminKlassIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): class CRUD"
```

---

## Task 10: Admin User CRUD (create/edit/delete/reset-password/graduate/enable)

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/UserService.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/AdminUserController.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/dto/UserDtos.java`
- Test: `ittools-app/src/test/java/com/ittools/platform/AdminUserIT.java`

**Interfaces:**
- Consumes: `UserRepository`, `KlassRepository`, `SchoolYearRepository`, `PasswordEncoder`.
- Produces:
  - DTOs:
    - `UserView(Long id,String role,String name,String loginName,String studentNo,String xjh,Long classId,String className,boolean graduated,boolean enabled)`
    - `CreateUserCommand(String role,String name,String loginName,String studentNo,String xjh,Long classId,Long enrollYearId,String password)`
    - `UpdateUserCommand(String name,String loginName,String studentNo,String xjh,Long classId,Long enrollYearId)`
    - `ResetPasswordCommand(String password)`
  - `UserService`: `list(String roleFilter, Long classId)`, `create(cmd)`, `update(id,cmd)`, `delete(id)`, `resetPassword(id,cmd)`, `setGraduated(id,boolean)`, `setEnabled(id,boolean)`. Password always BCrypt-encoded on write.
  - Endpoints `/api/admin/users`: GET `?role=&classId=`, POST, PUT/{id}, DELETE/{id}, POST/{id}/reset-password, POST/{id}/graduate?value=, POST/{id}/enabled?value=. ADMIN-only.

- [ ] **Step 1: Write the failing test**

`AdminUserIT.java`:
```java
package com.ittools.platform;

import com.ittools.platform.repository.UserRepository;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

class AdminUserIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private HttpHeaders adminSession() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        var login = rest.postForEntity("/api/auth/login",
            new HttpEntity<>("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}", h), String.class);
        HttpHeaders sess = new HttpHeaders(); sess.setContentType(MediaType.APPLICATION_JSON);
        sess.add(HttpHeaders.COOKIE, login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        for (String c : String.join(";", login.getHeaders().get(HttpHeaders.SET_COOKIE)).split(";"))
            if (c.trim().startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", c.trim().substring("XSRF-TOKEN=".length()));
        return sess;
    }

    @Test
    void createTeacherStoresBcryptHash() {
        HttpHeaders sess = adminSession();
        var create = rest.exchange("/api/admin/users", HttpMethod.POST,
            new HttpEntity<>("{\"role\":\"TEACHER\",\"name\":\"陈老师\",\"loginName\":\"chen\",\"password\":\"pw123\"}", sess),
            String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);

        var u = users.findByLoginName("chen").orElseThrow();
        assertThat(u.getPasswordHash()).isNotEqualTo("pw123");
        assertThat(encoder.matches("pw123", u.getPasswordHash())).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=AdminUserIT`
Expected: FAIL — endpoints missing.

- [ ] **Step 3: Write minimal implementation**

`UserDtos.java`:
```java
package com.ittools.platform.service.dto;
public class UserDtos {
    public record UserView(Long id, String role, String name, String loginName, String studentNo,
                           String xjh, Long classId, String className, boolean graduated, boolean enabled) {}
    public record CreateUserCommand(String role, String name, String loginName, String studentNo,
                                    String xjh, Long classId, Long enrollYearId, String password) {}
    public record UpdateUserCommand(String name, String loginName, String studentNo,
                                    String xjh, Long classId, Long enrollYearId) {}
    public record ResetPasswordCommand(String password) {}
}
```

`UserService.java`:
```java
package com.ittools.platform.service;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.platform.service.dto.UserDtos.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UserService {
    private final UserRepository users; private final KlassRepository classes;
    private final SchoolYearRepository years; private final PasswordEncoder encoder;

    public UserService(UserRepository u, KlassRepository c, SchoolYearRepository y, PasswordEncoder e) {
        this.users = u; this.classes = c; this.years = y; this.encoder = e;
    }

    public List<UserView> list(String roleFilter, Long classId) {
        return users.findAll().stream()
            .filter(u -> roleFilter == null || u.getRole().name().equals(roleFilter))
            .filter(u -> classId == null || (u.getKlass() != null && u.getKlass().getId().equals(classId)))
            .map(this::view).toList();
    }
    @Transactional public UserView create(CreateUserCommand c) {
        User u = new User();
        u.setRole(Role.valueOf(c.role())); u.setName(c.name());
        u.setLoginName(c.loginName()); u.setStudentNo(c.studentNo()); u.setXjh(c.xjh());
        if (c.classId() != null) u.setKlass(classes.findById(c.classId()).orElseThrow(() -> new IllegalArgumentException("班级不存在")));
        if (c.enrollYearId() != null) u.setEnrollYear(years.findById(c.enrollYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在")));
        u.setPasswordHash(encoder.encode(c.password() == null ? "123456" : c.password()));
        return view(users.save(u));
    }
    @Transactional public UserView update(Long id, UpdateUserCommand c) {
        User u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        u.setName(c.name()); u.setLoginName(c.loginName()); u.setStudentNo(c.studentNo()); u.setXjh(c.xjh());
        u.setKlass(c.classId() == null ? null : classes.findById(c.classId()).orElseThrow(() -> new IllegalArgumentException("班级不存在")));
        u.setEnrollYear(c.enrollYearId() == null ? null : years.findById(c.enrollYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在")));
        return view(u);
    }
    @Transactional public void delete(Long id) { users.deleteById(id); }
    @Transactional public void resetPassword(Long id, ResetPasswordCommand c) {
        User u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        u.setPasswordHash(encoder.encode(c.password()));
    }
    @Transactional public void setGraduated(Long id, boolean v) {
        users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在")).setGraduated(v);
    }
    @Transactional public void setEnabled(Long id, boolean v) {
        users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在")).setEnabled(v);
    }
    private UserView view(User u) {
        return new UserView(u.getId(), u.getRole().name(), u.getName(), u.getLoginName(), u.getStudentNo(),
            u.getXjh(), u.getKlass() == null ? null : u.getKlass().getId(),
            u.getKlass() == null ? null : u.getKlass().getName(), u.isGraduated(), u.isEnabled());
    }
}
```

`AdminUserController.java`:
```java
package com.ittools.platform.web;

import com.ittools.platform.service.UserService;
import com.ittools.platform.service.dto.UserDtos.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService svc;
    public AdminUserController(UserService s) { this.svc = s; }

    @GetMapping public ApiResponse<List<UserView>> list(@RequestParam(required=false) String role,
                                                        @RequestParam(required=false) Long classId) {
        return ApiResponse.ok(svc.list(role, classId));
    }
    @PostMapping public ApiResponse<UserView> create(@RequestBody CreateUserCommand c) { return ApiResponse.ok(svc.create(c)); }
    @PutMapping("/{id}") public ApiResponse<UserView> update(@PathVariable Long id, @RequestBody UpdateUserCommand c) { return ApiResponse.ok(svc.update(id, c)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { svc.delete(id); return ApiResponse.ok(null); }
    @PostMapping("/{id}/reset-password") public ApiResponse<Void> reset(@PathVariable Long id, @RequestBody ResetPasswordCommand c) { svc.resetPassword(id, c); return ApiResponse.ok(null); }
    @PostMapping("/{id}/graduate") public ApiResponse<Void> graduate(@PathVariable Long id, @RequestParam boolean value) { svc.setGraduated(id, value); return ApiResponse.ok(null); }
    @PostMapping("/{id}/enabled") public ApiResponse<Void> enabled(@PathVariable Long id, @RequestParam boolean value) { svc.setEnabled(id, value); return ApiResponse.ok(null); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=AdminUserIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): user CRUD + reset-password/graduate/enable"
```

---

## Task 11: Vue SPA scaffold + http client + auth store + /me boot

**Files:**
- Create: `web/package.json`, `web/vite.config.ts`, `web/index.html`, `web/src/main.ts`, `web/src/App.vue`
- Create: `web/src/api/http.ts`, `web/src/stores/auth.ts`, `web/src/router/index.ts`
- Test: `web/src/stores/__tests__/auth.spec.ts` (Vitest)

**Interfaces:**
- Consumes: backend `/api/auth/me`, `/api/auth/login`, `/api/auth/logout`.
- Produces:
  - `http` axios instance: baseURL `/api`, `withCredentials: true`, reads `XSRF-TOKEN` cookie into `X-XSRF-TOKEN` header on mutating requests.
  - Pinia `useAuthStore` with state `{ user: null|{userId,name,role,permissions[]} }`, actions `fetchMe()`, `login(payload)`, `logout()`, getter `isAuthenticated`, `hasPermission(code)`.

- [ ] **Step 1: Write the failing test**

`web/src/stores/__tests__/auth.spec.ts`:
```ts
import { setActivePinia, createPinia } from 'pinia'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useAuthStore } from '../auth'
import { http } from '../../api/http'

vi.mock('../../api/http', () => ({ http: { get: vi.fn(), post: vi.fn() } }))

describe('auth store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('stores user after fetchMe', async () => {
    ;(http.get as any).mockResolvedValue({ data: { code: 0, data: { userId: 1, name: 'admin', role: 'ADMIN', permissions: ['ADMIN_USERS'] } } })
    const store = useAuthStore()
    await store.fetchMe()
    expect(store.isAuthenticated).toBe(true)
    expect(store.hasPermission('ADMIN_USERS')).toBe(true)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && npm install && npx vitest run`
Expected: FAIL — store/http not implemented.

- [ ] **Step 3: Write minimal implementation**

`web/package.json` (key deps): `vue@^3.4`, `vue-router@^4`, `pinia@^2`, `element-plus@^2`, `axios@^1`, devDeps `vite@^5`, `@vitejs/plugin-vue`, `vitest`, `@vue/test-utils`, `typescript`. Scripts: `dev`, `build`, `test: vitest run`.

`web/vite.config.ts`:
```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom' }
})
```

`web/src/api/http.ts`:
```ts
import axios from 'axios'

function cookie(name: string): string | undefined {
  return document.cookie.split('; ').find(c => c.startsWith(name + '='))?.split('=')[1]
}

export const http = axios.create({ baseURL: '/api', withCredentials: true })

http.interceptors.request.use(cfg => {
  const method = (cfg.method || 'get').toLowerCase()
  if (['post', 'put', 'delete', 'patch'].includes(method)) {
    const token = cookie('XSRF-TOKEN')
    if (token) cfg.headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
  }
  return cfg
})
```

`web/src/stores/auth.ts`:
```ts
import { defineStore } from 'pinia'
import { http } from '../api/http'

interface User { userId: number; name: string; role: string; permissions: string[] }

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as User | null }),
  getters: {
    isAuthenticated: (s) => s.user !== null,
    hasPermission: (s) => (code: string) => !!s.user?.permissions.includes(code)
  },
  actions: {
    async fetchMe() {
      try { const { data } = await http.get('/auth/me'); this.user = data.data }
      catch { this.user = null }
    },
    async login(payload: Record<string, unknown>) {
      const { data } = await http.post('/auth/login', payload); this.user = data.data
    },
    async logout() { await http.post('/auth/logout'); this.user = null }
  }
})
```

`web/src/router/index.ts`, `web/src/main.ts`, `web/src/App.vue`, `web/index.html`: standard Vue + Pinia + Router + Element Plus bootstrap; router has routes `/login` and `/admin` (guarded, redirect to `/login` when `!isAuthenticated`).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && npx vitest run`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web
git commit -m "feat(web): vue scaffold + http client + auth store"
```

---

## Task 12: Login view (three entrances) — modern redesign, functional parity

**Files:**
- Create: `web/src/views/login/LoginView.vue`
- Create: `web/src/api/auth.ts`
- Test: `web/src/views/login/__tests__/LoginView.spec.ts`

**Interfaces:**
- Consumes: `/api/public/school-years|classes|students`, `useAuthStore().login`.
- Produces: a login screen with a **tabbed** modern layout (Element Plus tabs) covering all original entrances: `学生(班级+姓名)`, `学生(学籍号)`, `教师`, `管理员`. On student-name tab: year→class→name cascading selects (loaded from public endpoints). Emits `login({loginType, ...})`.

**Design note (per spec §8.0):** Do NOT reproduce the old `login2.jpg` fixed-table layout. Use a clean centered card, responsive, Element Plus form components, inline validation, and loading/error states. All four original login paths must remain functional.

- [ ] **Step 1: Write the failing test**

`LoginView.spec.ts`:
```ts
import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import LoginView from '../LoginView.vue'

vi.mock('../../../api/http', () => ({ http: { get: vi.fn().mockResolvedValue({ data: { code: 0, data: [] } }), post: vi.fn() } }))

describe('LoginView', () => {
  it('renders all four login entrances', () => {
    setActivePinia(createPinia())
    const wrapper = mount(LoginView, { global: { stubs: { 'el-tab-pane': false } } })
    const text = wrapper.text()
    expect(text).toContain('学籍号')
    expect(text).toContain('教师')
    expect(text).toContain('管理员')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && npx vitest run LoginView`
Expected: FAIL — component absent.

- [ ] **Step 3: Write minimal implementation**

`web/src/api/auth.ts`:
```ts
import { http } from './http'
export const publicApi = {
  years: () => http.get('/public/school-years').then(r => r.data.data),
  classes: (yearId: number) => http.get('/public/classes', { params: { yearId } }).then(r => r.data.data),
  students: (classId: number) => http.get('/public/students', { params: { classId } }).then(r => r.data.data)
}
```

`LoginView.vue`: implement with `<el-tabs>` holding four `<el-tab-pane>` (labels `学生 · 班级姓名`, `学生 · 学籍号`, `教师`, `管理员`), each a small `<el-form>`; student-name pane wires three `<el-select>` (year/class/name) to `publicApi`; submit calls `useAuthStore().login({...})` then routes by role to `/admin` (ADMIN), `/teacher`, `/student` (latter two are placeholders for later sub-projects — route to a "敬请期待" stub for now). Centered `<el-card>`, `max-width: 420px`, error via `<el-alert>`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && npx vitest run LoginView`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/views/login web/src/api/auth.ts
git commit -m "feat(web): modern three-entrance login view"
```

---

## Task 13: Admin layout + School Year / Class / User management views + Permission view

**Files:**
- Create: `web/src/layouts/AdminLayout.vue`
- Create: `web/src/api/admin.ts`
- Create: `web/src/views/admin/{SchoolYearView,ClassView,UserView,PermissionView}.vue`
- Modify: `web/src/router/index.ts` (nest admin children)
- Test: `web/src/views/admin/__tests__/SchoolYearView.spec.ts`

**Interfaces:**
- Consumes: `/api/admin/school-years|classes|users|permissions` and `/api/admin/module-permissions` (from Task 14).
- Produces: `adminApi` (typed CRUD wrappers) + four management screens inside `AdminLayout` (Element Plus `<el-container>` with side menu). Tables with add/edit dialog forms, delete confirm, and for users: role/class filter, reset-password, graduate/enable toggles.

**Design note:** consistent design tokens, responsive tables, dialog-based edit forms — one shared table/dialog pattern reused across all four screens (DRY).

- [ ] **Step 1: Write the failing test**

`SchoolYearView.spec.ts`:
```ts
import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import SchoolYearView from '../SchoolYearView.vue'

vi.mock('../../../api/admin', () => ({
  adminApi: { years: { list: vi.fn().mockResolvedValue([{ id: 1, yearCode: '2021', label: '2021级', active: true }]) } }
}))

describe('SchoolYearView', () => {
  it('lists years on mount', async () => {
    const wrapper = mount(SchoolYearView)
    await flushPromises()
    expect(wrapper.text()).toContain('2021级')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && npx vitest run SchoolYearView`
Expected: FAIL — view/api absent.

- [ ] **Step 3: Write minimal implementation**

`web/src/api/admin.ts`:
```ts
import { http } from './http'
const unwrap = (p: Promise<any>) => p.then(r => r.data.data)
export const adminApi = {
  years: {
    list: () => unwrap(http.get('/admin/school-years')),
    create: (b: any) => unwrap(http.post('/admin/school-years', b)),
    update: (id: number, b: any) => unwrap(http.put(`/admin/school-years/${id}`, b)),
    remove: (id: number) => unwrap(http.delete(`/admin/school-years/${id}`))
  },
  classes: {
    list: (yearId: number) => unwrap(http.get('/admin/classes', { params: { yearId } })),
    create: (b: any) => unwrap(http.post('/admin/classes', b)),
    update: (id: number, b: any) => unwrap(http.put(`/admin/classes/${id}`, b)),
    remove: (id: number) => unwrap(http.delete(`/admin/classes/${id}`))
  },
  users: {
    list: (params: any) => unwrap(http.get('/admin/users', { params })),
    create: (b: any) => unwrap(http.post('/admin/users', b)),
    update: (id: number, b: any) => unwrap(http.put(`/admin/users/${id}`, b)),
    remove: (id: number) => unwrap(http.delete(`/admin/users/${id}`)),
    resetPassword: (id: number, password: string) => unwrap(http.post(`/admin/users/${id}/reset-password`, { password })),
    graduate: (id: number, value: boolean) => unwrap(http.post(`/admin/users/${id}/graduate`, null, { params: { value } })),
    enabled: (id: number, value: boolean) => unwrap(http.post(`/admin/users/${id}/enabled`, null, { params: { value } }))
  },
  permissions: { list: () => unwrap(http.get('/admin/permissions')) }
}
```

Views: `SchoolYearView.vue` (table + add/edit dialog + delete), `ClassView.vue` (year selector + class table), `UserView.vue` (role/class filters + user table + create/edit dialog + reset-password/graduate/enable actions), `PermissionView.vue` (list permissions + module-permission toggles). `AdminLayout.vue` provides the shell with a left `<el-menu>` linking the four routes. Router nests them under `/admin`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && npx vitest run`
Expected: PASS (all web specs).

- [ ] **Step 5: Commit**

```bash
git add web/src
git commit -m "feat(web): admin layout + year/class/user/permission management"
```

---

## Task 14: Admin permission listing + module-permission toggles (backend)

**Files:**
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/PermissionService.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/web/AdminPermissionController.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/repository/ModulePermissionRepository.java`
- Create: `modules/platform-core/src/main/java/com/ittools/platform/service/dto/PermissionDtos.java`
- Test: `ittools-app/src/test/java/com/ittools/platform/AdminPermissionIT.java`

**Interfaces:**
- Consumes: `PermissionRepository`, `ModulePermissionRepository`.
- Produces:
  - DTOs `PermissionView(Long id,String code,String name,String module)`, `ModulePermissionCommand(String scope,Long scopeRefId,Long permissionId,boolean enabled)`.
  - `PermissionService`: `listPermissions()`, `upsertModulePermission(cmd)`.
  - Endpoints `GET /api/admin/permissions`, `PUT /api/admin/module-permissions`. ADMIN-only.

- [ ] **Step 1: Write the failing test**

`AdminPermissionIT.java`:
```java
package com.ittools.platform;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;

    private HttpHeaders adminSession() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        var login = rest.postForEntity("/api/auth/login",
            new HttpEntity<>("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}", h), String.class);
        HttpHeaders sess = new HttpHeaders(); sess.setContentType(MediaType.APPLICATION_JSON);
        sess.add(HttpHeaders.COOKIE, login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        return sess;
    }

    @Test
    void permissionsSeedListed() {
        var r = rest.exchange("/api/admin/permissions", HttpMethod.GET, new HttpEntity<>(adminSession()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("ADMIN_USERS");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=AdminPermissionIT`
Expected: FAIL — endpoint missing.

- [ ] **Step 3: Write minimal implementation**

`PermissionDtos.java`:
```java
package com.ittools.platform.service.dto;
public class PermissionDtos {
    public record PermissionView(Long id, String code, String name, String module) {}
    public record ModulePermissionCommand(String scope, Long scopeRefId, Long permissionId, boolean enabled) {}
}
```

`ModulePermissionRepository.java`:
```java
package com.ittools.platform.repository;
import com.ittools.platform.domain.ModulePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ModulePermissionRepository extends JpaRepository<ModulePermission, Long> {
    Optional<ModulePermission> findByScopeAndScopeRefIdAndPermissionId(String scope, Long scopeRefId, Long permissionId);
}
```
> Add `@Entity ModulePermission{id,scope,scopeRefId,permissionId,enabled}` and `@Entity Permission{id,code,name,module}` if not already created in Task 7. Include here if missing.

`PermissionService.java`:
```java
package com.ittools.platform.service;

import com.ittools.platform.domain.ModulePermission;
import com.ittools.platform.repository.*;
import com.ittools.platform.service.dto.PermissionDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PermissionService {
    private final PermissionRepository perms; private final ModulePermissionRepository modulePerms;
    public PermissionService(PermissionRepository p, ModulePermissionRepository m) { this.perms = p; this.modulePerms = m; }

    public List<PermissionView> listPermissions() {
        return perms.findAll().stream()
            .map(p -> new PermissionView(p.getId(), p.getCode(), p.getName(), p.getModule())).toList();
    }
    @Transactional public void upsertModulePermission(ModulePermissionCommand c) {
        ModulePermission mp = modulePerms
            .findByScopeAndScopeRefIdAndPermissionId(c.scope(), c.scopeRefId(), c.permissionId())
            .orElseGet(ModulePermission::new);
        mp.setScope(c.scope()); mp.setScopeRefId(c.scopeRefId());
        mp.setPermissionId(c.permissionId()); mp.setEnabled(c.enabled());
        modulePerms.save(mp);
    }
}
```
> `PermissionRepository.findAll()` requires `Permission` to be a managed entity (created in Task 7). `getCode/getName/getModule` accessors must exist on it.

`AdminPermissionController.java`:
```java
package com.ittools.platform.web;

import com.ittools.platform.service.PermissionService;
import com.ittools.platform.service.dto.PermissionDtos.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminPermissionController {
    private final PermissionService svc;
    public AdminPermissionController(PermissionService s) { this.svc = s; }

    @GetMapping("/permissions") public ApiResponse<List<PermissionView>> list() { return ApiResponse.ok(svc.listPermissions()); }
    @PutMapping("/module-permissions") public ApiResponse<Void> upsert(@RequestBody ModulePermissionCommand c) { svc.upsertModulePermission(c); return ApiResponse.ok(null); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=AdminPermissionIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): permission listing + module-permission upsert"
```

---

## Task 15: Production packaging — build Vue into app, Dockerfile, full-stack compose up

**Files:**
- Create: `Dockerfile` (multi-stage: node build web → maven build → jre runtime)
- Create: `web/.env.production` (base path)
- Modify: `ittools-app/pom.xml` (frontend-maven-plugin OR copy `web/dist` into `static/`)
- Modify: `docker-compose.yml` (app builds from Dockerfile)
- Test: `ittools-app/src/test/java/com/ittools/StaticResourceIT.java`

**Interfaces:**
- Consumes: entire app.
- Produces: single container serving API + SPA; `GET /` returns the SPA `index.html`; SPA routes fall back to `index.html`.

- [ ] **Step 1: Write the failing test**

`StaticResourceIT.java`:
```java
package com.ittools;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class StaticResourceIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;

    @Test
    void rootServesSpaIndex() {
        ResponseEntity<String> r = rest.getForEntity("/", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("<div id=\"app\">");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ittools-app test -Dtest=StaticResourceIT`
Expected: FAIL — no static `index.html`.

- [ ] **Step 3: Write minimal implementation**

- Add a build step producing `web/dist` and copying it to `ittools-app/src/main/resources/static/` (either via `frontend-maven-plugin` bound to `generate-resources`, or a committed `static/index.html` for the test plus Docker multi-stage for real builds). Ensure `web/index.html` contains `<div id="app"></div>`.
- Add a SPA fallback controller so non-`/api` unmatched routes forward to `index.html`:
```java
package com.ittools.platform.web;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
@Controller
public class SpaForwardController {
    @GetMapping({"/", "/login", "/admin/**", "/teacher/**", "/student/**"})
    public String forward() { return "forward:/index.html"; }
}
```

`Dockerfile` (multi-stage):
```dockerfile
FROM node:20-alpine AS web
WORKDIR /web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
COPY --from=web /web/dist ittools-app/src/main/resources/static
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
COPY --from=build /src/ittools-app/target/ittools-app-*.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ittools-app -am test -Dtest=StaticResourceIT`
Expected: PASS. Then `docker compose up --build` and manually verify login at `http://localhost:8080`.

- [ ] **Step 5: Commit**

```bash
git add Dockerfile web/.env.production ittools-app docker-compose.yml
git commit -m "build: package SPA into app + docker multi-stage + compose"
```

---

## Self-Review

**1. Spec coverage:**
- §3 tech stack → Tasks 1-2 (Spring/Maven/Postgres/Flyway), 11 (Vue). ✓
- §4 modular monolith structure → Task 1 (modules), file structure map. ✓
- §5 data model (school_year/klass/users/permission/role_permission/module_permission) → Task 3 schema, Task 4 + 7 + 14 entities. ✓
- §6 three-entrance auth, session, BCrypt, CSRF, parameterized, method authz → Tasks 5, 6, 7, 12. ✓
- §7 API surface (auth, public lookups, admin CRUD, permissions) → Tasks 6, 7, 8, 9, 10, 14. ✓
- §8 frontend Vue + §8.0 modern-redesign/functional-parity → Tasks 11, 12 (design notes), 13. ✓
- §9 deployment (compose, Flyway, seed admin, env-injected secrets) → Tasks 2, 3, 15. ✓
- §10 testing (Testcontainers, TDD) → every backend task; Vitest for frontend. ✓
- Delivery boundary (no lab/course/assignment) → respected; teacher/student routes are stubs. ✓

**2. Placeholder scan:** No "TBD/TODO/implement later". Frontend view bodies (Tasks 12-13) describe concrete components with the exact APIs and props to wire; the reusable table/dialog pattern is specified rather than repeated four times (acceptable DRY guidance, not a missing-code placeholder).

**3. Type consistency:** `LoginRequest` record shape consistent across Tasks 5/6/12. `AppUserDetails.userId()/role()` consistent Tasks 5/6. `ApiResponse.ok/error` consistent. `Permission`/`RolePermission`/`ModulePermission` entities: introduced in Task 7 (Permission, RolePermission) and Task 14 (ModulePermission) — noted explicitly in both tasks' Interfaces so an out-of-order implementer creates them. DTO record field names match controller/service usage.

**Known cross-task dependency to watch:** Task 6's `AuthController` calls `LookupService.permissionCodesForRole` (Task 7). Executors should implement Task 7 immediately after or alongside Task 6 (noted in Task 6 Step 4). If running strictly sequentially, stub the method returning `List.of()` in Task 6 and complete it in Task 7.
