import { http, httpForm } from "./http";

export type ProjectPerm = {
  projectId: number;
  perm: "read" | "operate";
};

export type AuthPayload = {
  token?: string;
  userId: number;
  username: string;
  nickname: string;
  expireAt?: number;
  roleCode?: string;
  roleName?: string;
  superFlag?: boolean;
  knowledge?: boolean;
  projectAdmin?: boolean;
  allProjectsOperate?: boolean;
  projectPerms?: ProjectPerm[];
  avatarUrl?: string | null;
};

export function login(username: string, password: string): Promise<AuthPayload> {
  return http<AuthPayload>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function fetchMe(): Promise<AuthPayload> {
  return http<AuthPayload>("/api/auth/me");
}

export function updateProfile(nickname: string): Promise<AuthPayload> {
  return http<AuthPayload>("/api/auth/profile", {
    method: "PUT",
    body: JSON.stringify({ nickname }),
  });
}

export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return http<void>("/api/auth/password", {
    method: "PUT",
    body: JSON.stringify({ oldPassword, newPassword }),
  });
}

export function uploadAvatar(file: File): Promise<AuthPayload> {
  const body = new FormData();
  body.append("file", file);
  return httpForm<AuthPayload>("/api/auth/avatar", body);
}

export function toSessionProfile(auth: AuthPayload) {
  return {
    userId: auth.userId,
    username: auth.username,
    nickname: auth.nickname,
    roleCode: auth.roleCode,
    roleName: auth.roleName,
    superFlag: !!auth.superFlag,
    knowledge: !!auth.knowledge,
    projectAdmin: !!auth.projectAdmin,
    allProjectsOperate: !!auth.allProjectsOperate,
    projectPerms: auth.projectPerms ?? [],
    avatarUrl: auth.avatarUrl,
  };
}
