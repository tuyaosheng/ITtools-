-- 每人乱序：题目顺序、单选/多选的选项顺序按“测验 + 学生”固定打乱（同一学生每次打开顺序不变），
-- 答案仍按原选项字母保存和批改，教师端看到的始终是原顺序。
ALTER TABLE quiz_test ADD COLUMN shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quiz_test ADD COLUMN shuffle_options BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quiz_page_config ADD COLUMN shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quiz_page_config ADD COLUMN shuffle_options BOOLEAN NOT NULL DEFAULT FALSE;
