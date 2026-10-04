import type { Metadata } from "next";
import { Suspense } from "react";
import { ResetPasswordForm } from "@/features/auth/components/reset-password-form";

export const metadata: Metadata = { title: "Choose a new password · Nexa", referrer: "no-referrer" };

export default function ResetPasswordPage() {
  return (
    <Suspense>
      <ResetPasswordForm />
    </Suspense>
  );
}
