import { BackendStatus } from "@/features/dashboard/components/backend-status";
import { Greeting } from "@/features/dashboard/components/greeting";

// PRD section 44. Values are populated once meetings and tasks exist (Phases 2–5).
const WIDGETS = [
  "Meetings This Week",
  "Action Items",
  "Completed Tasks",
  "Overdue Tasks",
  "Pending AI Actions",
  "Recent Decisions",
];

export default function DashboardPage() {
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <header>
        <Greeting />
        <p className="mt-1 text-sm text-muted">Your team&apos;s meetings, decisions and work in one place.</p>
      </header>

      <BackendStatus />

      <section aria-label="Overview" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {WIDGETS.map((label) => (
          <div key={label} className="rounded-lg border border-border bg-surface p-5">
            <p className="text-sm text-muted">{label}</p>
            <p className="mt-2 text-2xl font-semibold tabular-nums">—</p>
            <p className="mt-1 text-xs text-muted">No data yet</p>
          </div>
        ))}
      </section>
    </div>
  );
}
