import { http } from "./http";
import type { InquiryThread, InquiryThreadDetail } from "./types";

export function listInquiryThreads(projectId: number | string) {
  return http<InquiryThread[]>(`/api/projects/${projectId}/inquiries`);
}

export function createInquiryThread(projectId: number | string) {
  return http<InquiryThread>(`/api/projects/${projectId}/inquiries`, { method: "POST" });
}

export function getInquiryThread(projectId: number | string, threadId: number) {
  return http<InquiryThreadDetail>(`/api/projects/${projectId}/inquiries/${threadId}`);
}

export function postInquiryMessage(projectId: number | string, threadId: number, body: string) {
  return http<InquiryThreadDetail>(`/api/projects/${projectId}/inquiries/${threadId}/messages`, {
    method: "POST",
    body: JSON.stringify({ body }),
  });
}

export function deleteInquiryThread(projectId: number | string, threadId: number) {
  return http<void>(`/api/projects/${projectId}/inquiries/${threadId}`, { method: "DELETE" });
}
