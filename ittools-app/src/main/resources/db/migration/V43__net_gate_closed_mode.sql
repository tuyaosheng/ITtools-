-- 成绩开网：没达标的学生处于什么状态。DISCONNECT = 断网（原来的做法），WHITELIST = 白名单（只能访问网络控制器 CTR“规则管理 → 白名单”里放行的网站）
ALTER TABLE lesson_net_gate ADD COLUMN closed_mode VARCHAR(12) NOT NULL DEFAULT 'WHITELIST';
-- 已经存在的规则保持原来的断网行为
UPDATE lesson_net_gate SET closed_mode = 'DISCONNECT';
