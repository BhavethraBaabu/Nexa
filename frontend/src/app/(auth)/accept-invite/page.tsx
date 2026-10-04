import type { Metadata } from "next";
import { Suspense } from "react";
import { AcceptInviteForm } from "@/features/auth/components/accept-invite-form";

export const metadata: Metadata = { title: "Join your team · Nexa", referrer: "no-referrer" };

export default function AcceptInvitePage() {
  return (
    <Suspense>
      <AcceptInviteForm />
    </Suspense>
  );
}
