"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { useParams, useRouter } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/auth-provider";
import { ApiClientError } from "@/lib/api-client";
import { errorMessage } from "@/lib/errors";
import type { ActionItem, MeetingDetail as Meeting } from "@/types/api";
import { dateLabel, meetingsApi } from "../api";
import { ConfidenceBadge, StatusBadge } from "./status-badge";

type Load = { state: "loading" } | { state: "missing" } | { state: "error"; message: string } | { state: "ready"; meeting: Meeting };

const POLL_MS = 2000;

export function MeetingDetail() {
  const { id } = useParams<{ id: string }>();
  const { authFetch } = useAuth();
  const api = useMemo(() => meetingsApi(authFetch), [authFetch]);
  const router = useRouter();
  const [load, setLoad] = useState<Load>({ state: "loading" });
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoad({ state: "ready", meeting: await api.get(id) });
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 404) setLoad({ state: "missing" });
      else setLoad({ state: "error", message: errorMessage(err) });
    }
  }, [api, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
  }, [refresh]);

  // Analysis runs in the background (PRD section 35); poll until it finishes.
  const processing = load.state === "ready" && load.meeting.status === "PROCESSING";
  useEffect(() => {
    if (!processing) return;
    const timer = setInterval(() => void refresh(), POLL_MS);
    return () => clearInterval(timer);
  }, [processing, refresh]);

  async function analyze() {
    setBusy(true);
    setActionError(null);
    try {
      await api.analyze(id);
      await refresh();
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove(meeting: Meeting) {
    if (!window.confirm(`Delete “${meeting.title}”? Its transcript and AI results will be removed.`)) return;
    setBusy(true);
    try {
      await api.remove(meeting.id);
      router.replace("/meetings");
    } catch (err) {
      setActionError(errorMessage(err));
      setBusy(false);
    }
  }

  if (load.state === "loading") return <p className="text-sm text-muted" role="status">Loading meeting…</p>;
  if (load.state === "missing") {
    return (
      <div className="mx-auto max-w-md py-16 text-center">
        <h1 className="text-xl font-semibold">Meeting not found</h1>
        <Link href="/meetings" className="mt-4 inline-block text-sm underline">Back to meetings</Link>
      </div>
    );
  }
  if (load.state === "error") {
    return (
      <div className="flex items-center gap-3">
        <Alert>{load.message}</Alert>
        <Button variant="secondary" onClick={() => void refresh()}>Retry</Button>
      </div>
    );
  }

  const meeting = load.meeting;
  const analysis = meeting.analysis;
  const run = analysis?.latestRun;

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <Link href="/meetings" className="text-xs text-muted hover:underline">← Meetings</Link>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">{meeting.title}</h1>
          <p className="mt-1 text-sm text-muted">
            {dateLabel(meeting.meetingDate)}
            {meeting.durationMinutes ? ` · ${meeting.durationMinutes} min` : ""}
            {meeting.createdBy ? ` · Added by ${meeting.createdBy.name}` : ""}
          </p>
          {meeting.participants.length > 0 && (
            <p className="mt-1 text-sm text-muted">
              {meeting.participants.map((p) => p.name).join(", ")}
            </p>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <StatusBadge status={meeting.status} />
          {meeting.canEdit && meeting.status !== "PROCESSING" && (
            <Button onClick={() => void analyze()} disabled={busy}>
              {meeting.status === "UPLOADED" ? "Analyze meeting" : "Re-analyze"}
            </Button>
          )}
          {meeting.canEdit && (
            <Button variant="danger" onClick={() => void remove(meeting)} disabled={busy || processing}>Delete</Button>
          )}
        </div>
      </header>

      {actionError && <Alert>{actionError}</Alert>}

      {processing && (
        <div className="rounded-lg border border-border bg-surface p-5" role="status" aria-live="polite">
          <p className="text-sm font-medium">Analyzing this meeting…</p>
          <p className="mt-1 text-sm text-muted">Extracting tasks, owners, deadlines, decisions and risks. This usually takes a few seconds.</p>
        </div>
      )}

      {meeting.status === "FAILED" && run?.status === "FAILED" && (
        <div className="rounded-lg border border-danger/30 bg-danger/5 p-5" role="alert">
          <p className="text-sm font-medium text-danger">Nexa couldn&apos;t analyze this meeting.</p>
          <p className="mt-1 text-sm">{run.errorMessage}</p>
          <p className="mt-1 text-sm text-muted">Your transcript is safely stored{analysis?.summary ? ", and the previous results are shown below" : ""}.</p>
          {meeting.canEdit && run.retryable && (
            <Button className="mt-3" variant="secondary" onClick={() => void analyze()} disabled={busy}>Retry</Button>
          )}
        </div>
      )}

      {meeting.status === "UPLOADED" && !processing && (
        <div className="rounded-lg border border-dashed border-border bg-surface p-5">
          <p className="text-sm font-medium">Not analyzed yet</p>
          <p className="mt-1 text-sm text-muted">
            {meeting.canEdit
              ? "Run the analysis to pull out action items, owners, deadlines, decisions and risks."
              : "The meeting's creator or a manager can run the analysis."}
          </p>
        </div>
      )}

      {analysis?.summary && (
        <>
          <Section title="Summary">
            <p className="text-sm leading-relaxed">{analysis.summary}</p>
            {analysis.keyPoints.length > 0 && (
              <ul className="mt-3 list-disc space-y-1 pl-5 text-sm">
                {analysis.keyPoints.map((p) => <li key={p}>{p}</li>)}
              </ul>
            )}
          </Section>

          <Section title="Action items" count={analysis.actionItems.length} empty="No action items were found.">
            <ul className="divide-y divide-border">
              {analysis.actionItems.map((item) => <ActionRow key={item.id} item={item} />)}
            </ul>
          </Section>

          <div className="grid gap-6 lg:grid-cols-2">
            <Section title="Decisions" count={analysis.decisions.length} empty="No decisions were made.">
              <ul className="divide-y divide-border">
                {analysis.decisions.map((d) => (
                  <Row key={d.id} evidence={d.evidence} badge={<ConfidenceBadge level={d.confidenceLevel} confidence={d.confidence} />}>
                    <p className="text-sm font-medium">✓ {d.decision}</p>
                    {d.context && <p className="text-xs text-muted">{d.context}</p>}
                  </Row>
                ))}
              </ul>
            </Section>
            <Section title="Risks" count={analysis.risks.length} empty="No risks were raised.">
              <ul className="divide-y divide-border">
                {analysis.risks.map((r) => (
                  <Row key={r.id} evidence={r.evidence} badge={<ConfidenceBadge level={r.confidenceLevel} confidence={r.confidence} />}>
                    <p className="text-sm">
                      <span className={`mr-2 rounded px-1.5 py-0.5 text-xs font-medium ${r.severity === "HIGH" ? "bg-danger/10 text-danger" : "bg-surface-strong"}`}>
                        {r.severity}
                      </span>
                      {r.description}
                    </p>
                  </Row>
                ))}
              </ul>
            </Section>
          </div>

          <Section title="Open questions" count={analysis.questions.length} empty="No unresolved questions.">
            <ul className="divide-y divide-border">
              {analysis.questions.map((q) => (
                <Row key={q.id} evidence={q.evidence} badge={<ConfidenceBadge level={q.confidenceLevel} confidence={q.confidence} />}>
                  <p className="text-sm">? {q.question}</p>
                </Row>
              ))}
            </ul>
          </Section>

          {run?.status === "COMPLETED" && (
            <p className="text-xs text-muted">
              Analyzed by {run.model} · prompt {run.promptVersion}
              {run.durationMs ? ` · ${(run.durationMs / 1000).toFixed(1)}s` : ""}. AI suggestions: review before acting on them.
            </p>
          )}
        </>
      )}

      <details className="rounded-lg border border-border bg-surface">
        <summary className="cursor-pointer px-5 py-3 text-sm font-medium">Transcript</summary>
        <pre className="max-h-[32rem] overflow-auto whitespace-pre-wrap border-t border-border px-5 py-4 font-mono text-xs leading-relaxed">
          {meeting.transcript}
        </pre>
      </details>
    </div>
  );
}

function Section({ title, count, empty, children }: { title: string; count?: number; empty?: string; children: ReactNode }) {
  return (
    <section className="overflow-hidden rounded-lg border border-border bg-surface">
      <h2 className="border-b border-border px-5 py-3 text-sm font-medium">
        {title} {count !== undefined && <span className="text-muted">({count})</span>}
      </h2>
      {count === 0 ? <p className="px-5 py-4 text-sm text-muted">{empty}</p> : <div className={count === undefined ? "px-5 py-4" : ""}>{children}</div>}
    </section>
  );
}

function Row({ children, badge, evidence }: { children: ReactNode; badge: ReactNode; evidence: string | null }) {
  return (
    <li className="px-5 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">{children}</div>
        <div className="shrink-0">{badge}</div>
      </div>
      {evidence && <p className="mt-1 text-xs italic text-muted">“{evidence}”</p>}
    </li>
  );
}

function ActionRow({ item }: { item: ActionItem }) {
  return (
    <Row evidence={item.evidence} badge={<ConfidenceBadge level={item.confidenceLevel} confidence={item.confidence} />}>
      <p className="text-sm font-medium">{item.title}</p>
      {item.description && <p className="text-xs text-muted">{item.description}</p>}
      <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-xs">
        <span>
          <span className="text-muted">Owner: </span>
          {item.ownerStatus === "RESOLVED" && item.owner ? (
            item.owner.name
          ) : item.ownerStatus === "UNRESOLVED" ? (
            <span className="text-danger" title="This name doesn't match exactly one team member">{item.ownerName} (not matched to a member)</span>
          ) : (
            <span className="text-danger">Unassigned</span>
          )}
        </span>
        <span>
          <span className="text-muted">Due: </span>
          {item.deadlineStatus === "NONE" ? (
            "—"
          ) : item.deadlineStatus === "RESOLVED" && item.deadline ? (
            dateLabel(item.deadline)
          ) : (
            <span className="text-danger" title="The deadline was vague; confirm it">
              {item.deadline ? `${dateLabel(item.deadline)}? ` : ""}Needs review (“{item.deadlineText}”)
            </span>
          )}
        </span>
        <span>
          <span className="text-muted">Priority: </span>
          {item.priority.charAt(0) + item.priority.slice(1).toLowerCase()}
        </span>
      </div>
    </Row>
  );
}
