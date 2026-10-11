-- 子项目 2：课堂运行核心

-- 学期（原 it_yearlist 学年 + it_termlist 学期；"学期维护"）
CREATE TABLE teaching_term (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_name     VARCHAR(32) NOT NULL,          -- 如 2024-2025
    term_name     VARCHAR(32) NOT NULL,          -- 如 第一学期
    is_current    BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    UNIQUE (year_name, term_name)
);
CREATE UNIQUE INDEX ux_teaching_term_current ON teaching_term(is_current) WHERE is_current;

-- 班级绑定教师（原 it_user.classbinding：绑定后仅该教师可对该班开课）
CREATE TABLE class_teacher_binding (
    class_id   BIGINT PRIMARY KEY REFERENCES klass(id) ON DELETE CASCADE,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

-- 课程（原 it_course）
CREATE TABLE course (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id  BIGINT NOT NULL REFERENCES users(id),
    term_id     BIGINT REFERENCES teaching_term(id) ON DELETE SET NULL,
    name        VARCHAR(128) NOT NULL,
    category    VARCHAR(64),                      -- 原 Package（课程包/分类）
    description TEXT,
    shared      BOOLEAN NOT NULL DEFAULT FALSE,   -- 原 Share='共享'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_course_teacher ON course(teacher_id);

-- 课程页面/环节（原 it_courselist + it_coursetype）
-- page_type: TEXT 图文 / FILE 资料课件 / VIDEO 视频 / LINK 外部网页 /
--            HOMEWORK 作品提交 / QUIZ 课堂测验 / DISCUSS 讨论 / ANSWER_FIRST 抢答
CREATE TABLE course_page (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id       BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    seq             INT NOT NULL,
    title           VARCHAR(128) NOT NULL,
    page_type       VARCHAR(16) NOT NULL CHECK (page_type IN
                    ('TEXT','FILE','VIDEO','LINK','HOMEWORK','QUIZ','DISCUSS','ANSWER_FIRST')),
    content         TEXT,
    link_url        VARCHAR(512),
    file_path       VARCHAR(256),
    file_name       VARCHAR(256),
    student_visible BOOLEAN NOT NULL DEFAULT TRUE,
    score           INT NOT NULL DEFAULT 0,
    ref_id          BIGINT,          -- 关联其他模块对象（如测验试卷 id）
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_course_page_course ON course_page(course_id, seq);

-- 开课记录（原 it_TeRecord）
CREATE TABLE lesson (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id      BIGINT NOT NULL REFERENCES course(id),
    teacher_id     BIGINT NOT NULL REFERENCES users(id),
    class_id       BIGINT NOT NULL REFERENCES klass(id),
    term_id        BIGINT REFERENCES teaching_term(id) ON DELETE SET NULL,
    group_model_id BIGINT REFERENCES lab_group_model(id) ON DELETE SET NULL,
    status         VARCHAR(8) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ENDED')),
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at       TIMESTAMPTZ,
    summary        TEXT
);
-- 一个班同一时间只能有一堂进行中的课（原 it_user.nowcourse）
CREATE UNIQUE INDEX ux_lesson_active_class ON lesson(class_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_lesson_teacher ON lesson(teacher_id, started_at DESC);

-- 本堂课中各页面对学生是否开放（原 courselistctrl 教师控制页面）
CREATE TABLE lesson_page_state (
    lesson_id BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id   BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    is_open   BOOLEAN NOT NULL,
    PRIMARY KEY (lesson_id, page_id)
);

-- 学生课堂记录/考勤（原 it_sturecord：pc/starttime/flg2 出席/leave 请假/group/source/pingyu）
CREATE TABLE lesson_attendance (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id  BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status     VARCHAR(8) NOT NULL CHECK (status IN ('PRESENT','ABSENT','LEAVE')),
    pc_name    VARCHAR(64),
    ip_address VARCHAR(45),
    joined_at  TIMESTAMPTZ,
    group_no   INT NOT NULL DEFAULT 0,
    score      INT,              -- 课堂表现分
    comment    VARCHAR(256),     -- 教师评语
    UNIQUE (lesson_id, student_id)
);
CREATE INDEX ix_lesson_attendance_student ON lesson_attendance(student_id);

-- 教师课表（原 it_Lessonsheet / it_Lessonsheetname）
CREATE TABLE timetable_cell (
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period     INT NOT NULL CHECK (period BETWEEN 1 AND 12),
    weekday    INT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    content    VARCHAR(64) NOT NULL,
    PRIMARY KEY (teacher_id, period, weekday)
);

INSERT INTO teaching_term(year_name, term_name, is_current, display_order) VALUES
    ('2024-2025', '第一学期', FALSE, 1),
    ('2024-2025', '第二学期', FALSE, 2),
    ('2025-2026', '第一学期', TRUE, 3),
    ('2025-2026', '第二学期', FALSE, 4);
