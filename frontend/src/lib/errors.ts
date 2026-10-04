import { ApiClientError } from "./api-client";

/** User-facing message for any thrown value. */
export function errorMessage(err: unknown, fallback = "Something went wrong. Please try again."): string {
  if (err instanceof ApiClientError) {
    if (err.status === 0) return "Can't reach Nexa right now. Check your connection and try again.";
    if (err.status >= 500) return fallback;
    return err.message;
  }
  return fallback;
}

/** Field-level validation messages from a VALIDATION_ERROR response, keyed by field name. */
export function fieldErrors(err: unknown): Record<string, string> {
  if (!(err instanceof ApiClientError)) return {};
  return Object.fromEntries(err.violations.map((v) => [v.field, v.message]));
}
