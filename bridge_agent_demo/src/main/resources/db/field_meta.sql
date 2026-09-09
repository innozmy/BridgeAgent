-- 已有库补项目字段来源列。新库直接跑 schema.sql 即可。
-- MySQL 8.0 没有 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column。
USE bridge_agent;

ALTER TABLE project
  ADD COLUMN field_meta JSON NULL COMMENT '可识图字段来源与上次识图值、缺口' AFTER material;
