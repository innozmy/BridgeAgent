-- 识图闭环：已有库补列。新库直接跑 schema.sql 即可，不必再执行本文件。
-- MySQL 8.0 没有 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column。
USE bridge_agent;

ALTER TABLE model_task
  ADD COLUMN proposal_json TEXT NULL COMMENT '识图等待确认提案；有值且 waiting 时禁止默默覆盖账本' AFTER status;

ALTER TABLE model_task_event
  MODIFY COLUMN body TEXT NOT NULL COMMENT '时间线正文；冲突说明可能较长';
