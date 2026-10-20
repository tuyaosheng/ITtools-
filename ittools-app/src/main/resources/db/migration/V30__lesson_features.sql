-- 课堂功能开关：教师可在上课控制台里为本堂课关闭举手、课堂论坛等互动功能。
-- 逗号分隔的“已关闭功能”列表（HELP 举手求助 / FORUM 课堂论坛），空 = 全部开放。
ALTER TABLE lesson ADD COLUMN disabled_features VARCHAR(200) NOT NULL DEFAULT '';
