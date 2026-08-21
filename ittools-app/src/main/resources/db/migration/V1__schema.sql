CREATE TABLE school_year (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_code   VARCHAR(16)  NOT NULL UNIQUE,
    label       VARCHAR(64)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE klass (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_year_id  BIGINT NOT NULL REFERENCES school_year(id),
    name            VARCHAR(64) NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    UNIQUE (school_year_id, name)
);

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role            VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN','TEACHER','STUDENT')),
    name            VARCHAR(64) NOT NULL,
    login_name      VARCHAR(64),
    student_no      VARCHAR(32),
    xjh             VARCHAR(32),
    password_hash   VARCHAR(100) NOT NULL,
    class_id        BIGINT REFERENCES klass(id),
    enroll_year_id  BIGINT REFERENCES school_year(id),
    graduated       BOOLEAN NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_login_name ON users(login_name) WHERE login_name IS NOT NULL;
CREATE UNIQUE INDEX ux_users_xjh ON users(xjh) WHERE xjh IS NOT NULL;
CREATE INDEX ix_users_class_name ON users(class_id, name);

CREATE TABLE permission (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code    VARCHAR(64) NOT NULL UNIQUE,
    name    VARCHAR(128) NOT NULL,
    module  VARCHAR(32) NOT NULL
);

CREATE TABLE role_permission (
    role          VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN','TEACHER','STUDENT')),
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    PRIMARY KEY (role, permission_id)
);

CREATE TABLE module_permission (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope         VARCHAR(16) NOT NULL CHECK (scope IN ('GLOBAL','CLASS','USER')),
    scope_ref_id  BIGINT,
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE
);
