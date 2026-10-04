import type { ReactNode } from "react";
import { SidebarNav } from "./sidebar-nav";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <aside className="border-b border-border bg-surface md:w-60 md:shrink-0 md:border-b-0 md:border-r">
        <div className="px-6 py-5">
          <p className="text-lg font-semibold tracking-tight">Nexa</p>
          <p className="text-xs text-muted">Where meetings become momentum.</p>
        </div>
        <div className="px-3 pb-4">
          <SidebarNav />
        </div>
      </aside>
      <main className="flex-1 px-4 py-6 md:px-10 md:py-8">{children}</main>
    </div>
  );
}
