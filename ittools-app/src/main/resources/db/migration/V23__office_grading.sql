-- 第5批：Office 操作题
-- 学生页面成绩显示控制（原“学生页面成绩显示控制”）：ALL 分数和各评分点 / SCORE 只显示分数 / NONE 不显示
ALTER TABLE homework_config ADD COLUMN auto_visibility VARCHAR(8) NOT NULL DEFAULT 'ALL' CHECK (auto_visibility IN ('ALL','SCORE','NONE'));
-- 课后操作题（原 aftercourse*stu）：上过该课程的班级学生可在课后反复练习、即时自动评分
ALTER TABLE homework_config ADD COLUMN practice BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE op_practice (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    page_id      BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    student_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_path    VARCHAR(512) NOT NULL,
    file_name    VARCHAR(256) NOT NULL,
    file_size    BIGINT NOT NULL DEFAULT 0,
    score        NUMERIC(8,2),
    max_score    NUMERIC(8,2),
    detail       TEXT,
    best_score   NUMERIC(8,2),
    attempts     INT NOT NULL DEFAULT 1,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (page_id, student_id)
);
