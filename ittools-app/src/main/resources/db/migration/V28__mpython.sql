-- 掌控板编程（MPYTHON）：学生拼积木 → 经 USB 刷进掌控板 → 板子把消息经学生页转发到服务器 → 教师看板实时看到。
-- 前端直接复用 mpython-lite（web/public/mpython），这里替代它原来的 server.py。
-- 初始积木、这节课上哪几关、按组/按人 存在 course_page.config。
ALTER TABLE course_page DROP CONSTRAINT course_page_page_type_check;
ALTER TABLE course_page ADD CONSTRAINT course_page_page_type_check CHECK (page_type IN
    ('TEXT','FILE','VIDEO','LINK','HOMEWORK','QUIZ','DISCUSS','ANSWER_FIRST','PYTHON','GROUPING','GROUP_WORK','GROUP_SHOW',
     'SURVEY','TAKE_QUESTION','STORYLINE','SCRATCH','MPYTHON'));

-- 每组（或每人）当前的程序与进度。group_key：按组时是组号 "3"，未分组或按人时是 "s" + 学生 id
CREATE TABLE mpy_state (
    lesson_id  BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id    BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    group_key  VARCHAR(20) NOT NULL,
    code       TEXT NOT NULL DEFAULT '',
    filled     TEXT NOT NULL DEFAULT '[]',   -- 检查清单 JSON [{label, ok, note}]
    workspace  TEXT,                         -- 整份积木 JSON（教师看板画积木用）
    level      VARCHAR(40),
    level_no   INT,
    passed     TEXT NOT NULL DEFAULT '[]',   -- 已通关的关卡 id JSON
    runs       INT NOT NULL DEFAULT 0,       -- 刷入次数
    lamp       VARCHAR(20),                  -- 最近一句“告诉网页”
    events     TEXT NOT NULL DEFAULT '[]',   -- 最近 30 条事件 JSON
    updated_at TIMESTAMPTZ,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (lesson_id, page_id, group_key)
);

-- 过程日志（原 data/code_log.csv、events.csv）：kind = CODE（刷入/通关/改了程序）| EVENT（板子消息）
CREATE TABLE mpy_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id  BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id    BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    group_key  VARCHAR(20) NOT NULL,
    student_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    kind       VARCHAR(8) NOT NULL CHECK (kind IN ('CODE','EVENT')),
    action     VARCHAR(20) NOT NULL,         -- CODE：edit/save/pass/level/...；EVENT：暗号
    source     VARCHAR(8),                   -- EVENT：usb | sim
    progress   VARCHAR(16),                  -- CODE：完成度 "2/3"
    level      VARCHAR(40),
    code       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_mpy_log_page ON mpy_log(lesson_id, page_id, created_at);
