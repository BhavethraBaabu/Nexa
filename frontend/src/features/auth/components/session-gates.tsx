"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "../auth-provider";

function FullPageMessage({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center text-sm text-muted" role="status">
      {children}
    </div>
  );
}

/** Renders children only for signed-in users; otherwise sends them to /login. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "anonymous") router.replace("/login");
  }, [state.status, router]);

  if (state.status !== "authenticated") return <FullPageMessage>Loading…</FullPageMessage>;
  return <>{children}</>;
}

/** For login/register pages: signed-in users go straight to the dashboard. */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "authenticated") router.replace("/");
  }, [state.status, router]);

  if (state.status === "loading") return <FullPageMessage>Loading…</FullPageMessage>;
  if (state.status === "authenticated") return <FullPageMessage>Redirecting…</FullPageMessage>;
  return <>{children}</>;
}
