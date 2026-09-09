-- 任务卡：proposed / rejected；后台跑；问询 event；kind 枚举。已有库执行一次。
USE bridge_agent;

UPDATE model_task SET kind = 'drawing_full' WHERE kind IN ('图纸识别');
UPDATE model_task SET kind = 'modeling' WHERE kind NOT IN ('drawing_full', 'drawing_supplement', 'modeling', 'analysis');

ALTER TABLE model_task DROP CHECK chk_task_status;

ALTER TABLE model_task
  MODIFY COLUMN kind VARCHAR(32) NOT NULL COMMENT 'drawing_full / drawing_supplement / modeling / analysis',
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'proposed' COMMENT 'proposed / running / waiting / done / failed / rejected',
  ADD COLUMN file_id BIGINT UNSIGNED NULL COMMENT '补充识别针对的图纸' AFTER proposal_json,
  ADD COLUMN page_kinds_json VARCHAR(512) NULL COMMENT '页 kind 数组 JSON' AFTER file_id,
  ADD COLUMN unit_seq INT NULL COMMENT '可选联号' AFTER page_kinds_json,
  ADD COLUMN support_code VARCHAR(32) NULL COMMENT '可选墩台编号' AFTER unit_seq,
  ADD COLUMN directive VARCHAR(500) NULL COMMENT '本轮指令，可空' AFTER support_code,
  ADD COLUMN propose_reason VARCHAR(512) NULL COMMENT '问询提议原因' AFTER directive,
  ADD COLUMN inquiry_thread_id BIGINT UNSIGNED NULL COMMENT '来自哪条问询' AFTER propose_reason;

ALTER TABLE model_task
  ADD CONSTRAINT chk_task_status CHECK (status IN ('proposed', 'running', 'waiting', 'done', 'failed', 'rejected'));

ALTER TABLE inquiry_message DROP CHECK chk_inquiry_role;
ALTER TABLE inquiry_message
  ADD CONSTRAINT chk_inquiry_role CHECK (role IN ('user', 'agent', 'event'));

ALTER TABLE inquiry_stm
  ADD COLUMN submitted_json JSON NULL COMMENT '已同意提交的 taskId JSON 数组' AFTER recent_json;
