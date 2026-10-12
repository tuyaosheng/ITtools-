-- 子项目 5：课后作业 / 课堂测验完整流程（原 homework / sethomework / Conversion / homeworkscore / coursetest /
-- editcoursetestly / coursetestmark / testlist / exercises / Leakfilled / coursetestview）

-- 测验种类、考试方式、结分、同批发布
ALTER TABLE quiz_test ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'TEST' CHECK (kind IN ('TEST','HOMEWORK'));
ALTER TABLE quiz_test ADD COLUMN mode VARCHAR(8) NOT NULL DEFAULT 'AUTO' CHECK (mode IN ('CONTROL','AUTO','INSTANT'));
ALTER TABLE quiz_test ADD COLUMN finalized_at TIMESTAMPTZ;           -- 结分：之后不再允许改分、发还
ALTER TABLE quiz_test ADD COLUMN batch_key VARCHAR(36);              -- 同一次发布给多个班级时相同，用于年级统计
ALTER TABLE quiz_test DROP CONSTRAINT quiz_test_status_check;
ALTER TABLE quiz_test ADD CONSTRAINT quiz_test_status_check CHECK (status IN ('STANDBY','OPEN','CLOSED'));
CREATE INDEX ix_quiz_test_batch ON quiz_test(batch_key) WHERE batch_key IS NOT NULL;

-- 发还重改：RETURNED 的答卷学生可修改后重新提交（结分前）
ALTER TABLE quiz_attempt DROP CONSTRAINT quiz_attempt_status_check;
ALTER TABLE quiz_attempt ADD CONSTRAINT quiz_attempt_status_check CHECK (status IN ('IN_PROGRESS','SUBMITTED','RETURNED'));
ALTER TABLE quiz_attempt ADD COLUMN returned_note VARCHAR(200);

-- 课程中测验页的考试设置（控制模式 / 自动模式 / 提交即批改、限时、公布答案）
CREATE TABLE quiz_page_config (
    page_id     BIGINT PRIMARY KEY REFERENCES course_page(id) ON DELETE CASCADE,
    mode        VARCHAR(8) NOT NULL DEFAULT 'CONTROL' CHECK (mode IN ('CONTROL','AUTO','INSTANT')),
    time_limit  INT,
    show_answer BOOLEAN NOT NULL DEFAULT TRUE
);

-- 错题本“我已掌握”
CREATE TABLE wrong_mastered (
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, question_id)
);

-- 指定刷题题库：教师把一份试卷设为某些班级的课后刷题题库
CREATE TABLE practice_set (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    paper_id   BIGINT NOT NULL REFERENCES paper(id) ON DELETE CASCADE,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE practice_set_class (
    set_id   BIGINT NOT NULL REFERENCES practice_set(id) ON DELETE CASCADE,
    class_id BIGINT NOT NULL REFERENCES klass(id) ON DELETE CASCADE,
    PRIMARY KEY (set_id, class_id)
);

-- 刷题记录：每次抽取生成一份练习卷，交卷自动批改（原“刷题编号”）
CREATE TABLE practice_session (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source       VARCHAR(8) NOT NULL CHECK (source IN ('SET','RECORD','POOL')),
    set_id       BIGINT REFERENCES practice_set(id) ON DELETE SET NULL,
    title        VARCHAR(160) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    correct      INT,
    total        INT NOT NULL
);
CREATE INDEX ix_practice_session_student ON practice_session(student_id, id DESC);
CREATE TABLE practice_session_item (
    session_id  BIGINT NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    seq         INT NOT NULL,
    answer      TEXT,
    correct     BOOLEAN,
    PRIMARY KEY (session_id, question_id)
);
