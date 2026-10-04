"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

interface NavItem {
  label: string;
  href: string;
  /** Pages land in later phases; shown so the product shape is visible. */
  available: boolean;
}

// PRD section 43.
const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", href: "/", available: true },
  { label: "Meetings", href: "/meetings", available: false },
  { label: "Tasks", href: "/tasks", available: false },
  { label: "Actions", href: "/actions", available: false },
  { label: "Search", href: "/search", available: false },
  { label: "Integrations", href: "/integrations", available: false },
  { label: "Team", href: "/team", available: false },
  { label: "Settings", href: "/settings", available: false },
];

export function SidebarNav() {
  const pathname = usePathname();

  return (
    <nav aria-label="Main" className="flex flex-col gap-0.5">
      {NAV_ITEMS.map((item) => {
        if (!item.available) {
          return (
            <span
              key={item.href}
              aria-disabled="true"
              className="flex items-center justify-between rounded-md px-3 py-2 text-sm text-muted"
            >
              {item.label}
              <span className="text-[10px] uppercase tracking-wide">Soon</span>
            </span>
          );
        }
        const active = pathname === item.href;
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${
              active ? "bg-surface-strong text-foreground" : "text-foreground/80 hover:bg-surface-strong"
            }`}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
