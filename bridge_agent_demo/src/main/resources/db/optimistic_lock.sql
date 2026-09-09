-- 行级乐观锁：账本 project / 参数袋 / 知识文献。已有库执行一次。
USE bridge_agent;
SET NAMES utf8mb4;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bridge_agent' AND TABLE_NAME = 'project' AND COLUMN_NAME = 'version') = 0,
  'ALTER TABLE project ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''行级乐观锁，写时自增'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bridge_agent' AND TABLE_NAME = 'project_param' AND COLUMN_NAME = 'version') = 0,
  'ALTER TABLE project_param ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''行级乐观锁，写时自增'' AFTER source',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bridge_agent' AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'version') = 0,
  'ALTER TABLE knowledge_document ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''行级乐观锁；四步状态仍用列 CAS，禁止整行覆盖'' AFTER embed_status',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
