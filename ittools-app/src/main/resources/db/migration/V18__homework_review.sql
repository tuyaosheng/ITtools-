-- 子项目 3：作品批阅细节（原 courseuploadfile 作品名/自评/简介，markstufile 好评/中评/差评、发回重做、偷懒模式批量打分）
ALTER TABLE submission ADD COLUMN title VARCHAR(100);                       -- 作品名
ALTER TABLE submission ADD COLUMN self_score INT CHECK (self_score BETWEEN 0 AND 100);  -- 自我评分
ALTER TABLE submission ADD COLUMN rating VARCHAR(4) CHECK (rating IN ('GOOD','OK','BAD'));  -- 好评/中评/差评
ALTER TABLE submission ADD COLUMN returned BOOLEAN NOT NULL DEFAULT FALSE;  -- 发回重做，学生重新提交后清除
ALTER TABLE submission ADD COLUMN return_note VARCHAR(200);
