-- 子项目 6：课堂互动
-- 小组讨论（原 it_discuss：insc/class/course/list/group/content，flg=1 为小组结论）
CREATE TABLE discuss_post (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id     BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id       BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    group_no      INT NOT NULL DEFAULT 0,
    author_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content       TEXT NOT NULL,
    is_conclusion BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_discuss_post_lesson ON discuss_post(lesson_id, page_id, group_no);

-- 举手求助（原 it_helplist：course/stuid/help）
CREATE TABLE help_request (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pc_name     VARCHAR(64),
    message     VARCHAR(256),
    status      VARCHAR(8) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX ix_help_request_lesson ON help_request(lesson_id, status);

-- 教师广播消息
CREATE TABLE broadcast_message (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id  BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    sender_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 抢答（原 courseanswerfirst）
CREATE TABLE answer_first_round (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id  BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id    BIGINT REFERENCES course_page(id) ON DELETE CASCADE,
    question   VARCHAR(1000) NOT NULL,
    status     VARCHAR(8) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE answer_first_entry (
    round_id   BIGINT NOT NULL REFERENCES answer_first_round(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    answer     VARCHAR(1000),
    rank_no    INT NOT NULL,
    correct    BOOLEAN,
    score      INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (round_id, student_id)
);

-- 课堂论坛（原 it_forum / it_reforum：topic/attop 置顶/recommend 推荐/click/reply/lastname/lasttime）
CREATE TABLE forum_topic (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id       BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    author_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    pinned          BOOLEAN NOT NULL DEFAULT FALSE,
    recommended     BOOLEAN NOT NULL DEFAULT FALSE,
    clicks          INT NOT NULL DEFAULT 0,
    reply_count     INT NOT NULL DEFAULT 0,
    last_reply_name VARCHAR(64),
    last_reply_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_forum_topic_course ON forum_topic(course_id);
CREATE TABLE forum_reply (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic_id   BIGINT NOT NULL REFERENCES forum_topic(id) ON DELETE CASCADE,
    author_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 学生课堂行为日志（原 it_stulog / teacher/stulog.aspx）
CREATE TABLE student_activity (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id  BIGINT REFERENCES lesson(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action     VARCHAR(32) NOT NULL,
    detail     VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_student_activity_lesson ON student_activity(lesson_id, created_at DESC);
