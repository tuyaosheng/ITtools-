-- 每组人数上限（0 = 不限），学生自由加入时生效；成绩开网可要求小组全员完成才开网
ALTER TABLE lesson ADD COLUMN group_max_size INT NOT NULL DEFAULT 0 CHECK (group_max_size BETWEEN 0 AND 100);
ALTER TABLE lesson_net_gate ADD COLUMN require_group BOOLEAN NOT NULL DEFAULT FALSE;
