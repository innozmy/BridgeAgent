-- 任务卡增加 queued：人已同意、等资源车道。可重复执行。
USE bridge_agent;
SET NAMES utf8mb4;

ALTER TABLE model_task DROP CHECK chk_task_status;
ALTER TABLE model_task
  ADD CONSTRAINT chk_task_status CHECK (
    status IN ('proposed', 'queued', 'running', 'waiting', 'done', 'failed', 'rejected')
  );
