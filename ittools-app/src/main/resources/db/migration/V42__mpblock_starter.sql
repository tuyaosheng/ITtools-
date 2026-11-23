-- 平台图形化编程：作品页的起始程序（老师搭好或上传 .mpcode），学生第一次打开这一页时可以从它开始
-- 每个作品页一份；门铃组页、显示组页各配一份就是“按角色不同”
CREATE TABLE mpblock_starter (
    page_id    BIGINT PRIMARY KEY REFERENCES course_page(id) ON DELETE CASCADE,
    project    TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 允许学生选用自己以前的程序（别的课的作品页存档、随时练习、交过的 .mpcode），默认允许
ALTER TABLE homework_config ADD COLUMN allow_history BOOLEAN NOT NULL DEFAULT TRUE;
