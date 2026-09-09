import { http } from "./http";
import type { ModelTaskDetail, TaskKind } from "./types";

export type TaskCardBody = {
  kind: TaskKind | string;
  fileId?: number | null;
  pageKinds?: string[];
  unitSeq?: number | null;
  supportCode?: string | null;
  directive?: string | null;
  proposeReason?: string | null;
};

export function listModelTasks(projectId: number | string) {
  return http<ModelTaskDetail[]>(`/api/projects/${projectId}/tasks`);
}

export function draftTaskCard(projectId: number | string, body: TaskCardBody) {
  return http<ModelTaskDetail>(`/api/projects/${projectId}/tasks/cards`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function editTaskCard(projectId: number | string, taskId: number, body: TaskCardBody) {
  return http<ModelTaskDetail>(`/api/projects/${projectId}/tasks/${taskId}/card`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function agreeTaskCard(
  projectId: number | string,
  taskId: number,
  inquiryThreadId?: number | null,
) {
  return http<ModelTaskDetail>(`/api/projects/${projectId}/tasks/${taskId}/agree`, {
    method: "POST",
    body: JSON.stringify({ inquiryThreadId: inquiryThreadId ?? null }),
  });
}

export function dismissTaskCard(
  projectId: number | string,
  taskId: number,
  inquiryThreadId?: number | null,
) {
  return http<ModelTaskDetail>(`/api/projects/${projectId}/tasks/${taskId}/dismiss`, {
    method: "POST",
    body: JSON.stringify({ inquiryThreadId: inquiryThreadId ?? null }),
  });
}

/** 识别本项目图纸：立刻 running，后台识图，过程进任务时间线 */
export function parseProjectDrawings(projectId: number | string) {
  return http<ModelTaskDetail>(`/api/projects/${projectId}/tasks/parse`, { method: "POST" });
}

export function confirmDrawingParse(projectId: number | string, taskId: number) {
  return http<ModelTaskDetail>(`/api/projects/${projectId}/tasks/${taskId}/parse/confirm`, {
    method: "POST",
  });
}

export function rejectDrawingParse(projectId: number | string, taskId: number) {
  return http<ModelTaskDetail>(`/api/projects/${projectId}/tasks/${taskId}/parse/reject`, {
    method: "POST",
  });
}
