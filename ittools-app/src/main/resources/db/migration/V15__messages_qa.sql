-- 子项目 6：系统消息（原 note.aspx：只有教师可以向学生发送，个人消息 / 群体消息）与师生答疑（原 stuquestion / answer）

-- 系统消息：发送时展开收件人，便于逐人记录已读
CREATE TABLE sys_message (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,   -- 空 = 系统自动发送
    title       VARCHAR(200) NOT NULL,
    content     TEXT,
    target_desc VARCHAR(200),                                   -- 收件人摘要，如“2024级1班（全班）”
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sys_message_sender ON sys_message(sender_id, created_at DESC);

CREATE TABLE sys_message_recipient (
    message_id BIGINT NOT NULL REFERENCES sys_message(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at    TIMESTAMPTZ,
    PRIMARY KEY (message_id, user_id)
);
CREATE INDEX ix_sys_message_recipient_user ON sys_message_recipient(user_id, read_at);

-- 师生答疑：学生向某位老师提问，老师答复；可公开给同学参考
CREATE TABLE qa_question (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    teacher_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    content     TEXT NOT NULL,
    answer      TEXT,
    answered_at TIMESTAMPTZ,
    is_public   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_qa_question_teacher ON qa_question(teacher_id, answered_at, created_at DESC);
CREATE INDEX ix_qa_question_student ON qa_question(student_id, created_at DESC);
