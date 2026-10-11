-- 子项目 7：资源与教案
-- 教学资源（原 it_resource：title/share/coursetype/termtype；it_resourcestu 分发给学生）
CREATE TABLE resource (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    category    VARCHAR(64),
    term_id     BIGINT REFERENCES teaching_term(id) ON DELETE SET NULL,
    file_path   VARCHAR(256),
    file_name   VARCHAR(256),
    file_size   BIGINT,
    link_url    VARCHAR(512),
    shared      BOOLEAN NOT NULL DEFAULT FALSE,
    downloads   INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE resource_class (
    resource_id BIGINT NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    class_id    BIGINT NOT NULL REFERENCES klass(id) ON DELETE CASCADE,
    PRIMARY KEY (resource_id, class_id)
);

-- 教案（原 it_Lessonplans：title/share/pptfiles/files/Lessonplansdate/Lessonplansterm）
CREATE TABLE lesson_plan (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    term_id    BIGINT REFERENCES teaching_term(id) ON DELETE SET NULL,
    content    TEXT,
    doc_path   VARCHAR(256),
    doc_name   VARCHAR(256),
    ppt_path   VARCHAR(256),
    ppt_name   VARCHAR(256),
    shared     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 网盘（原 it_webdisc：title/content/files/filesize/stuid），师生共用，容量受系统参数限制
CREATE TABLE disk_file (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(200),
    note       VARCHAR(512),
    file_path  VARCHAR(256) NOT NULL,
    file_name  VARCHAR(256) NOT NULL,
    file_size  BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_disk_file_owner ON disk_file(owner_id);

-- 学习笔记 / 课后反思（原 note.aspx、it_reflection）
CREATE TABLE study_note (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id       BIGINT REFERENCES course(id) ON DELETE SET NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT,
    submitted       BOOLEAN NOT NULL DEFAULT FALSE,   -- 作为反思提交给课程教师
    teacher_comment VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_study_note_author ON study_note(author_id);

-- 走班选课（原 xkmanage / zbxk：it_zbxkkmname kmname/renshu/shijian/teacher）
CREATE TABLE elective_activity (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    year_id     BIGINT REFERENCES school_year(id) ON DELETE CASCADE,  -- 面向的年级，空=全部
    status      VARCHAR(8) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','OPEN','CLOSED')),
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE elective_subject (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    activity_id  BIGINT NOT NULL REFERENCES elective_activity(id) ON DELETE CASCADE,
    name         VARCHAR(128) NOT NULL,
    capacity     INT NOT NULL CHECK (capacity > 0),
    schedule     VARCHAR(128),
    teacher_name VARCHAR(64),
    description  VARCHAR(512)
);
CREATE TABLE elective_choice (
    activity_id BIGINT NOT NULL REFERENCES elective_activity(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id  BIGINT NOT NULL REFERENCES elective_subject(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (activity_id, student_id)
);
