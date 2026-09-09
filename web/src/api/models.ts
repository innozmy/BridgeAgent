import { downloadFile, http } from "./http";
import type { ProjectSapModel } from "./types";

export function listSapModels(projectId: number | string) {
  return http<ProjectSapModel[]>(`/api/projects/${projectId}/models`);
}

export function getSapModel(projectId: number | string, modelId: number) {
  return http<ProjectSapModel>(`/api/projects/${projectId}/models/${modelId}`);
}

export function downloadSapModel(projectId: number | string, modelId: number, filename: string) {
  return downloadFile(`/api/projects/${projectId}/models/${modelId}/download`, filename);
}

export function deleteSapModel(projectId: number | string, modelId: number) {
  return http<void>(`/api/projects/${projectId}/models/${modelId}`, { method: "DELETE" });
}
