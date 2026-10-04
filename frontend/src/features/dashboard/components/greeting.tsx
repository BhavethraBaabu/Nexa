"use client";

import { useSyncExternalStore } from "react";
import { useCurrentUser } from "@/features/auth/auth-provider";

function partOfDay(hour: number) {
  if (hour < 12) return "morning";
  if (hour < 18) return "afternoon";
  return "evening";
}

const noopSubscribe = () => () => {};

/** "Good evening, Bhavethra" (PRD section 24), using the viewer's local time. */
export function Greeting() {
  const user = useCurrentUser();
  // The hour differs between server and browser, so it is read on the client only.
  const hour = useSyncExternalStore(noopSubscribe, () => new Date().getHours(), () => null);
  const firstName = user.name.split(" ")[0];
  return (
    <h1 className="text-2xl font-semibold tracking-tight">
      {hour === null ? "Welcome" : `Good ${partOfDay(hour)}`}, {firstName}
    </h1>
  );
}
