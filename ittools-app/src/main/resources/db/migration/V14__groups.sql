-- 子项目 6：分组与小组活动（原 courseingroup 自由分组 / coursegroupmanage 组长推选 / courseloadgroup 加载历史分组 /
-- coursegroupactive 小组合作 / courseviewgroupfile 小组成果展示）。学生所在组仍以 lesson_attendance.group_no 为准。
ALTER TABLE course_page DROP CONSTRAINT course_page_page_type_check;
ALTER TABLE course_page ADD CONSTRAINT course_page_page_type_check CHECK (page_type IN
    ('TEXT','FILE','VIDEO','LINK','HOMEWORK','QUIZ','DISCUSS','ANSWER_FIRST','PYTHON','GROUPING','GROUP_WORK','GROUP_SHOW'));

-- 本堂课的分组控制（原 it_gnum2 组数、groupflg 允许加入、roleflg 允许推选）
ALTER TABLE lesson ADD COLUMN group_count INT NOT NULL DEFAULT 0 CHECK (group_count BETWEEN 0 AND 30);
ALTER TABLE lesson ADD COLUMN group_join_open BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lesson ADD COLUMN leader_vote_open BOOLEAN NOT NULL DEFAULT FALSE;

-- 组内分工（组长为每位组员填写）
ALTER TABLE lesson_attendance ADD COLUMN group_task VARCHAR(200);

-- 小组：组长、研究主题、小组成果
CREATE TABLE lesson_group (
    lesson_id      BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    group_no       INT NOT NULL CHECK (group_no >= 1),
    leader_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    topic          VARCHAR(200),
    result_text    TEXT,
    result_file_id BIGINT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, group_no)
);

-- 加载历史分组时，尚未进课学生的预分组；进课时生效
CREATE TABLE lesson_group_preset (
    lesson_id  BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_no   INT NOT NULL,
    PRIMARY KEY (lesson_id, student_id)
);

-- 组长推选：每人一票，只能投本组同学（原 it_sturecord.elect）
CREATE TABLE lesson_group_vote (
    lesson_id    BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    voter_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    candidate_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, voter_id)
);

-- 组内资源交流；组长可将其中一份设为小组成果
CREATE TABLE lesson_group_file (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    group_no    INT NOT NULL,
    uploader_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    intro       VARCHAR(500),
    file_path   VARCHAR(256) NOT NULL,
    file_name   VARCHAR(256) NOT NULL,
    file_size   BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_lesson_group_file ON lesson_group_file(lesson_id, group_no);

-- 小组成员互评（组内）
CREATE TABLE lesson_group_peer (
    lesson_id    BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    evaluator_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score        INT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment      VARCHAR(200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, evaluator_id, target_id)
);

-- 小组成果展示的组间互评（不能评本组）
CREATE TABLE lesson_group_review (
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    group_no    INT NOT NULL,
    reviewer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score       INT NOT NULL CHECK (score BETWEEN 0 AND 100),
    comment     VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, group_no, reviewer_id)
);
