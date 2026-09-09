import { computed, ref } from "vue";

const TOKEN_KEY = "ba_token";
const PROFILE_KEY = "ba_profile";

export type ProjectPerm = {
  projectId: number;
  perm: "read" | "operate";
};

export type AuthProfile = {
  userId: number;
  username: string;
  nickname: string;
  roleCode?: string | null;
  roleName?: string | null;
  superFlag?: boolean;
  knowledge?: boolean;
  projectAdmin?: boolean;
  allProjectsOperate?: boolean;
  projectPerms?: ProjectPerm[];
  avatarUrl?: string | null;
};

export const currentProfile = ref<AuthProfile | null>(readProfile());

export const canManageProjects = computed(
  () => !!(currentProfile.value?.superFlag || currentProfile.value?.projectAdmin || currentProfile.value?.allProjectsOperate),
);
export const canWriteKnowledge = computed(
  () => !!(currentProfile.value?.superFlag || currentProfile.value?.knowledge),
);
export const canManageUsers = computed(() => !!currentProfile.value?.superFlag);
export const canOpenSystem = computed(
  () => !!(currentProfile.value?.superFlag || currentProfile.value?.projectAdmin || currentProfile.value?.allProjectsOperate),
);

export function canOperateProject(projectId: number | string): boolean {
  const p = currentProfile.value;
  if (!p) {
    return false;
  }
  if (p.superFlag || p.projectAdmin || p.allProjectsOperate) {
    return true;
  }
  return (p.projectPerms ?? []).some((row) => String(row.projectId) === String(projectId) && row.perm === "operate");
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setSession(token: string, profile: AuthProfile): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
  currentProfile.value = profile;
}

export function applyProfile(profile: AuthProfile): void {
  const token = getToken();
  if (!token) {
    return;
  }
  setSession(token, profile);
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(PROFILE_KEY);
  currentProfile.value = null;
}

function readProfile(): AuthProfile | null {
  const raw = localStorage.getItem(PROFILE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as AuthProfile;
    if (!parsed?.userId || !parsed.username) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}
