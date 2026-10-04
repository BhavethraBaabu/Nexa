"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/auth-provider";
import { errorMessage } from "@/lib/errors";
import type { MeetingSummary, PageResponse } from "@/types/api";
import { dateLabel, meetingsApi } from "../api";
import { StatusBadge } from "./status-badge";

type Load = { state: "loading" } | { state: "error"; message: string } | { state: "ready"; page: PageResponse<MeetingSummary> };

export function MeetingsList() {
  const { authFetch } = useAuth();
  const api = useMemo(() => meetingsApi(authFetch), [authFetch]);
  const [page, setPage] = useState(0);
  const [load, setLoad] = useState<Load>({ state: "loading" });

  const refresh = useCallback(async () => {
    try {
      setLoad({ state: "ready", page: await api.list(page) });
    } catch (err) {
      setLoad({ state: "error", message: errorMessage(err) });
    }
  }, [api, page]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
  }, [refresh]);

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Meetings</h1>
          <p className="mt-1 text-sm text-muted">Transcripts and what came out of them.</p>
        </div>
        <Link href="/meetings/new" className="rounded-md bg-foreground px-3 py-2 text-sm font-medium text-background hover:bg-foreground/90">
          New meeting
        </Link>
      </header>

      {load.state === "loading" && <p className="text-sm text-muted" role="status">Loading meetings…</p>}
      {load.state === "error" && (
        <div className="flex items-center gap-3">
          <Alert>{load.message}</Alert>
          <Button variant="secondary" onClick={() => void refresh()}>Retry</Button>
        </div>
      )}
      {load.state === "ready" && load.page.totalElements === 0 && (
        <div className="rounded-lg border border-dashed border-border bg-surface px-6 py-12 text-center">
          <p className="text-sm font-medium">No meetings yet</p>
          <p className="mt-1 text-sm text-muted">Paste or upload a transcript to turn it into tasks, decisions and risks.</p>
          <Link href="/meetings/new" className="mt-4 inline-block text-sm font-medium underline">
            Add your first meeting
          </Link>
        </div>
      )}
      {load.state === "ready" && load.page.totalElements > 0 && (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border bg-surface">
            {load.page.content.map((m) => (
              <li key={m.id}>
                <Link href={`/meetings/${m.id}`} className="flex items-center gap-4 px-5 py-3 hover:bg-surface-strong">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{m.title}</p>
                    <p className="text-xs text-muted">
                      {dateLabel(m.meetingDate)}
                      {m.durationMinutes ? ` · ${m.durationMinutes} min` : ""}
                      {m.participantCount ? ` · ${m.participantCount} participants` : ""}
                    </p>
                  </div>
                  <StatusBadge status={m.status} />
                </Link>
              </li>
            ))}
          </ul>
          {load.page.totalPages > 1 && (
            <nav aria-label="Pagination" className="flex items-center justify-between text-sm">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
              <span className="text-muted">Page {page + 1} of {load.page.totalPages}</span>
              <Button variant="secondary" disabled={page + 1 >= load.page.totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
            </nav>
          )}
        </>
      )}
    </div>
  );
}
