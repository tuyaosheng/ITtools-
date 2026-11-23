-- 已下课的课堂可以“重新开课”，还原到上课中：
-- 下课时自动记的缺席（没进课的学生）要能撤回；下课时自动停用的成绩开网规则要能恢复。
ALTER TABLE lesson_attendance ADD COLUMN auto_absent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lesson_net_gate ADD COLUMN disabled_by_end BOOLEAN NOT NULL DEFAULT FALSE;
