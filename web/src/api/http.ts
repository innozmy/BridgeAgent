import { clearSession, getToken } from "@/stores/session";
import type { ApiResult } from "./types";

let redirectingToLogin = false;

function authHeaders(init?: HeadersInit): Headers {
  const headers = new Headers(init);
  const token = getToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

/** 401 = 没票或票废。清 localStorage 并跳登录，避免各页各自处理 */
async function handleUnauthorized(response: Response): Promise<void> {
  let kicked = false;
  try {
    const body = (await response.clone().json()) as ApiResult<unknown>;
    kicked = (body.msg ?? "").includes("其他设备");
  } catch {
    kicked = false;
  }
  clearSession();
  if (redirectingToLogin || window.location.pathname === "/login") {
    return;
  }
  redirectingToLogin = true;
  const redirect = `${window.location.pathname}${window.location.search}`;
  const params = new URLSearchParams();
  if (redirect && redirect !== "/") {
    params.set("redirect", redirect);
  }
  if (kicked) {
    params.set("reason", "kicked");
  }
  const query = params.toString() ? `?${params.toString()}` : "";
  window.location.assign(`/login${query}`);
}

async function readJsonResult<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    await handleUnauthorized(response);
    throw new Error("未登录或登录已失效");
  }
  let body: ApiResult<T>;
  try {
    body = (await response.json()) as ApiResult<T>;
  } catch {
    throw new Error(`请求失败（${response.status}）`);
  }
  if (response.status === 403) {
    throw new Error(body.msg || "没有权限");
  }
  if (!response.ok || body.code !== 1) {
    throw new Error(body.msg || `请求失败（${response.status}）`);
  }
  return body.data;
}

export async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = authHeaders(init?.headers);
  if (!headers.has("Content-Type") && init?.body) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(path, {
    ...init,
    headers,
  });
  return readJsonResult<T>(response);
}

export async function httpForm<T>(path: string, body: FormData): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: authHeaders(),
    body,
  });
  return readJsonResult<T>(response);
}

export async function downloadFile(path: string, filename: string): Promise<void> {
  const response = await fetch(path, { headers: authHeaders() });
  if (response.status === 401) {
    await handleUnauthorized(response);
    throw new Error("未登录或登录已失效");
  }
  if (response.status === 403) {
    throw new Error("没有权限");
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (!response.ok || contentType.includes("application/json")) {
    throw await readError(response);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function readError(response: Response): Promise<Error> {
  try {
    const body = (await response.json()) as ApiResult<unknown>;
    return new Error(body.msg || `请求失败（${response.status}）`);
  } catch {
    return new Error(`请求失败（${response.status}）`);
  }
}
