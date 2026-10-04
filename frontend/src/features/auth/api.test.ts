import { afterEach, describe, expect, it, vi } from "vitest";
import { refreshSession } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

const authResponse = {
  accessToken: "token",
  tokenType: "Bearer",
  expiresIn: 900,
  user: { id: "1", name: "Alice", email: "alice@acme.com", role: "ADMIN", organization: { id: "o", name: "Acme" } },
};

describe("refreshSession", () => {
  it("shares one request between concurrent callers so the refresh token is used only once", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify(authResponse), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const [a, b] = await Promise.all([refreshSession(), refreshSession()]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(a).toEqual(authResponse);
    expect(b).toEqual(authResponse);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST", credentials: "include" });
  });

  it("allows a new refresh after the previous one settles", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify(authResponse), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await refreshSession();
    await refreshSession();

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("clears the in-flight request after a failure", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 401, error: "UNAUTHORIZED", message: "expired" }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(authResponse), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(refreshSession()).rejects.toMatchObject({ status: 401 });
    await expect(refreshSession()).resolves.toEqual(authResponse);
  });
});
