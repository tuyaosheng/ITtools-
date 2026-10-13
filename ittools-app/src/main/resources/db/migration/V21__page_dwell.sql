-- 学情监控：学生在课堂各页面的停留时间（由课堂心跳累计，单位秒）
CREATE TABLE page_dwell (
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id     BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seconds     INT NOT NULL DEFAULT 0,
    visits      INT NOT NULL DEFAULT 0,
    last_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, page_id, student_id)
);
