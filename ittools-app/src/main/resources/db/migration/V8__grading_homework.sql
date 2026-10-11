-- 子项目 4：自动评分引擎 + 评价量规
-- 评价量规（原 it_gaugesname / it_gaugesitem）
CREATE TABLE rubric (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    shared     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE rubric_item (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rubric_id BIGINT NOT NULL REFERENCES rubric(id) ON DELETE CASCADE,
    seq       INT NOT NULL,
    name      VARCHAR(128) NOT NULL,
    max_score INT NOT NULL CHECK (max_score >= 0),
    description VARCHAR(256)
);

-- 自动评分规则（现代化重新设计，取代原 word/excel/ppt/access 检测表 + 本地评分 exe）
CREATE TABLE grading_rule (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    page_id   BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    seq       INT NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    param1    VARCHAR(256),
    param2    VARCHAR(256),
    param3    VARCHAR(256),
    score     INT NOT NULL CHECK (score >= 0),
    note      VARCHAR(128)
);
CREATE INDEX ix_grading_rule_page ON grading_rule(page_id, seq);

-- 子项目 3：作业提交与展示
-- 作品提交页设置（原 it_coursefile：filetype/zp 自评/hp 互评/stushow/teshow）
CREATE TABLE homework_config (
    page_id         BIGINT PRIMARY KEY REFERENCES course_page(id) ON DELETE CASCADE,
    allowed_exts    VARCHAR(256),              -- 逗号分隔，空=不限
    peer_review     BOOLEAN NOT NULL DEFAULT FALSE,
    show_classmates BOOLEAN NOT NULL DEFAULT FALSE,
    auto_grade      BOOLEAN NOT NULL DEFAULT TRUE,
    rubric_id       BIGINT REFERENCES rubric(id) ON DELETE SET NULL
);

-- 学生作品（原 it_stuupfile）
CREATE TABLE submission (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id       BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id         BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_path       VARCHAR(256) NOT NULL,
    file_name       VARCHAR(256) NOT NULL,
    file_size       BIGINT NOT NULL,
    description     VARCHAR(512),              -- 作品说明（原 act）
    pc_name         VARCHAR(64),
    ip_address      VARCHAR(45),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    submit_count    INT NOT NULL DEFAULT 1,
    late            BOOLEAN NOT NULL DEFAULT FALSE,
    auto_score      NUMERIC(7,2),
    auto_max        NUMERIC(7,2),
    auto_detail     TEXT,                      -- JSON：每条规则的结果
    teacher_score   NUMERIC(7,2),
    rubric_scores   TEXT,                      -- JSON：量规各项得分
    teacher_comment VARCHAR(512),              -- 原 teEvaluation
    graded_at       TIMESTAMPTZ,
    essence         BOOLEAN NOT NULL DEFAULT FALSE,   -- 精华作品（原 Essence）
    UNIQUE (lesson_id, page_id, student_id)
);
CREATE INDEX ix_submission_student ON submission(student_id);
CREATE INDEX ix_submission_essence ON submission(essence) WHERE essence;

-- 同学互评（原 numstu 互评分 + it_stuupfileComments）
CREATE TABLE peer_review (
    submission_id BIGINT NOT NULL REFERENCES submission(id) ON DELETE CASCADE,
    reviewer_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score         INT NOT NULL CHECK (score BETWEEN 0 AND 100),
    comment       VARCHAR(256),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (submission_id, reviewer_id)
);
