import { describe, expect, it } from "vitest";
import { ApiClientError } from "./api-client";
import { errorMessage, fieldErrors } from "./errors";

describe("errorMessage", () => {
  it("shows the API's message for client errors", () => {
    expect(errorMessage(new ApiClientError(409, "CONFLICT", "An account with this email already exists"))).toBe(
      "An account with this email already exists",
    );
  });

  it("hides server error details", () => {
    expect(errorMessage(new ApiClientError(500, "INTERNAL_ERROR", "stack trace"), "Fallback")).toBe("Fallback");
  });

  it("explains network failures", () => {
    expect(errorMessage(new ApiClientError(0, "NETWORK_ERROR", "x"))).toMatch(/can't reach nexa/i);
  });

  it("falls back for unknown errors", () => {
    expect(errorMessage(new Error("boom"), "Fallback")).toBe("Fallback");
  });
});

describe("fieldErrors", () => {
  it("maps violations by field", () => {
    const err = new ApiClientError(400, "VALIDATION_ERROR", "bad", undefined, [{ field: "email", message: "Email must be valid" }]);
    expect(fieldErrors(err)).toEqual({ email: "Email must be valid" });
  });
});
