-- 内置 MQTT 服务器（代替单独运行 SIoT）：
-- 每节课开课时生成一个 MQTT 项目名（如 B81_1115_2029），掌控板发布到“项目名/设备名”，消息按课堂分开保存，不用下课清理。
ALTER TABLE lesson ADD COLUMN mqtt_project VARCHAR(40);
CREATE INDEX lesson_mqtt_project ON lesson(mqtt_project);

CREATE TABLE mqtt_message (
    id         BIGSERIAL PRIMARY KEY,
    lesson_id  BIGINT REFERENCES lesson(id) ON DELETE CASCADE,  -- 项目名对不上任何课堂时为空（如上个班的掌控板还在发）
    project    VARCHAR(100) NOT NULL,
    device     VARCHAR(200) NOT NULL,
    payload    TEXT NOT NULL,
    client_ip  VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX mqtt_message_lesson ON mqtt_message(lesson_id, created_at);
CREATE INDEX mqtt_message_created ON mqtt_message(created_at);

-- 课程媒体（课程包 media/ 目录里的操作视频等），页面正文用 {{媒体:文件名}} 引用
CREATE TABLE course_media (
    course_id  BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    file_path  VARCHAR(500) NOT NULL,
    size       BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (course_id, name)
);

-- 新页面类型 MQTT（物联网消息）
ALTER TABLE course_page DROP CONSTRAINT course_page_page_type_check;
ALTER TABLE course_page ADD CONSTRAINT course_page_page_type_check CHECK (page_type IN
    ('TEXT','FILE','VIDEO','LINK','HOMEWORK','QUIZ','DISCUSS','ANSWER_FIRST','PYTHON','GROUPING','GROUP_WORK','GROUP_SHOW',
     'SURVEY','TAKE_QUESTION','STORYLINE','SCRATCH','MPYTHON','MQTT'));
