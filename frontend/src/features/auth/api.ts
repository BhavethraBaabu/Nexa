import { apiFetch } from "@/lib/api-client";
import type { AuthResponse, InvitationPreview } from "@/types/api";

function post<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
}

export const authApi = {
  register: (input: { name: string; email: string; password: string; organizationName: string }) =>
    post<AuthResponse>("/api/v1/auth/register", input),

  login: (input: { email: string; password: string }) => post<AuthResponse>("/api/v1/auth/login", input),

  logout: () => post<void>("/api/v1/auth/logout"),

  requestPasswordReset: (email: string) => post<void>("/api/v1/auth/password-reset/request", { email }),

  confirmPasswordReset: (token: string, newPassword: string) =>
    post<void>("/api/v1/auth/password-reset/confirm", { token, newPassword }),

  previewInvitation: (token: string) => post<InvitationPreview>("/api/v1/auth/invitations/preview", { token }),

  acceptInvitation: (input: { token: string; name: string; password: string }) =>
    post<AuthResponse>("/api/v1/auth/invitations/accept", input),
};

let inflightRefresh: Promise<AuthResponse> | null = null;

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request:
 * refresh tokens rotate on every use, and the server treats a second use of the same token as
 * theft and ends the session. (React StrictMode runs effects twice in development, which would
 * otherwise trigger exactly that.)
 */
export function refreshSession(): Promise<AuthResponse> {
  inflightRefresh ??= post<AuthResponse>("/api/v1/auth/refresh").finally(() => {
    inflightRefresh = null;
  });
  return inflightRefresh;
}
