import { downloadFile, http, httpForm } from "./http";
import type {
  FileBatchUpload,
  ProjectCreateBody,
  ProjectDetail,
  ProjectParamBatchBody,
  ProjectRecord,
  ProjectUnitBatchBody,
  ProjectUpdateBody,
} from "./types";

export function listProjects() {
  return http<ProjectRecord[]>("/api/projects");
}

export function getProject(id: number | string) {
  return http<ProjectDetail>(`/api/projects/${id}`);
}

export function createProject(body: ProjectCreateBody) {
  return http<ProjectDetail>("/api/projects", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateProject(id: number | string, body: ProjectUpdateBody) {
  return http<ProjectDetail>(`/api/projects/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function replaceProjectUnits(id: number | string, body: ProjectUnitBatchBody) {
  return http<ProjectDetail>(`/api/projects/${id}/units`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function replaceProjectParams(id: number | string, body: ProjectParamBatchBody) {
  return http<ProjectDetail>(`/api/projects/${id}/params`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function deleteProject(id: number | string) {
  return http<void>(`/api/projects/${id}`, { method: "DELETE" });
}

export function uploadProjectFiles(projectId: number | string, kind: string, files: File[]) {
  const body = new FormData();
  body.append("kind", kind);
  files.forEach((file) => body.append("files", file));
  return httpForm<FileBatchUpload>(`/api/projects/${projectId}/files`, body);
}

export function downloadProjectFile(projectId: number | string, fileId: number, filename: string) {
  return downloadFile(`/api/projects/${projectId}/files/${fileId}/download`, filename);
}

export function deleteProjectFile(projectId: number | string, fileId: number) {
  return http<void>(`/api/projects/${projectId}/files/${fileId}`, { method: "DELETE" });
}
