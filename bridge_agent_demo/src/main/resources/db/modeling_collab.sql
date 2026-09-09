-- 建模子识图：parent_task_id 挂到父建模任务。已有库执行一次。
USE bridge_agent;

ALTER TABLE model_task
  ADD COLUMN parent_task_id BIGINT UNSIGNED NULL COMMENT '自动补充识别所属的父建模任务' AFTER inquiry_thread_id;

ALTER TABLE model_task
  ADD KEY idx_task_parent (parent_task_id);
