-- 机房方案（与原“课后练习开网服务”的座位设置一致）：座位名前缀、编号位数、1号座位IP、IP随座位号递增/递减、
-- 文本排版（一行一排，座位号空格隔开，过道写 .）。按方案生成机位（计算机名 + IP）和座位格子。
ALTER TABLE lab_room ADD COLUMN prefix VARCHAR(12);
ALTER TABLE lab_room ADD COLUMN pad INT NOT NULL DEFAULT 2 CHECK (pad BETWEEN 0 AND 3);
ALTER TABLE lab_room ADD COLUMN base_ip VARCHAR(45);
ALTER TABLE lab_room ADD COLUMN ip_step INT NOT NULL DEFAULT 1 CHECK (ip_step IN (1, -1));
ALTER TABLE lab_room ADD COLUMN layout_text TEXT;
ALTER TABLE lab_room DROP CONSTRAINT lab_room_rows_count_check;
ALTER TABLE lab_room DROP CONSTRAINT lab_room_cols_count_check;
ALTER TABLE lab_room ADD CONSTRAINT lab_room_rows_count_check CHECK (rows_count BETWEEN 1 AND 40);
ALTER TABLE lab_room ADD CONSTRAINT lab_room_cols_count_check CHECK (cols_count BETWEEN 1 AND 40);

-- 网络控制器（CTR）：本平台最后一次给某台电脑下发的指令（CTR 查不到实时状态时用于显示）
CREATE TABLE lab_net_state (
    ip_address VARCHAR(45) PRIMARY KEY,
    enabled    BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
