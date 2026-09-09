-- 已有库补知识嵌入状态。新库直接跑 schema.sql 即可。
-- MySQL 8.0 没有 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column。
USE bridge_agent;

ALTER TABLE knowledge_document
  ADD COLUMN embed_status VARCHAR(16) NOT NULL DEFAULT 'unembedded' COMMENT 'unembedded / embedding / embedded / failed' AFTER split_status;

ALTER TABLE knowledge_document
  ADD CONSTRAINT chk_knowledge_embed CHECK (embed_status IN ('unembedded', 'embedding', 'embedded', 'failed'));
