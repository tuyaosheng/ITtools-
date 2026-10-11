-- 评分所用的代码单独保存：code 是学生的最新草稿（自动保存），submitted_code 是最近一次提交评分的代码。
-- 教师查看与重新评测都以 submitted_code 为准，避免把未提交的草稿当成作答。
ALTER TABLE py_submission ADD COLUMN submitted_code TEXT;
UPDATE py_submission SET submitted_code = code WHERE submit_count > 0;
