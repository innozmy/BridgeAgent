-- 已有库补知识合并状态。新库直接跑 schema.sql 即可。
-- MySQL 8.0 没有 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column。
USE bridge_agent;

ALTER TABLE knowledge_document
  ADD COLUMN merge_status VARCHAR(16) NOT NULL DEFAULT 'unmerged' COMMENT 'unmerged / merging / merged / failed' AFTER parse_status;

ALTER TABLE knowledge_document
  ADD CONSTRAINT chk_knowledge_merge CHECK (merge_status IN ('unmerged', 'merging', 'merged', 'failed'));
