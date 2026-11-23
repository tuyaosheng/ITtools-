-- 平台图形化编程：学生随时练习（不在课里）的程序，每人一份，自动保存
CREATE TABLE mpblock_free (
    student_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    project    TEXT NOT NULL,
    code       TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 作品页（打开图形化编程时）旁边显示的任务单：按页面标题找同一课程里的“课堂调查”或“图文”页
-- （按标题而不是页面编号，复制课程、导出导入课程包后还能对上）
ALTER TABLE homework_config ADD COLUMN task_page_title VARCHAR(200);
