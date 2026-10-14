-- 第6批：个人资料与头像（原 student/info、teacher/info、stucamera 学生头像采集）
ALTER TABLE users ADD COLUMN gender VARCHAR(2) CHECK (gender IN ('男','女'));
ALTER TABLE users ADD COLUMN birthday DATE;
ALTER TABLE users ADD COLUMN bio VARCHAR(200);
ALTER TABLE users ADD COLUMN avatar_path VARCHAR(512);
