"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/auth-provider";
import { dateLabel } from "@/features/meetings/api";
import { errorMessage } from "@/lib/errors";
import { ACTION_LABEL, titleCase, workApi } from "@/lib/work-api";
import type { AiAction, ActionStatus } from "@/types/api";

const STATUS_STYLE: Record<ActionStatus, string> = {
  PENDING: "bg-surface-strong text-foreground",
  APPROVED: "bg-surface-strong text-foreground",
  EXECUTING: "bg-surface-strong text-foreground",
  COMPLETED: "bg-success/10 text-success",
  FAILED: "bg-danger/10 text-danger",
  REJECTED: "bg-surface-strong text-muted",
  CANCELLED: "bg-surface-strong text-muted",
};

function statusLabel(action: AiAction) {
  if (action.status === "APPROVED" && action.attempts > 0) return "Retrying…";
  if (action.status === "APPROVED" || action.status === "EXECUTING") return "Running…";
  return titleCase(action.status);
}

/**
 * Review list for AI actions (PRD section 15): select, then Approve Selected or Reject. Nothing
 * reaches Jira or Slack until approved. Shows results, external links and failures with Retry.
 */
export function ActionList({ actions, onChanged, showMeeting = true }: { actions: AiAction[]; onChanged: () => void; showMeeting?: boolean }) {
  const { authFetch } = useAuth();
  const api = useMemo(() => workApi(authFetch), [authFetch]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);

  const selectable = actions.filter((a) => a.status === "PENDING" && a.canApprove && !a.blockedReason);
  const pendingVisible = actions.some((a) => a.status === "PENDING");

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setSelected(new Set());
      onChanged();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const chosen = [...selected].filter((id) => selectable.some((a) => a.id === id));

  return (
    <div className="flex flex-col">
      {error && <div className="px-5 pt-4"><Alert>{error}</Alert></div>}
      {pendingVisible && selectable.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 border-b border-border px-5 py-3">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={chosen.length === selectable.length && selectable.length > 0}
              onChange={(e) => setSelected(e.target.checked ? new Set(selectable.map((a) => a.id)) : new Set())}
            />
            Select all
          </label>
          <span className="flex-1" />
          <Button disabled={busy || chosen.length === 0} onClick={() => run(() => api.approve(chosen))}>
            Approve selected{chosen.length ? ` (${chosen.length})` : ""}
          </Button>
          <Button variant="secondary" disabled={busy || chosen.length === 0} onClick={() => run(() => api.reject(chosen))}>
            Reject
          </Button>
        </div>
      )}
      <ul className="divide-y divide-border">
        {actions.map((a) => {
          const canSelect = selectable.some((s) => s.id === a.id);
          return (
            <li key={a.id} className="flex gap-3 px-5 py-3">
              <div className="pt-0.5">
                {a.status === "PENDING" ? (
                  <input
                    type="checkbox"
                    aria-label={`Select: ${ACTION_LABEL[a.type]}${a.task ? ` for ${a.task.title}` : ""}`}
                    disabled={!canSelect}
                    checked={selected.has(a.id)}
                    onChange={() => toggle(a.id)}
                  />
                ) : (
                  <span className="inline-block w-[13px]" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="text-sm font-medium">{ACTION_LABEL[a.type]}</p>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[a.status]}`}>{statusLabel(a)}</span>
                </div>
                <p className="mt-0.5 text-xs text-muted">
                  {a.task ? (
                    <>
                      {a.task.title} · {a.task.ownerName ?? "Unassigned"}
                      {a.task.deadline ? ` · due ${dateLabel(a.task.deadline)}` : ""}
                    </>
                  ) : (
                    "Summary of all action items and decisions"
                  )}
                  {showMeeting && a.meeting && (
                    <> · <Link href={`/meetings/${a.meeting.id}`} className="underline">{a.meeting.title}</Link></>
                  )}
                </p>
                {a.status === "PENDING" && a.blockedReason && (
                  <p className="mt-1 text-xs text-danger">
                    {a.blockedReason} <Link href="/integrations" className="underline">Open Integrations</Link>
                  </p>
                )}
                {a.status === "PENDING" && !a.canApprove && !a.blockedReason && (
                  <p className="mt-1 text-xs text-muted">Only the meeting&apos;s creator or a manager can approve this.</p>
                )}
                {(a.status === "FAILED" || (a.status === "APPROVED" && a.errorMessage)) && (
                  <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
                    <span className="text-danger">{a.errorMessage}</span>
                    {a.status === "FAILED" && a.retryable && a.canApprove && (
                      <Button variant="secondary" className="px-2 py-0.5 text-xs" disabled={busy} onClick={() => run(() => api.retry(a.id))}>
                        Retry
                      </Button>
                    )}
                  </div>
                )}
                {a.status === "FAILED" && <p className="text-xs text-muted">Your approved action has been preserved.</p>}
                {a.external && (
                  <p className="mt-1 text-xs">
                    {a.external.url ? (
                      <a href={a.external.url} target="_blank" rel="noopener noreferrer" className="font-medium underline">
                        {a.external.provider === "JIRA" ? a.external.id : "View in Slack"} ↗
                      </a>
                    ) : (
                      <span>{a.external.id}</span>
                    )}
                  </p>
                )}
                {a.result && (
                  <div className="mt-2">
                    <button type="button" className="text-xs font-medium underline" onClick={() => setExpanded(expanded === a.id ? null : a.id)}>
                      {expanded === a.id ? "Hide draft" : "Show draft"}
                    </button>
                    {expanded === a.id && (
                      <div className="mt-2 rounded-md border border-border bg-background p-3">
                        <pre className="whitespace-pre-wrap font-mono text-xs leading-relaxed">{a.result}</pre>
                        <Button variant="secondary" className="mt-2 px-2 py-1 text-xs" onClick={() => void navigator.clipboard.writeText(a.result ?? "")}>
                          Copy to clipboard
                        </Button>
                      </div>
                    )}
                  </div>
                )}
                {a.approvedBy && a.status !== "PENDING" && (
                  <p className="mt-1 text-xs text-muted">
                    {a.status === "REJECTED" ? "Rejected" : "Approved"} by {a.approvedBy.name}
                    {a.approvedAt ? ` · ${new Date(a.approvedAt).toLocaleString()}` : ""}
                  </p>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
