import type { ApiErrorBody } from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? "";

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors?: Record<string, string>;
  readonly body: ApiErrorBody;

  constructor(body: ApiErrorBody) {
    super(body.message || `Request failed (${body.status})`);
    this.name = "ApiError";
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors;
    this.body = body;
  }
}

type TokenGetter = () => string | null;

let getAccessToken: TokenGetter = () => null;

export function configureApiAuth(getter: TokenGetter) {
  getAccessToken = getter;
}

export function apiBaseUrl(): string {
  return API_BASE || window.location.origin;
}

export function wsBaseUrl(): string {
  const configured = import.meta.env.VITE_WS_BASE_URL as string | undefined;
  if (configured) {
    return configured.replace(/\/$/, "");
  }
  if (API_BASE) {
    return API_BASE.replace(/^http/, "ws");
  }
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}`;
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiError({
      ...body,
      status: body.status ?? response.status,
      message: body.message || response.statusText || "Request failed",
    });
  } catch {
    return new ApiError({
      status: response.status,
      message: response.statusText || "Request failed",
    });
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  options: { auth?: boolean } = { auth: true },
): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }
  if (options.auth !== false) {
    const token = getAccessToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.headers.get("content-length") === "0") {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export function toQuery(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}
