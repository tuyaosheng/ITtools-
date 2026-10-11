-- 子项目 4：Python 操作题（原 coursepy / coursepy1 / pythonlist / editpy）
ALTER TABLE course_page DROP CONSTRAINT course_page_page_type_check;
ALTER TABLE course_page ADD CONSTRAINT course_page_page_type_check CHECK (page_type IN
    ('TEXT','FILE','VIDEO','LINK','HOMEWORK','QUIZ','DISCUSS','ANSWER_FIRST','PYTHON'));

-- Python 题库（原题库ID + 版本号；course_page.ref_id 指向此表）
CREATE TABLE py_problem (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    description    TEXT,                       -- 题干（富文本）
    starter_code   TEXT,                       -- 半成品代码
    reference_code TEXT,                       -- 参考代码
    time_limit_ms  INT NOT NULL DEFAULT 2000 CHECK (time_limit_ms BETWEEN 100 AND 10000),
    shared         BOOLEAN NOT NULL DEFAULT FALSE,
    practice       BOOLEAN NOT NULL DEFAULT FALSE,   -- 课后题库：学生可在课后练习
    version        INT NOT NULL DEFAULT 1,           -- 测试点变化时自增，旧成绩标记为需重测
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 阅卷点：标准输入 → 期望输出
CREATE TABLE py_testcase (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES py_problem(id) ON DELETE CASCADE,
    seq        INT NOT NULL,
    name       VARCHAR(64),
    input      TEXT NOT NULL DEFAULT '',
    expected   TEXT NOT NULL DEFAULT '',
    score      INT NOT NULL DEFAULT 10 CHECK (score >= 0),
    hidden     BOOLEAN NOT NULL DEFAULT FALSE      -- 隐藏点：学生只看到是否通过
);
CREATE INDEX ix_py_testcase_problem ON py_testcase(problem_id, seq);

-- 学生代码与评测结果：课堂内按 (课堂, 页面, 学生)，课后练习时 lesson/page 为空
CREATE TABLE py_submission (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    problem_id      BIGINT NOT NULL REFERENCES py_problem(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id       BIGINT REFERENCES lesson(id) ON DELETE CASCADE,
    page_id         BIGINT REFERENCES course_page(id) ON DELETE CASCADE,
    code            TEXT NOT NULL DEFAULT '',
    saved_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    score           NUMERIC(7,2),
    max_score       NUMERIC(7,2),
    best_score      NUMERIC(7,2),
    detail          TEXT,                      -- JSON：每个阅卷点的结果
    problem_version INT,
    submit_count    INT NOT NULL DEFAULT 0,
    graded_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_py_submission ON py_submission(problem_id, student_id, COALESCE(lesson_id, 0), COALESCE(page_id, 0));
CREATE INDEX ix_py_submission_lesson ON py_submission(lesson_id, page_id);

-- 本堂课对学生开放参考代码（原"一键额外开启参考代码"）
CREATE TABLE py_lesson_setting (
    lesson_id      BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id        BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    show_reference BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (lesson_id, page_id)
);
