-- 已有库补权限审计。新库直接跑 schema.sql。
USE bridge_agent;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_audit (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  actor_user_id   BIGINT UNSIGNED NULL COMMENT '登录失败可空',
  actor_username  VARCHAR(32)     NOT NULL COMMENT '当时登录名或尝试名',
  action          VARCHAR(32)     NOT NULL COMMENT '见 AuditActions',
  target_type     VARCHAR(32)     NULL COMMENT 'user / role / project_member',
  target_id       BIGINT UNSIGNED NULL,
  target_label    VARCHAR(128)    NULL COMMENT '展示快照，如用户名',
  project_id      BIGINT UNSIGNED NULL COMMENT '成员授权时有值',
  success         TINYINT(1)      NOT NULL DEFAULT 1,
  reason          VARCHAR(128)    NULL COMMENT '失败短句',
  before_text     VARCHAR(128)    NULL COMMENT '改前，如 read',
  after_text      VARCHAR(128)    NULL COMMENT '改后，如 operate',
  ip              VARCHAR(64)     NULL,
  user_agent      VARCHAR(256)    NULL,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at),
  KEY idx_audit_actor (actor_username),
  KEY idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限审计；不记干活与知识作业';
