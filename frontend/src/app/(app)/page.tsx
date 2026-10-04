import { BackendStatus } from "@/features/dashboard/components/backend-status";
import { DashboardOverview } from "@/features/dashboard/components/dashboard-overview";
import { Greeting } from "@/features/dashboard/components/greeting";

export default function DashboardPage() {
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <header>
        <Greeting />
        <p className="mt-1 text-sm text-muted">Your team&apos;s meetings, decisions and work in one place.</p>
      </header>
      <DashboardOverview />
      <BackendStatus />
    </div>
  );
}
