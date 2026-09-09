-- 已有库补记忆表。新库直接跑 schema.sql 即可。
-- 规划：Python 不落记忆；STM/LTM/参数袋/页地图均在 Spring MySQL。
USE bridge_agent;

CREATE TABLE IF NOT EXISTS project_param (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
  param_key       VARCHAR(64)     NOT NULL COMMENT '稳定键，项目内唯一；与固定列/field_meta 含义不得重复',
  label           VARCHAR(128)    NOT NULL COMMENT '桥梁/土木工程专业名词，给人看',
  value_text      VARCHAR(512)    NULL COMMENT '已确认值；复杂结构可后续再拆',
  unit            VARCHAR(32)     NULL COMMENT '单位，如 m、mm',
  source          VARCHAR(16)     NOT NULL COMMENT 'drawing / cad / agent / manual',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_param (project_id, param_key),
  KEY idx_param_project (project_id),
  CONSTRAINT fk_param_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT chk_param_source CHECK (source IN ('drawing', 'cad', 'agent', 'manual'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已确认扩展结构参数，一行一 key；不存 field_meta 已管字段';

CREATE TABLE IF NOT EXISTS project_ltm (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL COMMENT '所属项目，一行一份长期记忆',
  body_json       JSON            NOT NULL COMMENT '过程索引与跨任务教训，不含结构尺寸',
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_ltm (project_id),
  CONSTRAINT fk_ltm_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目长期记忆；Spring 在任务终态写入';

CREATE TABLE IF NOT EXISTS agent_stm (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL,
  agent_kind      VARCHAR(32)     NOT NULL COMMENT 'drawing_parse / modeling 等；不含 inquiry',
  body_json       JSON            NOT NULL COMMENT '仅索引/墓碑/短摘要，禁止塞全书页地图或 JPEG',
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_stm (project_id, agent_kind),
  KEY idx_stm_project (project_id),
  CONSTRAINT fk_stm_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务类工种短期记忆，每项目每种 Agent 一行';

CREATE TABLE IF NOT EXISTS drawing_page_map (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL,
  file_id         BIGINT UNSIGNED NOT NULL COMMENT '对应 project_file；删图纸级联删本行',
  sha256          CHAR(64)        NOT NULL COMMENT '内容指纹，哈希变了须重粗看',
  map_json        JSON            NOT NULL COMMENT 'pages / extracts / gaps / fineRead / totalPages 等',
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_page_map_file (project_id, file_id),
  KEY idx_page_map_project (project_id),
  CONSTRAINT fk_page_map_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT fk_page_map_file FOREIGN KEY (file_id) REFERENCES project_file (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='识图页地图与未确认摘录，按文件一行';

CREATE TABLE IF NOT EXISTS inquiry_stm (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  thread_id       BIGINT UNSIGNED NOT NULL COMMENT '对应 inquiry_thread',
  summary         TEXT            NULL COMMENT '窗口之前的对话摘要',
  recent_json     JSON            NULL COMMENT '最近若干轮的 inquiry_message id 列表',
  submitted_json  JSON            NULL COMMENT '已同意提交的 taskId 列表',
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_inquiry_stm_thread (thread_id),
  CONSTRAINT fk_inquiry_stm_thread FOREIGN KEY (thread_id) REFERENCES inquiry_thread (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一条问询会话一份短期记忆；全文仍在 inquiry_message';
