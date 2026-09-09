-- 作业链操作人 + 问询按用户私有。可重复执行。
USE bridge_agent;
SET NAMES utf8mb4;

SET @db := DATABASE();
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inquiry_thread' AND COLUMN_NAME='user_id')=0,
  'ALTER TABLE inquiry_thread ADD COLUMN user_id BIGINT UNSIGNED NULL COMMENT ''会话主人；空=旧共享会话，不对普通用户展示'' AFTER project_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inquiry_thread' AND INDEX_NAME='idx_inquiry_owner')=0,
  'ALTER TABLE inquiry_thread ADD KEY idx_inquiry_owner (project_id, user_id)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='model_task' AND COLUMN_NAME='origin')=0,
  'ALTER TABLE model_task ADD COLUMN origin VARCHAR(32) NULL COMMENT ''draft / inquiry / drawing_button / auto_supplement'' AFTER parent_task_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='model_task' AND COLUMN_NAME='created_by_user_id')=0,
  'ALTER TABLE model_task ADD COLUMN created_by_user_id BIGINT UNSIGNED NULL AFTER origin',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='model_task' AND COLUMN_NAME='created_by_username')=0,
  'ALTER TABLE model_task ADD COLUMN created_by_username VARCHAR(32) NULL AFTER created_by_user_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='model_task' AND COLUMN_NAME='agreed_by_user_id')=0,
  'ALTER TABLE model_task ADD COLUMN agreed_by_user_id BIGINT UNSIGNED NULL AFTER created_by_username',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='model_task' AND COLUMN_NAME='agreed_by_username')=0,
  'ALTER TABLE model_task ADD COLUMN agreed_by_username VARCHAR(32) NULL AFTER agreed_by_user_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='model_task_event' AND COLUMN_NAME='actor_user_id')=0,
  'ALTER TABLE model_task_event ADD COLUMN actor_user_id BIGINT UNSIGNED NULL AFTER task_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='model_task_event' AND COLUMN_NAME='actor_username')=0,
  'ALTER TABLE model_task_event ADD COLUMN actor_username VARCHAR(32) NULL COMMENT ''人点=登录名；后台=@Async 记 系统'' AFTER actor_user_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
