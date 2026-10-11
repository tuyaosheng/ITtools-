-- 系统参数（原 admin/setsystem.aspx），键值对存储
CREATE TABLE system_setting (
    setting_key    VARCHAR(64) PRIMARY KEY,
    setting_value  TEXT
);

-- 基座 final review 后续项：module_permission 同一范围同一权限只允许一条
CREATE UNIQUE INDEX ux_module_permission_scope
    ON module_permission(scope, COALESCE(scope_ref_id, 0), permission_id);
