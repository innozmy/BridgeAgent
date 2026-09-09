-- 已有库补 SAP 模型版本表。新库见 schema.sql。
-- 磁盘：data/models/{projectId}/{sha256}.sdb ；删项目时 Spring 清目录。

USE bridge_agent;

CREATE TABLE IF NOT EXISTS project_sap_model (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
  task_id         BIGINT UNSIGNED NULL COMMENT '产出该文件的建模任务，可空',
  seq             SMALLINT UNSIGNED NOT NULL COMMENT '项目内版本号，从 1 起，删除后不回收',
  sap_version     VARCHAR(64)     NULL COMMENT '所用 SAP2000 版本，如 24.2.0',
  original_name   VARCHAR(255)    NOT NULL COMMENT '下载时的文件名',
  storage_path    VARCHAR(512)    NOT NULL COMMENT '相对 app.model-storage-root',
  sha256          CHAR(64)        NOT NULL COMMENT '内容指纹',
  size_bytes      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  frame_count     INT UNSIGNED    NULL COMMENT '框架数，供列表',
  joint_count     INT UNSIGNED    NULL COMMENT '节点数，供列表',
  note            VARCHAR(2000)   NULL COMMENT 'estimated / 借用土层 / 二期缺失等',
  preview_json    JSON            NULL COMMENT 'joints+frames，模型页线框预览；无则只展示元数据',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sap_model_seq (project_id, seq),
  KEY idx_sap_model_project (project_id),
  CONSTRAINT fk_sap_model_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT fk_sap_model_task FOREIGN KEY (task_id) REFERENCES model_task (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目 SAP 模型版本；二进制在磁盘';
