-- 子项目 1：机房与机位
-- 机房模型（原 it_ipclassname：name/line/Column）
CREATE TABLE lab_room (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(64) NOT NULL UNIQUE,
    rows_count INT NOT NULL CHECK (rows_count BETWEEN 1 AND 30),
    cols_count INT NOT NULL CHECK (cols_count BETWEEN 1 AND 30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 机房座位格子：某行某列放哪台机位（原 it_ipgroup 的布局部分）
CREATE TABLE lab_seat (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id        BIGINT NOT NULL REFERENCES lab_room(id) ON DELETE CASCADE,
    row_no         INT NOT NULL,
    col_no         INT NOT NULL,
    workstation_id BIGINT REFERENCES lab_workstation(id) ON DELETE SET NULL,
    UNIQUE (room_id, row_no, col_no)
);
CREATE UNIQUE INDEX ux_lab_seat_room_ws ON lab_seat(room_id, workstation_id) WHERE workstation_id IS NOT NULL;

-- 分组模型（原 it_ipgroupname）与成员（原 it_ipgroup：mod/group/ip）
CREATE TABLE lab_group_model (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE lab_group_member (
    model_id       BIGINT NOT NULL REFERENCES lab_group_model(id) ON DELETE CASCADE,
    workstation_id BIGINT NOT NULL REFERENCES lab_workstation(id) ON DELETE CASCADE,
    group_no       INT NOT NULL CHECK (group_no >= 1),
    PRIMARY KEY (model_id, workstation_id)
);

-- 班级机房配置 + 固定机位模式（原 it_user.Fixedpositionmod / Fixedpositionipclass / jifang）
CREATE TABLE lab_class_config (
    class_id   BIGINT PRIMARY KEY REFERENCES klass(id) ON DELETE CASCADE,
    room_id    BIGINT REFERENCES lab_room(id) ON DELETE SET NULL,
    fixed_seat BOOLEAN NOT NULL DEFAULT FALSE
);

-- 学生绑定座位（原"绑定座位"）。同一座位可被不同班级的学生分别绑定（不同课时共用机房），
-- 同班内唯一性由服务层保证。
CREATE TABLE lab_student_seat (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    seat_id BIGINT NOT NULL REFERENCES lab_seat(id) ON DELETE CASCADE
);
CREATE INDEX ix_lab_student_seat_seat ON lab_student_seat(seat_id);
