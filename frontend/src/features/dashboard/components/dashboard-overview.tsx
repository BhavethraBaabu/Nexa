"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { useAuth } from "@/features/auth/auth-provider";
import { dateLabel, meetingsApi } from "@/features/meetings/api";
import { StatusBadge } from "@/features/meetings/components/status-badge";
import { errorMessage } from "@/lib/errors";
import type { Dashboard } from "@/types/api";

type Load = { state: "loading" } | { state: "error"; message: string } | { state: "ready"; data: Dashboard };

/** PRD sections 24 and 44. Completed, overdue and pending-approval counts arrive with Phases 4-5. */
export function DashboardOverview() {
  const { authFetch } = useAuth();
  const api = useMemo(() => meetingsApi(authFetch), [authFetch]);
  const [load, setLoad] = useState<Load>({ state: "loading" });

  useEffect(() => {
    api.dashboard().then(
      (data) => setLoad({ state: "ready", data }),
      (err) => setLoad({ state: "error", message: errorMessage(err) }),
    );
  }, [api]);

  if (load.state === "error") return <Alert>{load.message}</Alert>;
  const data = load.state === "ready" ? load.data : null;

  const stats: { label: string; value: number | null; note?: string }[] = [
    { label: "Meetings this week", value: data?.meetingsThisWeek ?? null },
    { label: "Action items", value: data?.actionItems ?? null },
    { label: "Total meetings", value: data?.totalMeetings ?? null },
  ];

  return (
    <>
      <section aria-label="Overview" className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {stats.map((s) => (
          <div key={s.label} className="rounded-lg border border-border bg-surface p-5">
            <p className="text-sm text-muted">{s.label}</p>
            <p className="mt-2 text-2xl font-semibold tabular-nums">{s.value === null ? "…" : s.value.toLocaleString()}</p>
          </div>
        ))}
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="overflow-hidden rounded-lg border border-border bg-surface">
          <div className="flex items-center justify-between border-b border-border px-5 py-3">
            <h2 className="text-sm font-medium">Recent meetings</h2>
            <Link href="/meetings/new" className="text-xs font-medium underline">New meeting</Link>
          </div>
          {data && data.recentMeetings.length === 0 ? (
            <p className="px-5 py-4 text-sm text-muted">
              No meetings yet. <Link href="/meetings/new" className="underline">Add a transcript</Link> to get started.
            </p>
          ) : (
            <ul className="divide-y divide-border">
              {data?.recentMeetings.map((m) => (
                <li key={m.id}>
                  <Link href={`/meetings/${m.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-surface-strong">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{m.title}</p>
                      <p className="text-xs text-muted">{dateLabel(m.meetingDate)}</p>
                    </div>
                    <StatusBadge status={m.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="overflow-hidden rounded-lg border border-border bg-surface">
          <h2 className="border-b border-border px-5 py-3 text-sm font-medium">Recent decisions</h2>
          {data && data.recentDecisions.length === 0 ? (
            <p className="px-5 py-4 text-sm text-muted">Decisions from analyzed meetings will appear here.</p>
          ) : (
            <ul className="divide-y divide-border">
              {data?.recentDecisions.map((d) => (
                <li key={d.id}>
                  <Link href={`/meetings/${d.meetingId}`} className="block px-5 py-3 text-sm hover:bg-surface-strong">✓ {d.decision}</Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
