-- 已有库补 RBAC。新库直接跑 schema.sql。
-- 表与种子可重复。列用 information_schema 判断，避免 Duplicate column。
USE bridge_agent;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code                 VARCHAR(32)     NOT NULL COMMENT 'admin / knowledge / project / user 或自定义',
  name                 VARCHAR(64)     NOT NULL COMMENT '展示名',
  builtin              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1=内置不可删、开关不可改',
  flag_super           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '超级管理',
  flag_knowledge       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '知识总库写',
  flag_project_admin   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '全部项目操作 + 建删项',
  created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录角色；一人一角色';

INSERT INTO sys_role (code, name, builtin, flag_super, flag_knowledge, flag_project_admin)
SELECT 'admin', '超级管理员', 1, 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'admin');
INSERT INTO sys_role (code, name, builtin, flag_super, flag_knowledge, flag_project_admin)
SELECT 'knowledge', '知识库管理员', 1, 0, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'knowledge');
INSERT INTO sys_role (code, name, builtin, flag_super, flag_knowledge, flag_project_admin)
SELECT 'project', '项目管理员', 1, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'project');
INSERT INTO sys_role (code, name, builtin, flag_super, flag_knowledge, flag_project_admin)
SELECT 'user', '普通用户', 1, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'user');

SET @db := DATABASE();
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='role_id')=0,
  'ALTER TABLE sys_user ADD COLUMN role_id BIGINT UNSIGNED NULL COMMENT ''一人一角色'' AFTER nickname',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='status')=0,
  'ALTER TABLE sys_user ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''enabled'' COMMENT ''enabled / disabled'' AFTER token_version',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='avatar_path')=0,
  'ALTER TABLE sys_user ADD COLUMN avatar_path VARCHAR(512) NULL COMMENT ''相对 app.avatar-storage-root'' AFTER status',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE sys_user u
JOIN sys_role r ON r.code = 'admin'
SET u.role_id = r.id
WHERE u.username = 'root' AND u.role_id IS NULL;

UPDATE sys_user u
JOIN sys_role r ON r.code = 'user'
SET u.role_id = r.id
WHERE u.role_id IS NULL;

-- 演示账号，密码均为 1234 的 BCrypt。
INSERT INTO sys_user (username, nickname, role_id, password_hash, token_version, status)
SELECT 'kb', '知识库管理员', r.id, '$2b$10$kSm7DmW8QAxfH8PfMQ6ufuhb/39y3WxLAMnWCLwgYr8BUQOvgnsYK', 1, 'enabled'
FROM sys_role r WHERE r.code = 'knowledge'
AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'kb');
INSERT INTO sys_user (username, nickname, role_id, password_hash, token_version, status)
SELECT 'pm', '项目管理员', r.id, '$2b$10$kSm7DmW8QAxfH8PfMQ6ufuhb/39y3WxLAMnWCLwgYr8BUQOvgnsYK', 1, 'enabled'
FROM sys_role r WHERE r.code = 'project'
AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'pm');
INSERT INTO sys_user (username, nickname, role_id, password_hash, token_version, status)
SELECT 'user', '操作员', r.id, '$2b$10$kSm7DmW8QAxfH8PfMQ6ufuhb/39y3WxLAMnWCLwgYr8BUQOvgnsYK', 1, 'enabled'
FROM sys_role r WHERE r.code = 'user'
AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'user');
INSERT INTO sys_user (username, nickname, role_id, password_hash, token_version, status)
SELECT 'reader', '只读', r.id, '$2b$10$kSm7DmW8QAxfH8PfMQ6ufuhb/39y3WxLAMnWCLwgYr8BUQOvgnsYK', 1, 'enabled'
FROM sys_role r WHERE r.code = 'user'
AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'reader');

CREATE TABLE IF NOT EXISTS sys_project_member (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id  BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  perm        VARCHAR(16)     NOT NULL COMMENT 'read / operate',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_member (project_id, user_id),
  KEY idx_member_user (user_id),
  CONSTRAINT fk_member_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT chk_member_perm CHECK (perm IN ('read', 'operate'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='普通用户的项目层权限；超管/项目管理员不写本表';

INSERT INTO sys_project_member (project_id, user_id, perm)
SELECT p.id, u.id, 'operate'
FROM project p
JOIN sys_user u ON u.username = 'user'
WHERE p.id = 11
AND NOT EXISTS (
  SELECT 1 FROM sys_project_member m WHERE m.project_id = p.id AND m.user_id = u.id
);

INSERT INTO sys_project_member (project_id, user_id, perm)
SELECT p.id, u.id, 'read'
FROM project p
JOIN sys_user u ON u.username = 'reader'
WHERE p.id = 11
AND NOT EXISTS (
  SELECT 1 FROM sys_project_member m WHERE m.project_id = p.id AND m.user_id = u.id
);
