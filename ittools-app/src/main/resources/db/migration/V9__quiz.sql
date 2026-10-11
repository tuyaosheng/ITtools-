-- 子项目 5：在线测验与题库
-- 题库（原 it_testteoper / it_coursetestteoper：flg1 判断/flg2 单选/flg3 多选/flg4 填空，p/xz/dx/t 答案，analytical 解析）
CREATE TABLE question (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category   VARCHAR(64),
    qtype      VARCHAR(8) NOT NULL CHECK (qtype IN ('JUDGE','SINGLE','MULTI','FILL')),
    stem       TEXT NOT NULL,
    options    TEXT,          -- JSON 数组（单选/多选）
    answer     TEXT NOT NULL, -- JUDGE: T/F；SINGLE: A；MULTI: ACD；FILL: JSON 数组，每空可用 | 分隔多个可接受答案
    analysis   TEXT,
    score      INT NOT NULL DEFAULT 5,
    shared     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_question_teacher ON question(teacher_id);

-- 试卷（原 it_testoperation：name/score/teacher/gx 共享模式）
CREATE TABLE paper (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    shared      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE paper_question (
    paper_id    BIGINT NOT NULL REFERENCES paper(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    seq         INT NOT NULL,
    score       INT NOT NULL CHECK (score >= 0),
    PRIMARY KEY (paper_id, question_id)
);

-- 测验发布（原 it_testoperstuscore.flg3 回收控制）：向班级发布试卷；课堂测验页按 (lesson, page) 对应一场测验
CREATE TABLE quiz_test (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paper_id     BIGINT NOT NULL REFERENCES paper(id),
    teacher_id   BIGINT NOT NULL REFERENCES users(id),
    class_id     BIGINT NOT NULL REFERENCES klass(id) ON DELETE CASCADE,
    lesson_id    BIGINT REFERENCES lesson(id) ON DELETE CASCADE,
    page_id      BIGINT REFERENCES course_page(id) ON DELETE CASCADE,
    title        VARCHAR(128) NOT NULL,
    status       VARCHAR(8) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    show_answer  BOOLEAN NOT NULL DEFAULT TRUE,   -- 回收后向学生显示答案与解析
    time_limit   INT,                             -- 分钟，空=不限
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_quiz_test_lesson_page ON quiz_test(lesson_id, page_id) WHERE lesson_id IS NOT NULL;

-- 学生答卷（原 it_testoperstuscore：flg1 已交/未交、flg2 已批改、score）
CREATE TABLE quiz_attempt (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    test_id      BIGINT NOT NULL REFERENCES quiz_test(id) ON DELETE CASCADE,
    student_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS','SUBMITTED')),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    score        NUMERIC(7,2),
    UNIQUE (test_id, student_id)
);
-- 逐题作答（原 it_teststuoper）
CREATE TABLE quiz_answer (
    attempt_id  BIGINT NOT NULL REFERENCES quiz_attempt(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    answer      TEXT,
    correct     BOOLEAN,
    score       NUMERIC(7,2),
    adjusted    BOOLEAN NOT NULL DEFAULT FALSE,   -- 教师人工改分
    PRIMARY KEY (attempt_id, question_id)
);

-- 刷题练习记录（原 it_stushuati：dtime/zql 正确率）
CREATE TABLE practice_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    correct     BOOLEAN NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_practice_log_student ON practice_log(student_id, answered_at DESC);
