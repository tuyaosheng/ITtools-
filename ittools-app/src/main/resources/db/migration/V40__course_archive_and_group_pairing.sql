-- v20261121：课程归档；小组角色与配对（门铃N组 / 显示N组）；课程页按小组角色显示。

-- 1. 课程归档：归档的课程默认不在“我的课程”、开课选择里出现，可取消归档；课程本身、上课记录、提交都不动。
ALTER TABLE course ADD COLUMN archived_at TIMESTAMPTZ;

-- 2. 小组角色与配对：组可以设“角色”（如 门铃 / 显示）和“配对号”，显示成“门铃1组”“显示1组”；
--    同一配对号的各组是一对（组数凑不成对时，一对里可以有几个同角色的组）。name 是老师手改的组名，优先显示。
ALTER TABLE lesson_group ADD COLUMN role VARCHAR(20);
ALTER TABLE lesson_group ADD COLUMN pair_no INT CHECK (pair_no IS NULL OR pair_no >= 1);
ALTER TABLE lesson_group ADD COLUMN name VARCHAR(40);
-- 本节课可选的角色（逗号分隔，如“门铃,显示”），以及是否开放学生整组选角色和配对号
ALTER TABLE lesson ADD COLUMN group_roles VARCHAR(100);
ALTER TABLE lesson ADD COLUMN group_role_open BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. 课程页按小组角色显示：NULL = 全班都看；否则逗号分隔的角色名，只有这些角色的组看得到（成绩开网也只算本组角色的页）
ALTER TABLE course_page ADD COLUMN visible_roles VARCHAR(100);

-- 4. 成绩开网可要求“本对各组都完成”（门铃N组和显示N组都完成才一起开网）
ALTER TABLE lesson_net_gate ADD COLUMN require_pair BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. 作品页可以用平台的图形化编程（掌控板，积木和 Mind+ 一样）交作品：学生在页面里拼积木、上传到掌控板、提交 .mpcode
ALTER TABLE homework_config ADD COLUMN block_editor BOOLEAN NOT NULL DEFAULT FALSE;
-- 学生拼到一半的积木程序（自动保存，下节课接着做）
CREATE TABLE mpblock_work (
    lesson_id  BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id    BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project    TEXT NOT NULL,
    code       TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, page_id, student_id)
);
CREATE INDEX ix_mpblock_work_student ON mpblock_work(student_id, page_id);
