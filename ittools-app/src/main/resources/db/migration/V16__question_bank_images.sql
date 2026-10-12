-- 子项目 5：图片式题库并入现有题库（原 tikujpg.aspx）
-- 题干可以是一张图片；批量传图入库的题目先作为“待设置答案”的草稿，设置好答案后才能组卷和练习；
-- 删除进入回收站（回收站里的图片仍参与去重，避免已删试题被重复导入）。
ALTER TABLE question ADD COLUMN image_path VARCHAR(256);
ALTER TABLE question ADD COLUMN image_hash CHAR(64);                    -- 图片 SHA-256，用于去重
ALTER TABLE question ADD COLUMN stars INT NOT NULL DEFAULT 0 CHECK (stars BETWEEN 0 AND 5);
ALTER TABLE question ADD COLUMN needs_answer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE question ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX ix_question_image_hash ON question(teacher_id, image_hash) WHERE image_hash IS NOT NULL;

CREATE TABLE question_tag (
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    tag         VARCHAR(32) NOT NULL,
    PRIMARY KEY (question_id, tag)
);
CREATE INDEX ix_question_tag_tag ON question_tag(tag);
