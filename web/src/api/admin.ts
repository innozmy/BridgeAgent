import { http } from "./http";

export type AdminUser = {
  id: number;
  username: string;
  nickname: string;
  roleId: number;
  roleCode: string;
  roleName: string;
  status: string;
  coveredByHighRole?: boolean;
};

export type AdminRole = {
  id: number;
  code: string;
  name: string;
  builtin: boolean;
  flagSuper: boolean;
  flagKnowledge: boolean;
  flagProjectAdmin: boolean;
};

export type ProjectMember = {
  id?: number;
  projectId: number;
  projectName?: string;
  userId: number;
  username: string;
  nickname: string;
  perm: string;
  overlay?: boolean;
};

export function listAdminUsers() {
  return http<AdminUser[]>("/api/admin/users");
}

export function createAdminUser(body: { username: string; nickname: string; password: string; roleId: number }) {
  return http<AdminUser>("/api/admin/users", { method: "POST", body: JSON.stringify(body) });
}

export function updateAdminUser(id: number, body: { nickname?: string; roleId?: number; status?: string; password?: string }) {
  return http<AdminUser>(`/api/admin/users/${id}`, { method: "PUT", body: JSON.stringify(body) });
}

export function listAdminRoles() {
  return http<AdminRole[]>("/api/admin/roles");
}

export function createAdminRole(body: Partial<AdminRole> & { code: string; name: string }) {
  return http<AdminRole>("/api/admin/roles", { method: "POST", body: JSON.stringify(body) });
}

export function updateAdminRole(id: number, body: Partial<AdminRole> & { code: string; name: string }) {
  return http<AdminRole>(`/api/admin/roles/${id}`, { method: "PUT", body: JSON.stringify(body) });
}

export function deleteAdminRole(id: number) {
  return http<void>(`/api/admin/roles/${id}`, { method: "DELETE" });
}

export function listAdminAudits(params?: { action?: string; actor?: string; page?: number; size?: number }) {
  const q = new URLSearchParams();
  if (params?.action) {
    q.set("action", params.action);
  }
  if (params?.actor) {
    q.set("actor", params.actor);
  }
  q.set("page", String(params?.page ?? 1));
  q.set("size", String(params?.size ?? 20));
  return http<AuditPage>(`/api/admin/audits?${q.toString()}`);
}

export type AuditRow = {
  id: number;
  createdAt: string;
  actorUserId?: number | null;
  actorUsername: string;
  action: string;
  targetType?: string | null;
  targetId?: number | null;
  targetLabel?: string | null;
  projectId?: number | null;
  success: boolean;
  reason?: string | null;
  beforeText?: string | null;
  afterText?: string | null;
  ip?: string | null;
};

export type AuditPage = {
  total: number;
  page: number;
  size: number;
  records: AuditRow[];
};

export function listProjectMembers(projectId: number | string) {
  return http<ProjectMember[]>(`/api/projects/${projectId}/members`);
}

export function assignProjectMember(projectId: number | string, userId: number, perm: "read" | "operate") {
  return http<void>(`/api/admin/projects/${projectId}/members`, {
    method: "PUT",
    body: JSON.stringify({ userId, perm }),
  });
}

export function removeProjectMember(projectId: number | string, userId: number) {
  return http<void>(`/api/admin/projects/${projectId}/members/${userId}`, { method: "DELETE" });
}
