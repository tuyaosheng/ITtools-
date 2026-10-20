-- 成绩开网：课堂里指定一次测验，学生达标才开网，不达标保持断网，可限定答题机会次数。
CREATE TABLE lesson_net_gate (
    lesson_id  BIGINT PRIMARY KEY REFERENCES lesson(id) ON DELETE CASCADE,
    test_id    BIGINT NOT NULL REFERENCES quiz_test(id) ON DELETE CASCADE,
    pass_score NUMERIC(7,2) NOT NULL,                       -- 达标分数（含）
    max_tries  INT NOT NULL DEFAULT 1 CHECK (max_tries BETWEEN 1 AND 20),   -- 每人默认机会（交卷次数）
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lesson_net_gate_student (
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tries       INT NOT NULL DEFAULT 0,                     -- 已交卷次数
    extra_tries INT NOT NULL DEFAULT 0,                     -- 教师额外加的机会
    best_score  NUMERIC(7,2),
    passed      BOOLEAN NOT NULL DEFAULT FALSE,
    override    VARCHAR(4) CHECK (override IN ('OPEN','CUT')),  -- 教师手动放行 / 手动断网，空 = 按成绩
    PRIMARY KEY (lesson_id, student_id)
);
