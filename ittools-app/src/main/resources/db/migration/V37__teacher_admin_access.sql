-- 教师兼任管理员：勾选后该教师登录即同时拥有管理后台权限，不用另开管理员账号来回切换
ALTER TABLE users ADD COLUMN admin_access BOOLEAN NOT NULL DEFAULT FALSE;
