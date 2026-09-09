-- 已有库补登录账号表。新库直接跑 schema.sql 即可。
-- 可重复执行：表用 IF NOT EXISTS，种子用 WHERE NOT EXISTS。
USE bridge_agent;

CREATE TABLE IF NOT EXISTS sys_user (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username        VARCHAR(32)     NOT NULL COMMENT '登录名，唯一',
  nickname        VARCHAR(64)     NOT NULL COMMENT '展示名，可改，不进 JWT',
  password_hash   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文，禁止明文',
  token_version   INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT 'JWT 载荷 ver；改密后 +1 废旧票',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录账号；本阶段全员当超级管理员';

-- 演示账号 root / 1234。密文由 BCrypt cost=10 生成，SQL 不写明文。
INSERT INTO sys_user (username, nickname, password_hash, token_version)
SELECT 'root', 'zmy', '$2b$10$kSm7DmW8QAxfH8PfMQ6ufuhb/39y3WxLAMnWCLwgYr8BUQOvgnsYK', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'root');
