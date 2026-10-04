import { API_BASE_URL } from "./config";
import type { ApiError } from "@/types/api";

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId?: string;
  readonly violations: NonNullable<ApiError["violations"]>;

  constructor(status: number, code: string, message: string, correlationId?: string, violations: ApiError["violations"] = []) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.violations = violations ?? [];
  }
}

function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as ApiError).status === "number" &&
    typeof (value as ApiError).error === "string" &&
    typeof (value as ApiError).message === "string"
  );
}

/**
 * Typed fetch wrapper for the Nexa API. Non-2xx responses are converted into
 * {@link ApiClientError}, using the backend's standard error body when present.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  // FormData sets its own multipart Content-Type (with boundary); everything else is JSON.
  if (init.body !== undefined && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    // credentials: "include" lets the browser send/receive the httpOnly refresh cookie.
    response = await fetch(`${API_BASE_URL}${path}`, { credentials: "include", ...init, headers });
  } catch {
    throw new ApiClientError(0, "NETWORK_ERROR", "Unable to reach the Nexa API");
  }

  const correlationId = response.headers.get("X-Correlation-Id") ?? undefined;
  const text = await response.text();
  let body: unknown = undefined;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = undefined;
    }
  }

  if (!response.ok) {
    if (isApiError(body)) {
      throw new ApiClientError(body.status, body.error, body.message, body.correlationId ?? correlationId, body.violations);
    }
    throw new ApiClientError(response.status, "HTTP_ERROR", `Request failed with status ${response.status}`, correlationId);
  }

  return body as T;
}
