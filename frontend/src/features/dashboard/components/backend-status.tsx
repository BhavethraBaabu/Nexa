"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiClientError } from "@/lib/api-client";
import type { SystemInfo } from "@/types/api";

type Status =
  | { state: "loading" }
  | { state: "ok"; info: SystemInfo }
  | { state: "error"; message: string };

export function BackendStatus() {
  const [status, setStatus] = useState<Status>({ state: "loading" });

  const check = useCallback(async () => {
    setStatus({ state: "loading" });
    try {
      const info = await apiFetch<SystemInfo>("/api/v1/system/info");
      setStatus({ state: "ok", info });
    } catch (err) {
      const message = err instanceof ApiClientError ? err.message : "Unexpected error";
      setStatus({ state: "error", message });
    }
  }, []);

  useEffect(() => {
    // Fetching on mount is the intended side effect here.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void check();
  }, [check]);

  return (
    <section aria-labelledby="backend-status" className="rounded-lg border border-border bg-surface p-5">
      <h2 id="backend-status" className="text-sm font-medium text-muted">
        API status
      </h2>
      <div className="mt-2 flex items-center gap-2" role="status" aria-live="polite">
        {status.state === "loading" && <p className="text-sm">Checking connection…</p>}
        {status.state === "ok" && (
          <>
            <span className="h-2 w-2 rounded-full bg-success" aria-hidden="true" />
            <p className="text-sm">
              Connected to <span className="font-medium">{status.info.name}</span> API {status.info.apiVersion}
            </p>
          </>
        )}
        {status.state === "error" && (
          <>
            <span className="h-2 w-2 rounded-full bg-danger" aria-hidden="true" />
            <p className="text-sm">{status.message}</p>
            <button
              type="button"
              onClick={() => void check()}
              className="ml-auto rounded-md border border-border px-3 py-1 text-sm hover:bg-surface-strong"
            >
              Retry
            </button>
          </>
        )}
      </div>
    </section>
  );
}
