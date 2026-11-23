-- 小组开网可设为“作品组内有人交了就算”：每个作品页组内任一出席同学达标即可，测验和任务单（课堂调查页）仍要每人完成。
-- 任务单页直接放进 lesson_net_gate_page，表结构不变。
ALTER TABLE lesson_net_gate ADD COLUMN group_programs BOOLEAN NOT NULL DEFAULT FALSE;
