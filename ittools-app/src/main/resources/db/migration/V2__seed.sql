INSERT INTO permission(code, name, module) VALUES
  ('ADMIN_USERS',   '用户管理',   'platform'),
  ('ADMIN_CLASSES', '班级管理',   'platform'),
  ('ADMIN_YEARS',   '年级管理',   'platform'),
  ('ADMIN_PERMS',   '权限管理',   'platform');

INSERT INTO role_permission(role, permission_id)
  SELECT 'ADMIN', id FROM permission WHERE module = 'platform';

-- BCrypt hash below (cost 10) for password 'admin123', generated and verified locally with
-- Spring Security's BCryptPasswordEncoder(): new BCryptPasswordEncoder().matches("admin123", hash) == true.
-- (The literal hash suggested in the task brief did NOT verify against 'admin123' and was replaced.)
INSERT INTO users(role, name, login_name, password_hash)
  VALUES ('ADMIN', '系统管理员', 'admin',
          '$2a$10$sG74YToqDR5OnhldWNpMb.48jrz.CfPV13N2tqofuetLp5lm5gbc.');
