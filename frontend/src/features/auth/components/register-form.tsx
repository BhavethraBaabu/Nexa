"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { errorMessage, fieldErrors } from "@/lib/errors";
import { authApi } from "../api";
import { useAuth } from "../auth-provider";
import { AuthCard } from "./auth-card";

export function RegisterForm() {
  const { startSession } = useAuth();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    setFields({});
    try {
      startSession(
        await authApi.register({
          name: String(form.get("name")),
          email: String(form.get("email")),
          password: String(form.get("password")),
          organizationName: String(form.get("organizationName")),
        }),
      );
      router.replace("/");
    } catch (err) {
      setFields(fieldErrors(err));
      setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="Create your organization"
      subtitle="You'll be the admin and can invite your team next."
      footer={
        <>
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-foreground underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}
        <Field label="Your name" name="name" autoComplete="name" required error={fields.name} />
        <Field label="Work email" name="email" type="email" autoComplete="email" required error={fields.email} />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          minLength={10}
          maxLength={64}
          hint="10–64 characters"
          error={fields.password}
        />
        <Field label="Organization name" name="organizationName" required error={fields.organizationName} />
        <Button type="submit" disabled={submitting}>
          {submitting ? "Creating…" : "Create organization"}
        </Button>
      </form>
    </AuthCard>
  );
}
