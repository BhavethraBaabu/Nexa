"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { errorMessage, fieldErrors } from "@/lib/errors";
import type { InvitationPreview } from "@/types/api";
import { authApi } from "../api";
import { useAuth } from "../auth-provider";
import { AuthCard } from "./auth-card";

type Preview = { state: "loading" } | { state: "ready"; invitation: InvitationPreview } | { state: "invalid"; message: string };

export function AcceptInviteForm() {
  const token = useSearchParams().get("token") ?? "";
  const { startSession } = useAuth();
  const router = useRouter();
  const [preview, setPreview] = useState<Preview>(
    token ? { state: "loading" } : { state: "invalid", message: "This invitation link is incomplete." },
  );
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!token) return;
    authApi.previewInvitation(token).then(
      (invitation) => setPreview({ state: "ready", invitation }),
      (err) => setPreview({ state: "invalid", message: errorMessage(err) }),
    );
  }, [token]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    setFields({});
    try {
      startSession(await authApi.acceptInvitation({ token, name: String(form.get("name")), password: String(form.get("password")) }));
      router.replace("/");
    } catch (err) {
      setFields(fieldErrors(err));
      setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  if (preview.state === "loading") {
    return (
      <AuthCard title="Join your team">
        <p className="text-sm text-muted" role="status">
          Checking invitation…
        </p>
      </AuthCard>
    );
  }
  if (preview.state === "invalid") {
    return (
      <AuthCard title="Invitation unavailable">
        <Alert>{preview.message} Ask your admin to send a new invitation.</Alert>
      </AuthCard>
    );
  }

  const { invitation } = preview;
  return (
    <AuthCard
      title={`Join ${invitation.organizationName}`}
      subtitle={
        <>
          {invitation.invitedByName ?? "A teammate"} invited <span className="font-medium">{invitation.email}</span> as{" "}
          {invitation.role.toLowerCase()}.
        </>
      }
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert>{error}</Alert>}
        <Field label="Your name" name="name" autoComplete="name" required error={fields.name} />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          hint="10–64 characters"
          error={fields.password}
        />
        <Button type="submit" disabled={submitting}>
          {submitting ? "Joining…" : "Accept invitation"}
        </Button>
      </form>
    </AuthCard>
  );
}
