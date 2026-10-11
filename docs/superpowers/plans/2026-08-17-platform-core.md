# Platform-Core（平台基座）实现计划

> **面向智能体执行者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现本计划。步骤使用 checkbox（`- [ ]`）语法进行跟踪。

**目标：** 为 ITtools 重写项目搭建平台基座——一个模块化单体的 Spring Boot 应用 + Vue 3 单页应用 + PostgreSQL，提供三入口认证以及用户、班级、学年、角色/模块权限的管理端功能。

**架构：** Maven 多模块单体。`ittools-app` 是 Spring Boot 启动模块；`platform-core` 是一个库模块，承载用户/班级/学年/认证/权限的领域层、服务层、仓储层和 Web 层。PostgreSQL 数据库结构由 Flyway 做版本管理。前端是一个独立的 Vite/Vue 3 单页应用，同源部署，通过服务端会话 cookie 完成鉴权。

**技术栈：** **Java 8**，**Spring Boot 2.7.18**（Web、Security、Data JPA、Validation；javax.* 命名空间），PostgreSQL 16，Flyway（Spring Boot 托管的 8.5.x），Hibernate 5.6，Maven 3.6.1，JUnit 5，Testcontainers（Spring Boot 托管的 BOM），Vue 3，Vite，Pinia，Vue Router，Element Plus，axios，Vitest。

> **版本适配（用户指令，2026-08-17）：** 将框架版本适配为本地已安装的工具链。本地 JDK 为 **Java 8（1.8.0_221）**，因此后端目标框架为 **Spring Boot 2.7.18**——不允许使用 Spring Boot 3.x（它需要 Java 17）。以下任务中的示例代码是按 Java 17/Spring Boot 3 起草的；实现者**必须**依照紧跟在全局约束之后的 **Java 8 适配规则** 进行转换。这一转换不会丢失任何 platform-core 功能——只涉及语言/API 语法上的变化。

**设计文档：** `docs/superpowers/specs/2026-08-17-platform-core-design.md`

## 全局约束

- 运行时：**Java 8（1.8.0_221）**。框架：**Spring Boot 2.7.18**。禁止使用 Spring Boot 3.x（需要 Java 17）。Maven **3.6.1**。
- 数据库：仅使用 **PostgreSQL 16**。所有数据库结构变更均通过 **Flyway** 迁移脚本完成，存放于 `ittools-app/src/main/resources/db/migration`。`spring.jpa.hibernate.ddl-auto` 不得超出 `validate`。
- 编码：全程使用 **UTF-8**（源文件、数据库、HTTP）。
- 密码以 **BCrypt** 哈希存储；绝不明文存储，绝不记录到日志。
- 所有 SQL 均参数化（JPA / 绑定参数）。绝不使用字符串拼接的 SQL——任何情况下都不允许。
- 包基础路径：`com.ittools`。模块包：`com.ittools.platform`。
- 角色只能是以下三种：`ADMIN`、`TEACHER`、`STUDENT`（由数据库 CHECK 约束 + Java 枚举共同强制）。
- REST 响应统一使用信封结构 `{ "code": int, "message": string, "data": <payload> }`；`code` 为 0 表示成功。
- API 前缀为 `/api`。鉴权通过会话 cookie `JSESSIONID`；启用 CSRF（双重提交 `XSRF-TOKEN` cookie）。
- 涉及数据库的集成测试使用 **Testcontainers PostgreSQL 16**，不使用 H2。
- TDD：每个行为类任务都先写失败的测试。频繁提交（每个任务至少提交一次）。

## Java 8 / Spring Boot 2.7 适配规则（约束性规则——适用于每一个任务）

下文任务中的代码用于示意**结构、字段契约与行为**。实现时须将每段代码转换为 Java 8 / Spring Boot 2.7 写法。这些规则具有约束力；评审人将据此核查。

1. **不使用 `record`。** 文中出现的每个 DTO/record（`ApiResponse`、`LoginRequest`，以及所有 `*View`/`*Command`/`*Option`/`*Dtos`）都应改写为一个普通的 `final class`：私有 final 字段、一个全参构造函数（参数顺序与 record 相同），以及命名为 `field()` 的 getter——**必须与 record 的访问器名称保持一致**（例如 `userId()`、`role()`、`yearCode()`），以确保计划中所有调用点仍能编译通过。仅在测试需要时才添加 `equals`/`hashCode`。（也可以改用 Lombok 的 `@Value` 配合 `@Accessors(fluent = true)`——但优先使用普通类；除非某个任务已经列出 Lombok，否则不要额外引入。）
2. **命名空间：** 使用 `javax.persistence.*`（而非 `jakarta.persistence.*`）、`javax.servlet.*`（而非 `jakarta.servlet.*`）、`javax.validation.*`。
3. **不使用 `var`**——使用显式类型（测试代码中也一样）。
4. **不使用 switch 表达式**（`case X ->`）——使用带 `break`/`return` 的经典 `switch` 语句，或 `if/else` 链。影响到 `MultiLoginAuthenticationProvider.resolve`。
5. **不使用 `List.of()` / `Map.of()`**——使用 `java.util.Arrays.asList(...)`、`Collections.singletonList(...)` 或 `new ArrayList<>()`。影响到 `AppUserDetails.getAuthorities`、权限相关的桩代码。
6. **Stream/lambda/`Optional`** 均可正常使用（Java 8 支持）。但 `Stream.toList()` 是 Java 16+ 的特性——凡是任务中出现 `.toList()` 的地方一律改为 `.collect(Collectors.toList())`。
7. **Spring Security 5.7/5.8 API**（不是 Security 6）：
   - 使用 `.antMatchers(...)`，而不是 `.requestMatchers(...)`。
   - 使用 `@EnableGlobalMethodSecurity(prePostEnabled = true)`，而不是 `@EnableMethodSecurity`。
   - 保留 `SecurityFilterChain` bean 的写法（自 5.7 起支持）；不要使用已废弃的 `WebSecurityConfigurerAdapter`。
   - `CookieCsrfTokenRepository.withHttpOnlyFalse()`、`HttpStatusEntryPoint`、`HttpSessionSecurityContextRepository` 在 5.x 中均存在——按原样保留。
8. **文本块**（`"""`）是 Java 15+ 特性——本计划中不需要用到；使用普通字符串即可。
9. **Dockerfile（任务 15）：** 基础镜像使用 `maven:3.8-eclipse-temurin-8`（构建阶段）和 `eclipse-temurin:8-jre`（运行阶段），而不是文中展示的 `-17` 镜像。
10. **POM：** 父级 `spring-boot-starter-parent` 版本为 `2.7.18`；`<java.version>8</java.version>`；Flyway 依赖只用 `flyway-core`（不需要 `flyway-database-postgresql`——那是 Flyway 9+/SB3 才有的拆分；SB 2.7 的 `flyway-core` 已自带 Postgres 支持）。

如果在应用某条规则时发现某个行为确实无法在这套技术栈上实现，请停下来并报告（依用户指令），而不要悄悄丢弃该行为。

---

## 文件结构

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

**职责划分：** `domain` = 仅 JPA 实体。`repository` = Spring Data 接口。`service` = 业务规则 + 事务 + DTO 映射（该模块对外的公共接口层）。`web` = HTTP 控制器 + 响应信封 + 校验。`security` = 认证/鉴权相关配置。前端的 `api` = 传输层，`stores` = 会话状态，`views` = 页面。

---

## 任务 1：Maven 多模块骨架 + 应用可启动

**文件：**
- 新建：`pom.xml`、`ittools-app/pom.xml`、`modules/platform-core/pom.xml`
- 新建：`ittools-app/src/main/java/com/ittools/IttoolsApplication.java`
- 新建：`ittools-app/src/main/resources/application.yml`
- 测试：`ittools-app/src/test/java/com/ittools/ContextSmokeTest.java`

**接口：**
- 依赖：无（首个任务）。
- 产出：可启动的 Spring Boot 应用 `IttoolsApplication`；包含 `ittools-app`、`modules/platform-core` 两个模块的 Maven reactor。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=ContextSmokeTest`
预期结果：失败 — 尚无 `pom.xml`、也无 `IttoolsApplication` 类（编译/reactor 错误）。

- [ ] **步骤 3：编写最小实现**

父级 `pom.xml`：
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

`application.yml`（目前先保持最小配置）：
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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=ContextSmokeTest`
预期结果：通过（在排除数据库自动配置的情况下上下文正常加载）。

- [ ] **步骤 5：提交**

```bash
git add pom.xml ittools-app modules
git commit -m "chore: maven multi-module skeleton + bootable app"
```

---

## 任务 2：通过 docker-compose 引入 Postgres + Testcontainers 集成测试基类 + Flyway 跑通

**文件：**
- 新建：`docker-compose.yml`
- 新建：`ittools-app/src/test/java/com/ittools/support/AbstractPostgresIT.java`
- 新建：`ittools-app/src/main/resources/db/migration/V1__schema.sql`（内容为空但有效的占位表）
- 测试：`ittools-app/src/test/java/com/ittools/FlywayMigrationIT.java`

**接口：**
- 依赖：`IttoolsApplication`。
- 产出：`AbstractPostgresIT`（基类，启动一个共享的 Postgres 16 容器并注入 `spring.datasource.*`）；一条可正常运行的 Flyway 迁移链。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=FlywayMigrationIT`
预期结果：失败 — 没有 `V1__schema.sql`，Flyway 找不到迁移脚本 / 表不存在。

- [ ] **步骤 3：编写最小实现**

`V1__schema.sql`（临时的启动占位内容，会在任务 3 中被替换）：
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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=FlywayMigrationIT`
预期结果：通过 — Flyway 已应用 V1，存在历史记录行。

- [ ] **步骤 5：提交**

```bash
git add docker-compose.yml ittools-app/src
git commit -m "test: postgres testcontainers base + flyway smoke"
```

---

## 任务 3：V1 数据库结构（school_year、klass、users、permissions）+ V2 种子数据

**文件：**
- 修改：`ittools-app/src/main/resources/db/migration/V1__schema.sql`（替换占位内容）
- 新建：`ittools-app/src/main/resources/db/migration/V2__seed.sql`
- 测试：`ittools-app/src/test/java/com/ittools/SchemaShapeIT.java`

**接口：**
- 依赖：任务 2 产出的 Flyway 迁移链。
- 产出：`school_year, klass, users, permission, role_permission, module_permission` 各张表；权限种子数据行以及一个种子管理员账号（`login_name='admin'`）。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=SchemaShapeIT`
预期结果：失败 — 表/种子数据均不存在。

- [ ] **步骤 3：编写最小实现**

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

`V2__seed.sql`（下方 BCrypt 哈希对应密码 `admin123` — cost 10）：
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
> 说明：如果上面这个字面量在你的 Spring 版本中验证不通过，用 `new BCryptPasswordEncoder().encode("admin123")` 重新生成哈希值；这里给出的值是 `admin123` 的一个规范 BCrypt 示例摘要。

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=SchemaShapeIT`
预期结果：通过（3 个测试）。

- [ ] **步骤 5：提交**

```bash
git add ittools-app/src/main/resources/db/migration ittools-app/src/test
git commit -m "feat(db): core schema + platform permission seed + admin"
```

---

## 任务 4：领域实体 + 仓储层（school_year、klass、users）

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/domain/{Role,SchoolYear,Klass,User}.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/repository/{SchoolYearRepository,KlassRepository,UserRepository}.java`
- 测试：`ittools-app/src/test/java/com/ittools/platform/UserRepositoryIT.java`

**接口：**
- 依赖：任务 3 产出的数据库结构。
- 产出：
  - 枚举 `Role { ADMIN, TEACHER, STUDENT }`
  - `@Entity User` 字段：`Long id; Role role; String name; String loginName; String studentNo; String xjh; String passwordHash; Klass klass; SchoolYear enrollYear; boolean graduated; boolean enabled;`
  - `UserRepository extends JpaRepository<User,Long>`，包含 `Optional<User> findByLoginName(String)`、`Optional<User> findByXjh(String)`、`Optional<User> findByKlass_IdAndNameAndRole(Long classId, String name, Role role)`、`List<User> findByKlass_Id(Long)`。
  - `SchoolYearRepository`、`KlassRepository`（`List<Klass> findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(Long)`）。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=UserRepositoryIT`
预期结果：失败 — 实体/仓储尚未定义（编译错误）。

- [ ] **步骤 3：编写最小实现**

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

仓储接口：
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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=UserRepositoryIT`
预期结果：通过。

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(platform): core entities + repositories"
```

---

## 任务 5：BCrypt 编码器 + 多入口登录认证提供者

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/security/{AppUserDetails,LoginRequest,MultiLoginAuthenticationProvider}.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/security/SecurityBeans.java`（PasswordEncoder bean）
- 测试：`ittools-app/src/test/java/com/ittools/platform/AuthenticationProviderIT.java`

**接口：**
- 依赖：`UserRepository`、`Role`、`User`。
- 产出：
  - `record LoginRequest(String loginType, String yearCode, String className, String name, String xjh, String loginName, String password)` — `loginType ∈ {STUDENT_NAME, STUDENT_XJH, TEACHER, ADMIN}`。
  - `AppUserDetails implements UserDetails`，暴露 `Long userId()`、`Role role()`，授予权限 `ROLE_<role>`。
  - `MultiLoginAuthenticationProvider`（一个 `@Component`），使用 `PasswordEncoder` 将 `LoginRequest` 解析为已认证的 `AppUserDetails`。
  - `@Bean PasswordEncoder passwordEncoder()` = `BCryptPasswordEncoder`。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=AuthenticationProviderIT`
预期结果：失败 — 安全相关类尚不存在。

- [ ] **步骤 3：编写最小实现**

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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=AuthenticationProviderIT`
预期结果：通过（4 个测试）。如果 `adminSeedLoginSucceeds` 因种子哈希不匹配而失败，按任务 3 中所述重新生成 V2 哈希。

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main/java/com/ittools/platform/security ittools-app/src/test
git commit -m "feat(auth): bcrypt + multi-login authentication provider"
```

---

## 任务 6：安全配置、ApiResponse 信封、认证相关接口（login/logout/me）

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/ApiResponse.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/GlobalExceptionHandler.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/security/SecurityConfig.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/AuthController.java`
- 测试：`ittools-app/src/test/java/com/ittools/platform/AuthEndpointIT.java`

**接口：**
- 依赖：`MultiLoginAuthenticationProvider`、`AppUserDetails`。
- 产出：
  - `ApiResponse<T>` 的静态工厂方法 `ok(T)`、`error(int, String)`。
  - `POST /api/auth/login`（请求体为 JSON 形式的 `LoginRequest`）→ 建立会话，返回 `{userId, name, role, permissions[]}`。
  - `POST /api/auth/logout` → 使会话失效。
  - `GET /api/auth/me` → 返回当前登录用户信息，未登录则返回 401。
  - `SecurityConfig`：`/api/auth/**` 与 `/api/public/**` 全部放行；其余接口均需认证；CSRF 通过 `CookieCsrfTokenRepository.withHttpOnlyFalse()` 实现。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=AuthEndpointIT`
预期结果：失败 — 接口/安全配置均不存在（大概率返回 404/500）。

- [ ] **步骤 3：编写最小实现**

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
> 需要添加 `import org.springframework.security.core.annotation.AuthenticationPrincipal;`

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=AuthEndpointIT`
预期结果：通过。（需要任务 7 中的 `LookupService.permissionCodesForRole`——如果编译失败，先实现任务 7，或者先让该方法返回 `List.of()` 作为桩实现，之后在任务 7 中补全。）

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(auth): security config + login/logout/me endpoints"
```

---

## 任务 7：LookupService + 公开的登录下拉框接口

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/LookupService.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/repository/{PermissionRepository,RolePermissionRepository}.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/PublicLookupController.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/dto/LookupDtos.java`
- 测试：`ittools-app/src/test/java/com/ittools/platform/PublicLookupIT.java`

**接口：**
- 依赖：`SchoolYearRepository`、`KlassRepository`、`UserRepository`、`PermissionRepository`。
- 产出：
  - `LookupService.permissionCodesForRole(Role)` → `List<String>`。
  - `LookupService.activeYears()`、`classesOf(Long yearId)`、`studentsOf(Long classId)`，返回 DTO record `YearOption(id, yearCode, label)`、`ClassOption(id, name)`、`StudentOption(id, name)`。
  - `GET /api/public/school-years`、`GET /api/public/classes?yearId=`、`GET /api/public/students?classId=`。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=PublicLookupIT`
预期结果：失败 — 接口/服务均不存在。

- [ ] **步骤 3：编写最小实现**

`PermissionRepository.java` / `RolePermissionRepository.java`：
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
> `Permission` 与 `RolePermission` 实体：按数据库结构对应创建（`Permission{id,code,name,module}`，`@Entity RolePermission{@EmbeddedId or @IdClass (role, permissionId)}`）。在本任务的实现中一并创建它们。

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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=PublicLookupIT,AuthEndpointIT`
预期结果：通过（此时 AuthEndpointIT 也应完全变绿，使用真实的权限数据）。

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(platform): lookup service + public login dropdown endpoints"
```

---

## 任务 8：管理端 SchoolYear（学年）CRUD

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/SchoolYearService.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/AdminSchoolYearController.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/dto/SchoolYearDtos.java`
- 测试：`ittools-app/src/test/java/com/ittools/platform/AdminSchoolYearIT.java`

**接口：**
- 依赖：`SchoolYearRepository`、安全权限（ADMIN 角色）。
- 产出：
  - DTO：`SchoolYearView(Long id,String yearCode,String label,boolean active)`、`SchoolYearCommand(String yearCode,String label,boolean active)`。
  - `SchoolYearService`：`list()`、`create(cmd)`、`update(id,cmd)`、`delete(id)`。
  - `/api/admin/school-years` 下的接口（GET 列表、POST、PUT/{id}、DELETE/{id}），仅限 ADMIN。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=AdminSchoolYearIT`
预期结果：失败 — 接口尚不存在。

- [ ] **步骤 3：编写最小实现**

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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=AdminSchoolYearIT`
预期结果：通过。

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): school year CRUD"
```

---

## 任务 9：管理端 Klass（班级）CRUD

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/KlassService.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/AdminKlassController.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/dto/KlassDtos.java`
- 测试：`ittools-app/src/test/java/com/ittools/platform/AdminKlassIT.java`

**接口：**
- 依赖：`KlassRepository`、`SchoolYearRepository`、管理员会话辅助方法（将任务 8 中的 `adminSession()` 辅助方法复制一份到本测试类中——不要在多个任务之间共享测试基础设施）。
- 产出：
  - DTO：`KlassView(Long id,Long schoolYearId,String yearLabel,String name,int displayOrder)`、`KlassCommand(Long schoolYearId,String name,int displayOrder)`。
  - `KlassService`：`listByYear(Long yearId)`、`create(cmd)`、`update(id,cmd)`、`delete(id)`。
  - `/api/admin/classes` 接口（GET `?yearId=`、POST、PUT/{id}、DELETE/{id}），仅限 ADMIN。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=AdminKlassIT`
预期结果：失败 — 接口尚不存在。

- [ ] **步骤 3：编写最小实现**

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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=AdminKlassIT`
预期结果：通过。

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): class CRUD"
```

---

## 任务 10：管理端 User（用户）CRUD（创建/编辑/删除/重置密码/毕业/启停用）

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/UserService.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/AdminUserController.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/dto/UserDtos.java`
- 测试：`ittools-app/src/test/java/com/ittools/platform/AdminUserIT.java`

**接口：**
- 依赖：`UserRepository`、`KlassRepository`、`SchoolYearRepository`、`PasswordEncoder`。
- 产出：
  - DTO：
    - `UserView(Long id,String role,String name,String loginName,String studentNo,String xjh,Long classId,String className,boolean graduated,boolean enabled)`
    - `CreateUserCommand(String role,String name,String loginName,String studentNo,String xjh,Long classId,Long enrollYearId,String password)`
    - `UpdateUserCommand(String name,String loginName,String studentNo,String xjh,Long classId,Long enrollYearId)`
    - `ResetPasswordCommand(String password)`
  - `UserService`：`list(String roleFilter, Long classId)`、`create(cmd)`、`update(id,cmd)`、`delete(id)`、`resetPassword(id,cmd)`、`setGraduated(id,boolean)`、`setEnabled(id,boolean)`。写入密码时始终经过 BCrypt 编码。
  - `/api/admin/users` 接口：GET `?role=&classId=`、POST、PUT/{id}、DELETE/{id}、POST/{id}/reset-password、POST/{id}/graduate?value=、POST/{id}/enabled?value=。仅限 ADMIN。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=AdminUserIT`
预期结果：失败 — 接口尚不存在。

- [ ] **步骤 3：编写最小实现**

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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=AdminUserIT`
预期结果：通过。

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): user CRUD + reset-password/graduate/enable"
```

---

## 任务 11：Vue 单页应用脚手架 + http 客户端 + 认证 store + 启动时拉取 /me

**文件：**
- 新建：`web/package.json`、`web/vite.config.ts`、`web/index.html`、`web/src/main.ts`、`web/src/App.vue`
- 新建：`web/src/api/http.ts`、`web/src/stores/auth.ts`、`web/src/router/index.ts`
- 测试：`web/src/stores/__tests__/auth.spec.ts`（Vitest）

**接口：**
- 依赖：后端接口 `/api/auth/me`、`/api/auth/login`、`/api/auth/logout`。
- 产出：
  - `http` axios 实例：baseURL 为 `/api`，`withCredentials: true`，在有副作用的请求中读取 `XSRF-TOKEN` cookie 并写入 `X-XSRF-TOKEN` 请求头。
  - Pinia 的 `useAuthStore`，状态为 `{ user: null|{userId,name,role,permissions[]} }`，动作有 `fetchMe()`、`login(payload)`、`logout()`，getter 有 `isAuthenticated`、`hasPermission(code)`。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`cd web && npm install && npx vitest run`
预期结果：失败 — store/http 尚未实现。

- [ ] **步骤 3：编写最小实现**

`web/package.json`（关键依赖）：`vue@^3.4`、`vue-router@^4`、`pinia@^2`、`element-plus@^2`、`axios@^1`，开发依赖 `vite@^5`、`@vitejs/plugin-vue`、`vitest`、`@vue/test-utils`、`typescript`。脚本：`dev`、`build`、`test: vitest run`。

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

`web/src/router/index.ts`、`web/src/main.ts`、`web/src/App.vue`、`web/index.html`：标准的 Vue + Pinia + Router + Element Plus 启动脚手架；路由包含 `/login` 与 `/admin`（带守卫，`!isAuthenticated` 时重定向到 `/login`）。

- [ ] **步骤 4：运行测试确认通过**

运行：`cd web && npx vitest run`
预期结果：通过。

- [ ] **步骤 5：提交**

```bash
git add web
git commit -m "feat(web): vue scaffold + http client + auth store"
```

---

## 任务 12：登录页（三入口）——现代化重新设计，功能对等

**文件：**
- 新建：`web/src/views/login/LoginView.vue`
- 新建：`web/src/api/auth.ts`
- 测试：`web/src/views/login/__tests__/LoginView.spec.ts`

**接口：**
- 依赖：`/api/public/school-years|classes|students`、`useAuthStore().login`。
- 产出：一个采用**分标签页**现代布局（Element Plus tabs）的登录页面，覆盖原有的全部入口：`学生(班级+姓名)`、`学生(学籍号)`、`教师`、`管理员`。在“学生姓名”标签页中：年级→班级→姓名的级联下拉选择（数据从公开接口加载）。触发 `login({loginType, ...})`。

**设计说明（依据设计文档 §8.0）：** 不要照搬旧版 `login2.jpg` 的固定表格布局。使用简洁居中的卡片式布局、响应式设计、Element Plus 表单组件、行内校验，以及加载/错误状态。原有四条登录路径必须全部保持可用。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`cd web && npx vitest run LoginView`
预期结果：失败 — 组件尚不存在。

- [ ] **步骤 3：编写最小实现**

`web/src/api/auth.ts`:
```ts
import { http } from './http'
export const publicApi = {
  years: () => http.get('/public/school-years').then(r => r.data.data),
  classes: (yearId: number) => http.get('/public/classes', { params: { yearId } }).then(r => r.data.data),
  students: (classId: number) => http.get('/public/students', { params: { classId } }).then(r => r.data.data)
}
```

`LoginView.vue`：使用 `<el-tabs>` 承载四个 `<el-tab-pane>`（标签分别为 `学生 · 班级姓名`、`学生 · 学籍号`、`教师`、`管理员`），每个都是一个小型 `<el-form>`；学生姓名标签页中用三个 `<el-select>`（年级/班级/姓名）对接 `publicApi`；提交时调用 `useAuthStore().login({...})`，随后按角色跳转到 `/admin`（ADMIN）、`/teacher`、`/student`（后两者是留给后续子项目的占位路由——目前先跳转到一个“敬请期待”的桩页面）。使用居中的 `<el-card>`，`max-width: 420px`，错误提示通过 `<el-alert>` 展示。

- [ ] **步骤 4：运行测试确认通过**

运行：`cd web && npx vitest run LoginView`
预期结果：通过。

- [ ] **步骤 5：提交**

```bash
git add web/src/views/login web/src/api/auth.ts
git commit -m "feat(web): modern three-entrance login view"
```

---

## 任务 13：管理端布局 + 学年/班级/用户管理页面 + 权限页面

**文件：**
- 新建：`web/src/layouts/AdminLayout.vue`
- 新建：`web/src/api/admin.ts`
- 新建：`web/src/views/admin/{SchoolYearView,ClassView,UserView,PermissionView}.vue`
- 修改：`web/src/router/index.ts`（嵌套 admin 子路由）
- 测试：`web/src/views/admin/__tests__/SchoolYearView.spec.ts`

**接口：**
- 依赖：`/api/admin/school-years|classes|users|permissions` 以及（任务 14 产出的）`/api/admin/module-permissions`。
- 产出：`adminApi`（带类型的 CRUD 封装）+ `AdminLayout` 内的四个管理页面（Element Plus `<el-container>` 配侧边菜单）。表格配合新增/编辑对话框表单、删除确认，用户页面还包括角色/班级筛选、重置密码、毕业/启停用开关。

**设计说明：** 统一的设计规范、响应式表格、基于对话框的编辑表单——四个页面复用同一套表格/对话框模式（遵循 DRY 原则）。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`cd web && npx vitest run SchoolYearView`
预期结果：失败 — 视图/api 尚不存在。

- [ ] **步骤 3：编写最小实现**

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

页面：`SchoolYearView.vue`（表格 + 新增/编辑对话框 + 删除）、`ClassView.vue`（年级选择器 + 班级表格）、`UserView.vue`（角色/班级筛选 + 用户表格 + 新增/编辑对话框 + 重置密码/毕业/启停用操作）、`PermissionView.vue`（权限列表 + 模块权限开关）。`AdminLayout.vue` 提供外层框架，左侧 `<el-menu>` 链接到这四个路由。路由将它们嵌套在 `/admin` 之下。

- [ ] **步骤 4：运行测试确认通过**

运行：`cd web && npx vitest run`
预期结果：通过（前端全部测试）。

- [ ] **步骤 5：提交**

```bash
git add web/src
git commit -m "feat(web): admin layout + year/class/user/permission management"
```

---

## 任务 14：管理端权限列表 + 模块权限开关（后端）

**文件：**
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/PermissionService.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/web/AdminPermissionController.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/repository/ModulePermissionRepository.java`
- 新建：`modules/platform-core/src/main/java/com/ittools/platform/service/dto/PermissionDtos.java`
- 测试：`ittools-app/src/test/java/com/ittools/platform/AdminPermissionIT.java`

**接口：**
- 依赖：`PermissionRepository`、`ModulePermissionRepository`。
- 产出：
  - DTO：`PermissionView(Long id,String code,String name,String module)`、`ModulePermissionCommand(String scope,Long scopeRefId,Long permissionId,boolean enabled)`。
  - `PermissionService`：`listPermissions()`、`upsertModulePermission(cmd)`。
  - 接口 `GET /api/admin/permissions`、`PUT /api/admin/module-permissions`。仅限 ADMIN。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=AdminPermissionIT`
预期结果：失败 — 接口尚不存在。

- [ ] **步骤 3：编写最小实现**

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
> 如果在任务 7 中尚未创建，需在此处补充添加 `@Entity ModulePermission{id,scope,scopeRefId,permissionId,enabled}` 和 `@Entity Permission{id,code,name,module}`。

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
> `PermissionRepository.findAll()` 要求 `Permission` 是一个受管实体（在任务 7 中创建）。它必须具备 `getCode/getName/getModule` 访问器。

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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=AdminPermissionIT`
预期结果：通过。

- [ ] **步骤 5：提交**

```bash
git add modules/platform-core/src/main ittools-app/src/test
git commit -m "feat(admin): permission listing + module-permission upsert"
```

---

## 任务 15：生产环境打包——将 Vue 构建产物纳入应用、Dockerfile、全栈 compose 启动

**文件：**
- 新建：`Dockerfile`（多阶段构建：node 构建前端 → maven 构建 → jre 运行时）
- 新建：`web/.env.production`（基础路径）
- 修改：`ittools-app/pom.xml`（使用 frontend-maven-plugin，或将 `web/dist` 拷贝进 `static/`）
- 修改：`docker-compose.yml`（app 从 Dockerfile 构建）
- 测试：`ittools-app/src/test/java/com/ittools/StaticResourceIT.java`

**接口：**
- 依赖：整个应用。
- 产出：单一容器同时提供 API 与单页应用；`GET /` 返回单页应用的 `index.html`；单页应用路由回落到 `index.html`。

- [ ] **步骤 1：编写失败测试**

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

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -q -pl ittools-app test -Dtest=StaticResourceIT`
预期结果：失败 — 没有静态的 `index.html`。

- [ ] **步骤 3：编写最小实现**

- 添加一个构建步骤生成 `web/dist` 并拷贝到 `ittools-app/src/main/resources/static/`（可以是绑定在 `generate-resources` 阶段的 `frontend-maven-plugin`，也可以是为测试提交一份 `static/index.html`，再配合 Docker 多阶段构建完成真正的构建）。确保 `web/index.html` 中包含 `<div id="app"></div>`。
- 添加一个单页应用回落控制器，让非 `/api` 且未匹配到的路由转发到 `index.html`：
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

`Dockerfile`（多阶段构建）：
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

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -q -pl ittools-app -am test -Dtest=StaticResourceIT`
预期结果：通过。随后执行 `docker compose up --build`，并在 `http://localhost:8080` 手动验证登录流程。

- [ ] **步骤 5：提交**

```bash
git add Dockerfile web/.env.production ittools-app docker-compose.yml
git commit -m "build: package SPA into app + docker multi-stage + compose"
```

---

## 自评审

**1. 设计文档覆盖情况：**
- §3 技术栈 → 任务 1-2（Spring/Maven/Postgres/Flyway）、任务 11（Vue）。✓
- §4 模块化单体结构 → 任务 1（模块划分）、文件结构图。✓
- §5 数据模型（school_year/klass/users/permission/role_permission/module_permission）→ 任务 3 的数据库结构，任务 4 + 7 + 14 的实体。✓
- §6 三入口认证、会话、BCrypt、CSRF、参数化、方法级鉴权 → 任务 5、6、7、12。✓
- §7 API 接口面（认证、公开查询、管理端 CRUD、权限）→ 任务 6、7、8、9、10、14。✓
- §8 前端 Vue + §8.0 现代化重设计/功能对等 → 任务 11、12（设计说明）、13。✓
- §9 部署（compose、Flyway、种子管理员、环境变量注入的密钥）→ 任务 2、3、15。✓
- §10 测试（Testcontainers、TDD）→ 每个后端任务；前端使用 Vitest。✓
- 交付边界（不涉及实验/课程/作业内容）→ 已遵守；教师端/学生端路由均为占位桩。✓

**2. 占位内容排查：** 未发现 "TBD/TODO/日后实现" 之类的占位说明。前端视图部分（任务 12-13）以具体组件描述了需要对接的确切 API 与 props；可复用的表格/对话框模式是作为规范说明给出的，而非重复写四遍（这是合理的 DRY 指导，不是缺失代码的占位）。

**3. 类型一致性：** `LoginRequest` 这个 record 的结构在任务 5/6/12 中保持一致。`AppUserDetails.userId()/role()` 在任务 5/6 中保持一致。`ApiResponse.ok/error` 保持一致。`Permission`/`RolePermission`/`ModulePermission` 实体：分别在任务 7（Permission、RolePermission）和任务 14（ModulePermission）中引入——已在两个任务的“接口”部分明确标注，以便乱序实现者也能正确创建它们。DTO record 的字段名称与控制器/服务层的使用保持一致。

**需要留意的跨任务依赖：** 任务 6 的 `AuthController` 调用了 `LookupService.permissionCodesForRole`（属于任务 7）。执行者应在完成任务 6 之后立即实现任务 7，或与任务 6 同步进行（已在任务 6 步骤 4 中标注）。若严格按顺序执行，可在任务 6 中先让该方法返回 `List.of()` 作为桩实现，再在任务 7 中补全。
