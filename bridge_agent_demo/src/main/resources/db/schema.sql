-- BridgeAgent 项目账本（第一版）
-- MySQL 8.0 / InnoDB / utf8mb4
-- 建项只强制 name + carriageway；联跨几何由识图/会话/CAD 后写入。

CREATE DATABASE IF NOT EXISTS bridge_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bridge_agent;

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

-- 登录账号。已有库补丁见 db/sys_user.sql 与 db/rbac.sql。
CREATE TABLE IF NOT EXISTS sys_user (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username        VARCHAR(32)     NOT NULL COMMENT '登录名，唯一',
  nickname        VARCHAR(64)     NOT NULL COMMENT '展示名，可改，不进 JWT',
  role_id         BIGINT UNSIGNED NULL COMMENT '一人一角色',
  password_hash   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文，禁止明文',
  token_version   INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT 'JWT 载荷 ver；改密/停用后 +1 废旧票',
  status          VARCHAR(16)     NOT NULL DEFAULT 'enabled' COMMENT 'enabled / disabled',
  avatar_path     VARCHAR(512)    NULL COMMENT '相对 app.avatar-storage-root',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_role (role_id),
  CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
  CONSTRAINT chk_sys_user_status CHECK (status IN ('enabled', 'disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录账号';

INSERT INTO sys_user (username, nickname, role_id, password_hash, token_version, status)
SELECT 'root', 'zmy', r.id, '$2b$10$kSm7DmW8QAxfH8PfMQ6ufuhb/39y3WxLAMnWCLwgYr8BUQOvgnsYK', 1, 'enabled'
FROM sys_role r WHERE r.code = 'admin'
AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'root');
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

CREATE TABLE IF NOT EXISTS project (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统主键，路由与外键均用它',
  name            VARCHAR(200)    NOT NULL COMMENT '项目名称',
  carriageway     VARCHAR(16)     NOT NULL COMMENT 'left / right / undivided',
  code            VARCHAR(64)     NULL COMMENT '工程标号，可重复、可空',
  intro           VARCHAR(2000)   NULL COMMENT '简介',
  region          VARCHAR(64)     NULL COMMENT '地区，如上海市',
  opened_on       DATE            NULL COMMENT '桥梁落地（建成/通车）日；仅知年份时存该年-01-01',
  code_strategy   VARCHAR(32)     NULL COMMENT 'at_opening / current_review',
  girder_type     VARCHAR(64)     NULL COMMENT '主梁形式，项目级',
  layout_type     VARCHAR(64)     NULL COMMENT '结构形式：简支 / 连续 / 桥面连续等',
  material        VARCHAR(32)     NULL COMMENT '主要材料摘要，如 C50',
  field_meta      JSON            NULL COMMENT '可识图字段来源与上次识图值、缺口；见 ProjectFieldMeta',
  status          VARCHAR(32)     NOT NULL DEFAULT 'draft' COMMENT 'draft / modeling / validating / calibrating / done',
  version         INT             NOT NULL DEFAULT 0 COMMENT '行级乐观锁，写时自增',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '建档时间',
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_project_code (code),
  KEY idx_project_name (name),
  KEY idx_project_region_opened (region, opened_on),
  CONSTRAINT chk_project_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
  CONSTRAINT chk_project_carriageway CHECK (carriageway IN ('left', 'right', 'undivided')),
  CONSTRAINT chk_project_code_strategy CHECK (code_strategy IS NULL OR code_strategy IN ('at_opening', 'current_review')),
  CONSTRAINT chk_project_status CHECK (status IN ('draft', 'modeling', 'validating', 'calibrating', 'done'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='桥梁项目（一座桥×一幅一行）';

CREATE TABLE IF NOT EXISTS project_unit (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
  seq             SMALLINT UNSIGNED NOT NULL COMMENT '联序号，从 1 起',
  spans_m         JSON            NOT NULL COMMENT '跨径数组，单位米，如 [40, 60, 40]',
  length_m        DECIMAL(10,3)   NOT NULL COMMENT '联长=跨径之和，写入时重算',
  source          VARCHAR(16)     NOT NULL COMMENT 'drawing / cad / agent / manual',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_unit (project_id, seq),
  KEY idx_unit_project (project_id),
  CONSTRAINT fk_unit_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT chk_unit_seq CHECK (seq >= 1),
  CONSTRAINT chk_unit_length CHECK (length_m > 0),
  CONSTRAINT chk_unit_source CHECK (source IN ('drawing', 'cad', 'agent', 'manual'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目下的一联：跨径在本表；墩台与每根墩柱高度见子表';

-- 一联沿路线的墩或台。n 跨通常对应 seq=0..n 共 n+1 个墩台；P1 双柱不在本表拆行。
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

-- 同一墩上每根柱单独一行：双柱且左右高度不同也要两行。
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

CREATE TABLE IF NOT EXISTS project_file (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
  kind            VARCHAR(16)     NOT NULL COMMENT 'drawing / cad / other',
  original_name   VARCHAR(255)    NOT NULL COMMENT '上传时的文件名',
  storage_path    VARCHAR(512)    NOT NULL COMMENT '对象存储或本地相对路径',
  sha256          CHAR(64)        NOT NULL COMMENT '内容指纹',
  mime_type       VARCHAR(128)    NULL,
  size_bytes      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  parse_status    VARCHAR(16)     NOT NULL DEFAULT 'uploaded' COMMENT 'uploaded / parsing / parsed / failed',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_file_hash (project_id, sha256),
  KEY idx_file_project (project_id),
  CONSTRAINT fk_file_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT chk_file_kind CHECK (kind IN ('drawing', 'cad', 'other')),
  CONSTRAINT chk_file_status CHECK (parse_status IN ('uploaded', 'parsing', 'parsed', 'failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目文件元数据（二进制不进库）';

CREATE TABLE IF NOT EXISTS knowledge_document (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(300)    NOT NULL COMMENT '文献全称',
  category        VARCHAR(16)     NOT NULL COMMENT 'code 规范 / manual 手册 / case 案例',
  family_code     VARCHAR(64)     NULL COMMENT '规范号，如 JTG D62；相同号在界面收成一组，主标签显示最新年版',
  region          VARCHAR(64)     NULL COMMENT '国家 / 上海市 / 通用',
  specialty       VARCHAR(64)     NULL COMMENT '桥梁、抗震、混凝土等',
  effective_from  DATE            NULL COMMENT '生效日',
  effective_to    DATE            NULL COMMENT '废止日；现行本为空',
  original_name   VARCHAR(255)    NULL COMMENT '上传时的 PDF 文件名；种子可无文件',
  storage_path    VARCHAR(512)    NULL COMMENT '相对 knowledge 根目录的路径',
  sha256          CHAR(64)        NULL COMMENT '内容指纹，全公司去重；无文件时为空',
  mime_type       VARCHAR(128)    NULL,
  size_bytes      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  parse_status    VARCHAR(16)     NOT NULL DEFAULT 'unparsed' COMMENT 'unparsed / parsing / parsed / failed',
  merge_status    VARCHAR(16)     NOT NULL DEFAULT 'unmerged' COMMENT 'unmerged / merging / merged / failed',
  split_status    VARCHAR(16)     NOT NULL DEFAULT 'unsplit' COMMENT 'unsplit / splitting / split / failed',
  embed_status    VARCHAR(16)     NOT NULL DEFAULT 'unembedded' COMMENT 'unembedded / embedding / embedded / failed',
  version         INT             NOT NULL DEFAULT 0 COMMENT '行级乐观锁；四步状态用列 CAS，禁止整行 updateById',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_sha (sha256),
  KEY idx_knowledge_family (family_code),
  KEY idx_knowledge_category (category),
  CONSTRAINT chk_knowledge_category CHECK (category IN ('code', 'manual', 'case')),
  CONSTRAINT chk_knowledge_parse CHECK (parse_status IN ('unparsed', 'parsing', 'parsed', 'failed')),
  CONSTRAINT chk_knowledge_merge CHECK (merge_status IN ('unmerged', 'merging', 'merged', 'failed')),
  CONSTRAINT chk_knowledge_split CHECK (split_status IN ('unsplit', 'splitting', 'split', 'failed')),
  CONSTRAINT chk_knowledge_embed CHECK (embed_status IN ('unembedded', 'embedding', 'embedded', 'failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司知识总库（正文文件在磁盘，此表只存元数据）';

CREATE TABLE IF NOT EXISTS project_knowledge (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL,
  document_id     BIGINT UNSIGNED NOT NULL,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_knowledge (project_id, document_id),
  KEY idx_pk_project (project_id),
  CONSTRAINT fk_pk_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT fk_pk_document FOREIGN KEY (document_id) REFERENCES knowledge_document (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目知识启用集：有行即启用';

CREATE TABLE IF NOT EXISTS inquiry_thread (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NULL COMMENT '会话主人；空=旧数据不对普通用户展示',
  title           VARCHAR(200)    NOT NULL DEFAULT '新会话',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_inquiry_project (project_id),
  KEY idx_inquiry_owner (project_id, user_id),
  CONSTRAINT fk_inquiry_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问询会话；按项目+用户私有';

CREATE TABLE IF NOT EXISTS inquiry_message (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  thread_id       BIGINT UNSIGNED NOT NULL,
  role            VARCHAR(16)     NOT NULL COMMENT 'user / agent',
  body            TEXT            NOT NULL,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_message_thread (thread_id),
  CONSTRAINT fk_message_thread FOREIGN KEY (thread_id) REFERENCES inquiry_thread (id) ON DELETE CASCADE,
  CONSTRAINT chk_inquiry_role CHECK (role IN ('user', 'agent', 'event'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问询消息；event 给人看提交记录，不喂模型';

CREATE TABLE IF NOT EXISTS model_task (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL,
  title           VARCHAR(200)    NOT NULL,
  kind            VARCHAR(32)     NOT NULL COMMENT 'drawing_full / drawing_supplement / modeling / analysis',
  status          VARCHAR(16)     NOT NULL DEFAULT 'proposed' COMMENT 'proposed / queued / running / waiting / done / failed / rejected',
  proposal_json   TEXT            NULL COMMENT '识图等待确认提案；有值且 waiting 时禁止默默覆盖账本',
  file_id         BIGINT UNSIGNED NULL COMMENT '补充识别针对的图纸',
  page_kinds_json VARCHAR(512)    NULL COMMENT '页 kind 数组 JSON',
  unit_seq        INT             NULL,
  support_code    VARCHAR(32)     NULL,
  directive       VARCHAR(500)    NULL COMMENT '本轮指令，可空',
  propose_reason  VARCHAR(512)    NULL,
  inquiry_thread_id BIGINT UNSIGNED NULL,
  parent_task_id  BIGINT UNSIGNED NULL COMMENT '自动补充识别所属的父建模任务',
  origin          VARCHAR(32)     NULL COMMENT 'draft / inquiry / drawing_button / auto_supplement',
  created_by_user_id BIGINT UNSIGNED NULL,
  created_by_username VARCHAR(32) NULL,
  agreed_by_user_id BIGINT UNSIGNED NULL,
  agreed_by_username VARCHAR(32) NULL,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_task_project (project_id),
  KEY idx_task_parent (parent_task_id),
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT chk_task_status CHECK (status IN ('proposed', 'queued', 'running', 'waiting', 'done', 'failed', 'rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务卡；过程看 model_task_event';

CREATE TABLE IF NOT EXISTS model_task_event (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id         BIGINT UNSIGNED NOT NULL,
  actor_user_id   BIGINT UNSIGNED NULL,
  actor_username  VARCHAR(32)     NULL COMMENT '人点=登录名；后台记 系统',
  body            TEXT            NOT NULL COMMENT '时间线正文；冲突说明可能较长',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_event_task (task_id),
  CONSTRAINT fk_event_task FOREIGN KEY (task_id) REFERENCES model_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务时间线（作业链）';

-- 记忆：参数袋 / LTM / 工种 STM / 页地图 / 问询 STM（Python 不落盘）
CREATE TABLE IF NOT EXISTS project_param (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  project_id      BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
  param_key       VARCHAR(64)     NOT NULL COMMENT '稳定键，项目内唯一；与固定列/field_meta 含义不得重复',
  label           VARCHAR(128)    NOT NULL COMMENT '桥梁/土木工程专业名词，给人看',
  value_text      VARCHAR(512)    NULL COMMENT '已确认值；复杂结构可后续再拆',
  unit            VARCHAR(32)     NULL COMMENT '单位，如 m、mm',
  source          VARCHAR(16)     NOT NULL COMMENT 'drawing / cad / agent / manual',
  version         INT             NOT NULL DEFAULT 0 COMMENT '行级乐观锁，写时自增',
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

-- SAP 模型版本：元数据在库，.sdb 在 data/models/{projectId}/
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

CREATE TABLE IF NOT EXISTS sys_audit (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  actor_user_id   BIGINT UNSIGNED NULL COMMENT '登录失败可空',
  actor_username  VARCHAR(32)     NOT NULL COMMENT '当时登录名或尝试名',
  action          VARCHAR(32)     NOT NULL,
  target_type     VARCHAR(32)     NULL COMMENT 'user / role / project_member',
  target_id       BIGINT UNSIGNED NULL,
  target_label    VARCHAR(128)    NULL,
  project_id      BIGINT UNSIGNED NULL,
  success         TINYINT(1)      NOT NULL DEFAULT 1,
  reason          VARCHAR(128)    NULL,
  before_text     VARCHAR(128)    NULL,
  after_text      VARCHAR(128)    NULL,
  ip              VARCHAR(64)     NULL,
  user_agent      VARCHAR(256)    NULL,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at),
  KEY idx_audit_actor (actor_username),
  KEY idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限审计；不记干活与知识作业';


