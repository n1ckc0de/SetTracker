import { apiFetch } from "./http";
import type { AuthResponse } from "./types";

export function register(email: string, password: string, name: string) {
  return apiFetch<AuthResponse>(
    "/api/auth/register",
    {
      method: "POST",
      body: JSON.stringify({ email, password, name }),
    },
    { auth: false },
  );
}

export function login(email: string, password: string) {
  return apiFetch<AuthResponse>(
    "/api/auth/login",
    {
      method: "POST",
      body: JSON.stringify({ email, password }),
    },
    { auth: false },
  );
}
