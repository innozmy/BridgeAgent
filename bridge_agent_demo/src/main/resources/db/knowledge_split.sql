-- 已有库补知识过长分割状态。新库直接跑 schema.sql 即可。
-- MySQL 8.0 没有 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column。
USE bridge_agent;

ALTER TABLE knowledge_document
  ADD COLUMN split_status VARCHAR(16) NOT NULL DEFAULT 'unsplit' COMMENT 'unsplit / splitting / split / failed' AFTER merge_status;

ALTER TABLE knowledge_document
  ADD CONSTRAINT chk_knowledge_split CHECK (split_status IN ('unsplit', 'splitting', 'split', 'failed'));
