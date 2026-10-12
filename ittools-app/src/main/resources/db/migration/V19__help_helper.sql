-- 学生互助（原 coursehelplist“收到谁的帮助”）：求助的学生登记帮助自己的同学，计入互助次数
ALTER TABLE help_request ADD COLUMN helper_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX ix_help_request_helper ON help_request(lesson_id, helper_id) WHERE helper_id IS NOT NULL;
