-- 题库防重复：文字题的“题目指纹”（由应用按同一套规则计算，启动时为旧题回填；图片题仍按 image_hash 去重，这两列为空）
ALTER TABLE question ADD COLUMN stem_key CHAR(64);   -- SHA-256(题型 + 规范化题干)：题干相同、选项不同时用来提示“疑似重复”
ALTER TABLE question ADD COLUMN dup_key CHAR(64);    -- SHA-256(题型 + 规范化题干 + 排序后的选项)：相同即视为重复题
CREATE INDEX ix_question_dup_key ON question(teacher_id, dup_key) WHERE dup_key IS NOT NULL;
CREATE INDEX ix_question_stem_key ON question(teacher_id, stem_key) WHERE stem_key IS NOT NULL;
