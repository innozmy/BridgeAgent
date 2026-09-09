-- 已有库补联下墩台 / 墩柱表。新库直接跑 schema.sql 即可。
-- 柱高不进 spans_m，也不进 project_param。
USE bridge_agent;

CREATE TABLE IF NOT EXISTS project_unit_support (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  unit_id         BIGINT UNSIGNED NOT NULL COMMENT '所属联',
  seq             SMALLINT UNSIGNED NOT NULL COMMENT '沿联向序号，从 0 起',
  code            VARCHAR(32)     NULL COMMENT '如 P1、1#、0#台',
  kind            VARCHAR(16)     NOT NULL DEFAULT 'pier' COMMENT 'pier 桥墩 / abutment 桥台',
  source          VARCHAR(16)     NOT NULL COMMENT 'drawing / cad / agent / manual',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_unit_support (unit_id, seq),
  KEY idx_support_unit (unit_id),
  CONSTRAINT fk_support_unit FOREIGN KEY (unit_id) REFERENCES project_unit (id) ON DELETE CASCADE,
  CONSTRAINT chk_support_kind CHECK (kind IN ('pier', 'abutment')),
  CONSTRAINT chk_support_source CHECK (source IN ('drawing', 'cad', 'agent', 'manual'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一联上的墩或台';

CREATE TABLE IF NOT EXISTS project_unit_column (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  support_id      BIGINT UNSIGNED NOT NULL COMMENT '所属墩台',
  seq             SMALLINT UNSIGNED NOT NULL COMMENT '同一墩上柱序号，从 1 起',
  side            VARCHAR(16)     NULL COMMENT 'left / right / inner / outer，可空',
  height_m        DECIMAL(10,3)   NULL COMMENT '盖梁底（或墩顶）至承台顶/桩顶，米；未测可空',
  source          VARCHAR(16)     NOT NULL COMMENT 'drawing / cad / agent / manual',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_support_column (support_id, seq),
  KEY idx_column_support (support_id),
  CONSTRAINT fk_column_support FOREIGN KEY (support_id) REFERENCES project_unit_support (id) ON DELETE CASCADE,
  CONSTRAINT chk_column_seq CHECK (seq >= 1),
  CONSTRAINT chk_column_height CHECK (height_m IS NULL OR height_m > 0),
  CONSTRAINT chk_column_source CHECK (source IN ('drawing', 'cad', 'agent', 'manual'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='墩台上的单根墩柱高度';
