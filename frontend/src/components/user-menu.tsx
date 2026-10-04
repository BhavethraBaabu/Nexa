"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth, useCurrentUser } from "@/features/auth/auth-provider";

export function UserMenu() {
  const user = useCurrentUser();
  const { logout } = useAuth();
  const router = useRouter();
  const [signingOut, setSigningOut] = useState(false);

  async function onSignOut() {
    setSigningOut(true);
    await logout().catch(() => undefined);
    router.replace("/login");
  }

  return (
    <div className="flex items-center justify-between gap-2 border-t border-border px-6 py-4">
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">{user.name}</p>
        <p className="truncate text-xs text-muted">
          {user.organization.name} · {user.role.charAt(0) + user.role.slice(1).toLowerCase()}
        </p>
      </div>
      <button
        type="button"
        onClick={onSignOut}
        disabled={signingOut}
        className="shrink-0 rounded-md px-2 py-1 text-xs text-muted hover:bg-surface-strong hover:text-foreground disabled:opacity-50"
      >
        Sign out
      </button>
    </div>
  );
}
