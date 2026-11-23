-- 成绩开网可同时要求作品页达标：所选作品页每页得分（教师分优先，否则自动分）都达到 homework_pass，且测验达标才开网。
ALTER TABLE lesson_net_gate ADD COLUMN homework_pass NUMERIC(7,2) NOT NULL DEFAULT 100;

CREATE TABLE lesson_net_gate_page (
    lesson_id BIGINT NOT NULL REFERENCES lesson_net_gate(lesson_id) ON DELETE CASCADE,
    page_id   BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    PRIMARY KEY (lesson_id, page_id)
);
