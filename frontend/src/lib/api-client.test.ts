import { afterEach, describe, expect, it, vi } from "vitest";
import { apiFetch, ApiClientError } from "./api-client";

function mockFetch(response: Response | Error) {
  const fn = response instanceof Error ? vi.fn().mockRejectedValue(response) : vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("apiFetch", () => {
  it("returns parsed JSON on success", async () => {
    const fetchMock = mockFetch(new Response(JSON.stringify({ name: "nexa" }), { status: 200 }));

    await expect(apiFetch<{ name: string }>("/api/v1/system/info")).resolves.toEqual({ name: "nexa" });
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/v1/system/info", expect.any(Object));
  });

  it("maps the backend error body to ApiClientError", async () => {
    mockFetch(
      new Response(
        JSON.stringify({
          timestamp: "2026-10-03T20:30:00Z",
          status: 400,
          error: "VALIDATION_ERROR",
          message: "Meeting title is required",
          path: "/api/v1/meetings",
          correlationId: "abc",
          violations: [{ field: "title", message: "Meeting title is required" }],
        }),
        { status: 400 },
      ),
    );

    const error = await apiFetch("/api/v1/meetings").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiClientError);
    expect(error).toMatchObject({
      status: 400,
      code: "VALIDATION_ERROR",
      message: "Meeting title is required",
      correlationId: "abc",
      violations: [{ field: "title", message: "Meeting title is required" }],
    });
  });

  it("handles non-JSON error responses", async () => {
    mockFetch(new Response("Bad Gateway", { status: 502, headers: { "X-Correlation-Id": "xyz" } }));

    await expect(apiFetch("/api/v1/meetings")).rejects.toMatchObject({
      status: 502,
      code: "HTTP_ERROR",
      correlationId: "xyz",
    });
  });

  it("reports network failures", async () => {
    mockFetch(new TypeError("fetch failed"));

    await expect(apiFetch("/api/v1/meetings")).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });

  it("sets JSON content type only when a body is sent", async () => {
    const fetchMock = mockFetch(new Response(null, { status: 204 }));

    await apiFetch("/api/v1/meetings", { method: "POST", body: JSON.stringify({ title: "x" }) });
    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("Content-Type")).toBe("application/json");
  });
});
