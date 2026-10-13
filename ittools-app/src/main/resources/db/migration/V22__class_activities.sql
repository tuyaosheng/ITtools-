-- 第4批：其余课堂环节
-- 课堂调查（原 coursecheck）、随机抽题（原 courseTakequestions）、Storyline 课件包（原 storyline）、Scratch 作品播放（原 coursescratch）
ALTER TABLE course_page DROP CONSTRAINT course_page_page_type_check;
ALTER TABLE course_page ADD CONSTRAINT course_page_page_type_check CHECK (page_type IN
    ('TEXT','FILE','VIDEO','LINK','HOMEWORK','QUIZ','DISCUSS','ANSWER_FIRST','PYTHON','GROUPING','GROUP_WORK','GROUP_SHOW',
     'SURVEY','TAKE_QUESTION','STORYLINE','SCRATCH'));

-- 页面类型专属配置（调查题目、抽题题目、课件包入口等），随课程复制、课程包导出
ALTER TABLE course_page ADD COLUMN config TEXT;

-- 调查作答：每位学生每题一条，answer 为 JSON（判断 "T"/"F"、单选 "A"、多选 ["A","C"]、填空 "文字"）
CREATE TABLE survey_answer (
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id     BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    item_id     VARCHAR(16) NOT NULL,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    answer      TEXT NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, page_id, item_id, student_id)
);
CREATE INDEX ix_survey_answer_page ON survey_answer(page_id);

-- 随机抽题记录
CREATE TABLE take_question_draw (
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    page_id     BIGINT NOT NULL REFERENCES course_page(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_no INT NOT NULL,
    drawn_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, page_id, student_id)
);

-- 随机点名（原 stu_dianming）：请假原因、点名记录；座位图“关注”标记
ALTER TABLE lesson_attendance ADD COLUMN leave_reason VARCHAR(50);
ALTER TABLE lesson_attendance ADD COLUMN attention BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE roll_call (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id   BIGINT NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    called_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_roll_call_lesson ON roll_call(lesson_id);
