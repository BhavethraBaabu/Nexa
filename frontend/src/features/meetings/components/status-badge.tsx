import type { ConfidenceLevel, MeetingStatus } from "@/types/api";

const STATUS: Record<MeetingStatus, { label: string; className: string }> = {
  UPLOADED: { label: "Not analyzed", className: "bg-surface-strong text-muted" },
  PROCESSING: { label: "Analyzing…", className: "bg-surface-strong text-foreground" },
  COMPLETED: { label: "Analyzed", className: "bg-success/10 text-success" },
  FAILED: { label: "Analysis failed", className: "bg-danger/10 text-danger" },
};

export function StatusBadge({ status }: { status: MeetingStatus }) {
  const s = STATUS[status];
  return <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${s.className}`}>{s.label}</span>;
}

/** PRD section 12: low-confidence results must be clearly marked. */
export function ConfidenceBadge({ level, confidence }: { level: ConfidenceLevel; confidence: number }) {
  const className =
    level === "HIGH" ? "text-muted" : level === "MEDIUM" ? "text-foreground" : "bg-danger/10 text-danger font-medium";
  return (
    <span className={`rounded px-1.5 py-0.5 text-xs tabular-nums ${className}`} title={`${level} confidence`}>
      {Math.round(confidence * 100)}%{level === "LOW" && " · low confidence"}
    </span>
  );
}
