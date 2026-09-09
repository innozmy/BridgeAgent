import { downloadFile, http, httpForm } from "./http";
import type { KnowledgeDocument, KnowledgeSearchResult, KnowledgeUploadResult, ProjectKnowledgePayload } from "./types";

export function listKnowledgeDocuments() {
  return http<KnowledgeDocument[]>("/api/knowledge/documents");
}

export function uploadKnowledgeDocument(body: FormData) {
  return httpForm<KnowledgeUploadResult>("/api/knowledge/documents", body);
}

export function downloadKnowledgeDocument(id: number, filename: string) {
  return downloadFile(`/api/knowledge/documents/${id}/download`, filename);
}

export function deleteKnowledgeDocument(id: number) {
  return http<void>(`/api/knowledge/documents/${id}`, { method: "DELETE" });
}

export function parseKnowledgeDocument(id: number, force = false) {
  const query = force ? "?force=true" : "";
  return http<{ started: boolean; needConfirm: boolean }>(
    `/api/knowledge/documents/${id}/parse${query}`,
    { method: "POST" },
  );
}

export function mergeKnowledgeDocument(id: number, force = false) {
  const query = force ? "?force=true" : "";
  return http<{ started: boolean; needConfirm: boolean }>(
    `/api/knowledge/documents/${id}/merge${query}`,
    { method: "POST" },
  );
}

export function splitKnowledgeDocument(id: number, force = false) {
  const query = force ? "?force=true" : "";
  return http<{ started: boolean; needConfirm: boolean }>(
    `/api/knowledge/documents/${id}/split${query}`,
    { method: "POST" },
  );
}

export function embedKnowledgeDocument(id: number, force = false) {
  const query = force ? "?force=true" : "";
  return http<{ started: boolean; needConfirm: boolean }>(
    `/api/knowledge/documents/${id}/embed${query}`,
    { method: "POST" },
  );
}

export function getProjectKnowledge(projectId: number | string) {
  return http<ProjectKnowledgePayload>(`/api/projects/${projectId}/knowledge`);
}

export function enableProjectKnowledge(projectId: number | string, docId: number) {
  return http<void>(`/api/projects/${projectId}/knowledge/${docId}`, { method: "PUT" });
}

export function disableProjectKnowledge(projectId: number | string, docId: number) {
  return http<void>(`/api/projects/${projectId}/knowledge/${docId}`, { method: "DELETE" });
}

export function searchProjectKnowledge(projectId: number | string, query: string) {
  return http<KnowledgeSearchResult>(`/api/projects/${projectId}/knowledge/search`, {
    method: "POST",
    body: JSON.stringify({ query }),
  });
}
