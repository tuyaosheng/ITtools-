-- 第7批：资源小功能
-- 课后阅读计时（原 resource.aspx “阅读时间 分钟”）
CREATE TABLE resource_read (
    resource_id BIGINT NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seconds     INT NOT NULL DEFAULT 0,
    views       INT NOT NULL DEFAULT 0,
    first_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (resource_id, student_id)
);

-- 学生个人站点（原 website.aspx：上传网站包自动解压，首页 index）
CREATE TABLE student_site (
    student_id  BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    package     VARCHAR(32) NOT NULL,
    entry       VARCHAR(256) NOT NULL,
    title       VARCHAR(100) NOT NULL,
    file_count  INT NOT NULL DEFAULT 0,
    size_bytes  BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 指法练习成绩（原 st_zhifa / paiming 全校指法英雄榜）
CREATE TABLE typing_record (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    level        VARCHAR(16) NOT NULL,
    speed        INT NOT NULL,                 -- 每分钟正确字符数
    accuracy     NUMERIC(5,1) NOT NULL,        -- 正确率 %
    words        INT NOT NULL,
    duration_sec INT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_typing_record_student ON typing_record(student_id);

-- 教师管理学生网盘（原 teacher/webdisc：单独关闭某学生网盘、删除无关资源可扣除空间）
CREATE TABLE disk_policy (
    user_id       BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    disabled      BOOLEAN NOT NULL DEFAULT FALSE,
    penalty_bytes BIGINT NOT NULL DEFAULT 0
);
