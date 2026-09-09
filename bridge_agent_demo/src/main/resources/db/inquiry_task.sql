-- 问询 + 建模任务表（可对已有库执行）
USE bridge_agent;

CREATE TABLE IF NOT EXISTS inquiry_thread (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL,
  title           VARCHAR(200)    NOT NULL DEFAULT '新会话',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_inquiry_project (project_id),
  CONSTRAINT fk_inquiry_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目问询会话（只读，可建可删）';

CREATE TABLE IF NOT EXISTS inquiry_message (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  thread_id       BIGINT UNSIGNED NOT NULL,
  role            VARCHAR(16)     NOT NULL COMMENT 'user / agent',
  body            TEXT            NOT NULL,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_message_thread (thread_id),
  CONSTRAINT fk_message_thread FOREIGN KEY (thread_id) REFERENCES inquiry_thread (id) ON DELETE CASCADE,
  CONSTRAINT chk_inquiry_role CHECK (role IN ('user', 'agent'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问询消息；Agent 第一版只写占位回复';

CREATE TABLE IF NOT EXISTS model_task (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL,
  title           VARCHAR(200)    NOT NULL,
  kind            VARCHAR(64)     NOT NULL COMMENT '类型自由文本',
  status          VARCHAR(16)     NOT NULL DEFAULT 'waiting' COMMENT 'waiting / running / done / failed',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_task_project (project_id),
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT chk_task_status CHECK (status IN ('waiting', 'running', 'done', 'failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='建模任务账本';

CREATE TABLE IF NOT EXISTS model_task_event (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id         BIGINT UNSIGNED NOT NULL,
  body            VARCHAR(500)    NOT NULL,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_event_task (task_id),
  CONSTRAINT fk_event_task FOREIGN KEY (task_id) REFERENCES model_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务时间线';
