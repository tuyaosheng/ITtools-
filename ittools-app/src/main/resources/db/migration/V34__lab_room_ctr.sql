-- 每个机房可以有自己的网络控制器（CTR）；留空时用系统设置里的全局控制器
ALTER TABLE lab_room ADD COLUMN ctr_url VARCHAR(200);
ALTER TABLE lab_room ADD COLUMN ctr_token VARCHAR(200);
