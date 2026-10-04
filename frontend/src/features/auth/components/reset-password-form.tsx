"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { errorMessage, fieldErrors } from "@/lib/errors";
import { authApi } from "../api";
import { AuthCard } from "./auth-card";

export function ResetPasswordForm() {
  const token = useSearchParams().get("token") ?? "";
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password"));
    if (password !== String(form.get("confirm"))) {
      setFields({ confirm: "Passwords don't match" });
      return;
    }
    setSubmitting(true);
    setError(null);
    setFields({});
    try {
      await authApi.confirmPasswordReset(token, password);
      setDone(true);
    } catch (err) {
      const f = fieldErrors(err);
      setFields(f.newPassword ? { password: f.newPassword } : {});
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard title="Choose a new password">
      {!token ? (
        <Alert>This reset link is incomplete. Request a new one from the sign-in page.</Alert>
      ) : done ? (
        <div className="flex flex-col gap-4">
          <Alert tone="success">Your password has been changed. You&apos;ve been signed out everywhere else.</Alert>
          <Link href="/login" className="text-center text-sm font-medium underline">
            Sign in
          </Link>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          {error && <Alert>{error}</Alert>}
          <Field
            label="New password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            hint="10–64 characters"
            error={fields.password}
          />
          <Field label="Confirm password" name="confirm" type="password" autoComplete="new-password" required error={fields.confirm} />
          <Button type="submit" disabled={submitting}>
            {submitting ? "Saving…" : "Set password"}
          </Button>
        </form>
      )}
    </AuthCard>
  );
}
