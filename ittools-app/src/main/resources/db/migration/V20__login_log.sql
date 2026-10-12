-- 登入记录（原 stulog.aspx 学生登入情况查询）
CREATE TABLE login_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ip_address VARCHAR(45),
    login_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    logout_at  TIMESTAMPTZ
);
CREATE INDEX ix_login_log_user ON login_log(user_id, login_at DESC);
CREATE INDEX ix_login_log_time ON login_log(login_at);
