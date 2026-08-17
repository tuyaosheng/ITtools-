# 设计文档 — 子项目 0：平台基座（platform-core）

- 日期：2026-08-17
- 状态：已定稿（待用户复核）
- 范围：ITtools 教学平台现代化重写 · 子项目 0 · 平台基座

---

## 1. 背景与上下文

原系统 **ITtools 3.51** 是一套面向中小学信息技术课程的「教学 + 自动评分 + 机房管理」平台，运行于 Windows Server 2003 + IIS 6 + .NET Framework 2.0/3.5，采用 ASP.NET Web Forms(VB.NET)，核心逻辑编译在无源码的 `ittool3.0.dll` 中，数据库为 SQL Server 2005，全局 GB2312 编码，依赖大量已淘汰技术（Flash/FlashPaper、Office/WPS COM 互操作、Jet OLEDB、本地评分 exe）。

本项目对其进行**现代技术栈彻底重写**，目标能运行于现代系统（Windows Server 2019/2022 或 Linux）。

### 已确定的全局重构基调
- 彻底重写 · **Java 17+ / Spring Boot 3.x** · 单校机房 LAN · 单租户
- 保留主流题型 + 自动评分（**评分规则现代化重新设计**，不逆向旧引擎），砍掉 Flash/FrontPage/FlashPaper 等死技术
- **全新 PostgreSQL 数据库，干净重新建模，不迁移历史数据**
- 前端 **Vue 3 SPA + REST/WebSocket**，**界面现代化重设计但功能等价**（详见 §8.0）

### 平台整体拆分（8 个子项目，本文档为子项目 0）
| # | 子项目 | 依赖 |
|---|--------|------|
| **0** | **平台基座**（用户·班级·年级·认证·权限·部署） | 无 |
| 1 | 机房与机位管理 | 0 |
| 2 | 课堂运行核心 | 0 |
| 3 | 作业提交与展示 | 2 |
| 4 | 自动评分引擎 | 3 |
| 5 | 在线测验与题库 | 2 |
| 6 | 课堂互动（讨论/举手/抢答/分组/广播/监控） | 2 |
| 7 | 资源与教案 | 2 |

建设顺序：0 →（1、2 并行）→ 3 →（4、5、6、7 并行）。每个子项目走各自「设计 → 规划 → 实现」循环。

---

## 2. 本子项目目标与非目标

### 目标（交付边界）
1. 可运行的模块化单体骨架（Spring Boot 应用 + 前端 SPA + PostgreSQL）
2. 三角色认证与会话（ADMIN / TEACHER / STUDENT），还原原系统登录方式
3. 用户、班级、入学年份（年级）的管理（管理员侧）
4. 角色-模块权限框架（对应原 `stumodule` / `temodule`）
5. 一键本地开发/部署（Docker Compose + Flyway 自动建表 + 种子管理员）

### 非目标（明确不在本子项目）
- 机房/机位/IP/座位（子项目 1）
- 课程、开课、进课、作业、评分、测验、互动、资源（子项目 2+）
- 多租户、多校（本项目单校单租户）
- 历史数据迁移

---

## 3. 技术选型

> **版本适配原则（用户指示 2026-08-17）**：以本地已装版本为准，反向选择适配的框架版本。本地 JDK 为 **Java 8 (1.8.0_221)**，故后端锁定 **Spring Boot 2.7.18**（支持 Java 8 的最后一条线）。仅当某功能因版本实现不了时才停下确认——经评估，本子项目全部功能在此栈上均可实现，无功能丢失。

| 层 | 选择 | 说明 |
|---|---|---|
| 语言/运行时 | **Java 8 (1.8.0_221)** | 本地版本，决定后端上限 |
| 后端框架 | **Spring Boot 2.7.18** | Web、Security、Validation；javax.* 命名空间 |
| 持久化 | Spring Data JPA (Hibernate 5.6) | javax.persistence |
| 数据库 | **PostgreSQL 16** | 容器 postgres:16（非本地安装） |
| Schema 版本 | **Flyway**（SB 2.7 管理版本 8.5.x） | 版本化迁移脚本 |
| 实时通信 | Spring WebSocket (STOMP) | 基础设施在本层预留，具体功能在子项目 2/6 |
| 前端 | **Vue 3 + Vite + Element Plus** | SPA，Pinia、Vue Router、axios；Node 24/npm 11 |
| 认证 | Spring Security 5.7/5.8（SecurityFilterChain） + 服务端 Session | session cookie |
| 构建 | Maven 3.6.1（多模块） | SB 2.7 需 Maven 3.5+ |
| 部署 | Docker 28 + Docker Compose（app + postgres） | |
| 编码 | 全程 UTF-8 | 摆脱 GB2312 |

**Java 8 语言约束**（不丢功能，仅写法降级）：不用 `record`（改普通类）、`var`、switch 表达式、`List.of()`；Spring Security 用 5.x 写法（`antMatchers`、`@EnableGlobalMethodSecurity(prePostEnabled=true)`）。
> 迁移提示：将来若本地安装 JDK 17+，可平滑升级到 Spring Boot 3.x / jakarta.* 命名空间。

---

## 4. 架构

### 4.1 模块化单体结构
```
ittools/
├─ pom.xml                    (父 POM，多模块聚合)
├─ ittools-app/               Spring Boot 启动 + 全局配置 + 模块装配
│   └─ src/main/resources/
│       ├─ application.yml
│       └─ db/migration/      Flyway 脚本 (V1__init.sql ...)
├─ modules/
│   ├─ platform-core/         用户·班级·年级·认证·权限          ← 本子项目
│   ├─ lab/                   机房机位（子项目 1，占位）
│   ├─ classroom/             课堂运行（子项目 2，占位）
│   └─ …
└─ web/                       Vue 前端（独立 Vite 工程）
```
- 模块之间**只经 service 接口**调用，不直接跨模块访问对方的 repository/实体。
- `ittools-app` 只做装配与全局横切（安全、异常处理、CORS、序列化）。
- 前端 `web/` 独立构建；生产可打包进 `ittools-app` 的 static 资源，或独立容器 + 反向代理（部署方式在 §8 定）。

### 4.2 platform-core 模块内部分层
```
platform-core/
├─ domain/          实体 (User, Klass, SchoolYear, Role, Permission, ModulePermission)
├─ repository/      Spring Data JPA 接口
├─ service/         业务逻辑 (对外暴露的模块接口)
├─ web/             REST Controller + DTO
└─ security/        登录/会话/授权配置
```

---

## 5. 数据模型（PostgreSQL）

> 命名：表名小写下划线、复数；主键 `id BIGINT GENERATED ALWAYS AS IDENTITY`。字段旁注明对应原 `it_user` 等旧字段，便于团队理解语义（不用于数据迁移）。

### 5.1 `school_year`（入学年份 / 年级）
对应原 `it_user.insc`（入学年份字符串，如 "2021"）。
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| year_code | varchar(16) unique | 入学年份码，如 "2021" |
| label | varchar(64) | 显示名，如 "2021 级" |
| active | boolean | 是否在校 |

### 5.2 `klass`（班级）
对应原 `it_user.class`；原系统班级标识 = `insc + class` 拼接。
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| school_year_id | bigint FK → school_year | 所属年级 |
| name | varchar(64) | 班级名，如 "1班" |
| display_order | int | 排序 |
| unique(school_year_id, name) | | |

### 5.3 `users`（师生管理员统一表）
对应原 `it_user`（`isteacher` 区分师生，`byflg` 毕业标志，`num` 学号）。
| 字段 | 类型 | 说明 | 旧字段 |
|---|---|---|---|
| id | bigint PK | | id |
| role | varchar(16) | ADMIN / TEACHER / STUDENT | isteacher |
| name | varchar(64) | 姓名 | name |
| login_name | varchar(64) | 登录名（教师/管理员用；学生可空） | |
| student_no | varchar(32) | 学号 | num |
| xjh | varchar(32) | 学籍号（学生登录用，唯一） | 学籍号 |
| password_hash | varchar(100) | **BCrypt** | password（旧疑似明文）|
| class_id | bigint FK → klass null | 学生所属班级；教师/管理员可空 | insc+class |
| enroll_year_id | bigint FK → school_year null | | insc |
| graduated | boolean default false | 毕业/离校标志 | byflg |
| enabled | boolean default true | 账号启用 | |
| created_at / updated_at | timestamptz | | |

约束/索引：
- 学生：`(class_id, name)` 用于「班级+姓名」登录下拉；`xjh` unique（学籍号登录）
- 教师/管理员：`login_name` unique
- role 用 CHECK 约束限定枚举值

### 5.4 权限模型
对应原 `stumodule` / `temodule`（控制师生可见功能模块）。

`permission`（功能模块/权限点，静态种子数据）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| code | varchar(64) unique | 如 `COURSE_WORD`、`TEST`、`DISCUSS` |
| name | varchar(128) | 中文名 |
| module | varchar(32) | 归属子系统 |

`role_permission`（角色→权限，默认授权）
| role | varchar(16) | |
| permission_id | bigint FK | |

`module_permission`（按班级/用户的模块开关，还原原系统"给某班开放某模块"）
| id | bigint PK | |
| scope | varchar(16) | GLOBAL / CLASS / USER |
| scope_ref_id | bigint null | class_id 或 user_id |
| permission_id | bigint FK | |
| enabled | boolean | |

> 注：权限点清单会随子项目 1-7 逐步补全；本层先建表结构 + 平台基座相关权限种子。

---

## 6. 认证与授权

### 6.1 登录方式（还原原系统三入口）
1. **学生 - 班级+姓名+密码**：选年级→班级→姓名（下拉，来自 `users` where role=STUDENT, class）→输密码
2. **学生 - 学籍号+密码**：`xjh` + 密码
3. **教师 - 教师名+密码**：`login_name` + 密码
4. **管理员 - 独立入口**：`login_name` + 密码，role=ADMIN

> 前端提供班级/姓名级联下拉的只读查询接口（无需登录即可拉取班级、姓名列表，与原 `index.aspx` 的 SqlDataSource 行为一致，但改为参数化 REST）。

### 6.2 会话与安全
- Spring Security：自定义 `AuthenticationProvider` 支持上述多种登录标识 → 统一解析到 `users`
- 服务端 Session（`JSESSIONID` cookie）；SPA 同源部署，带 cookie 请求
- 密码 **BCrypt** 哈希；提供初始化时的种子管理员（首次登录强制改密，后续子项目可选）
- CSRF：SPA + cookie 场景开启 Spring Security CSRF（前端读取 XSRF-TOKEN）
- 授权：基于 role + permission 的方法级注解（`@PreAuthorize`）

### 6.3 修正的历史安全问题
- 旧系统全字符串拼接 SQL → 本项目**全部参数化（JPA/PreparedStatement）**
- 旧系统密码疑似明文 → BCrypt
- 旧系统 `validateRequest=false` → 现代框架默认输入校验 + 输出转义

---

## 7. REST API 面（platform-core）

> 前缀 `/api`。返回统一 JSON 包裹 `{code, message, data}`。

认证/会话
- `POST /api/auth/login`（body 含 loginType + 凭据）
- `POST /api/auth/logout`
- `GET  /api/auth/me`（当前用户 + 权限）
- 登录辅助（免登录只读）：`GET /api/public/school-years`、`GET /api/public/classes?yearId=`、`GET /api/public/students?classId=`

管理员 - 用户/班级/年级
- `GET/POST/PUT/DELETE /api/admin/school-years`
- `GET/POST/PUT/DELETE /api/admin/classes`
- `GET/POST/PUT/DELETE /api/admin/users`（支持按班级/角色筛选、批量导入、改密、毕业/启用）
- 权限：`GET /api/admin/permissions`、`PUT /api/admin/module-permissions`

---

## 8. 前端（Vue 3）

### 8.0 设计原则：现代化外观 + 功能等价（贯穿全平台）
> 本原则在基座确立，适用于子项目 1-7 的所有前端。

- **不照搬旧界面**：原系统是固定 1024×768、表格套表格、背景图拼版式的 WebForms 界面。新前端采用**现代设计系统**：响应式布局、统一的色彩/间距/字体规范、卡片与留白、清晰的信息层级、组件化交互（Element Plus 基础上定制主题）。
- **功能等价是硬约束**：视觉与交互可以重新设计，但**原系统的每一个功能点、每一条操作路径都必须保留**，不得因"现代化"而丢失或简化行为。每个页面重写时以"原功能清单"逐项核对。
- **交互现代化**：以往整页回发 → 改为局部异步更新、即时校验、加载/成功/错误状态反馈、可访问性（键盘/对比度）。
- **一致性**：全平台共用一套设计 token 与通用组件（按钮、表格、表单、对话框、分页、上传、通知），避免各页各style。
- **落地方式**：具体页面的视觉稿在各页实现阶段确定（可届时用可视化 mockup 对比方案）；本文档只确立原则与组件库基线。

### 8.1 结构
```
web/
├─ src/
│   ├─ api/            axios 封装 + 各模块请求
│   ├─ router/         路由（按角色守卫）
│   ├─ stores/         Pinia（auth、user）
│   ├─ layouts/        角色布局外壳
│   ├─ views/
│   │   ├─ login/      三入口登录页（还原 index.aspx 的多视图）
│   │   └─ admin/      用户/班级/年级/权限管理
│   └─ components/     通用组件
└─ vite.config.ts
```
本子项目交付的界面：登录页（学生班级+姓名 / 学籍号 / 教师 / 管理员）+ 管理员的用户/班级/年级/权限管理 CRUD。

---

## 9. 部署与开发

- `docker-compose.yml`：服务 `app`（Spring Boot）+ `db`（postgres:16）
- **Flyway** 在应用启动时自动执行 `db/migration` 建表 + 插入种子（权限点、初始管理员）
- 本地：`docker compose up` 一键起全栈；前端开发用 `vite dev` + 代理到后端
- 配置：`application.yml` 用环境变量注入 DB 连接/凭据；不硬编码密码（修正旧系统 web.config 明文连接串）
- 目标运行环境：Windows Server 2019/2022 或 Linux；PostgreSQL 可原生安装为服务或用容器

---

## 10. 测试策略

- 单元测试：service 层业务逻辑（JUnit 5 + Mockito）
- 集成测试：repository/API 层用 **Testcontainers（PostgreSQL）**，真实库验证
- 安全测试：登录多入口、权限拦截、密码哈希、CSRF
- 采用 TDD：先写失败测试再实现（实现阶段遵循 test-driven-development 技能）

---

## 11. 待办 / 后续澄清
- 权限点（permission code）完整清单随子项目 1-7 定义时补全
- 学生密码初始值策略（原系统默认密码规则？本项目定为管理员设置/批量导入时指定）
- 前端生产部署形态（打包进后端 static vs 独立容器 + Nginx）——部署阶段定，默认打包进后端简化运维
- 批量导入学生的文件格式（Excel/CSV 模板）——用户管理实现时定

---

## 12. 变更记录
- 2026-08-17：初稿，经与用户逐项确认（技术栈 Java+Spring、数据库 PostgreSQL 16、前端 Vue SPA、Session 认证、全新建库不迁数据、评分规则现代化重设计、单校 LAN 单租户）后定稿。
- 2026-08-17：追加 §8.0 前端设计原则——界面现代化重设计但功能等价（贯穿全平台），应用户要求。
