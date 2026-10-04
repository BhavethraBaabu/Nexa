"use client";

import { useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { useAuth, useCurrentUser } from "@/features/auth/auth-provider";
import { teamApi } from "@/features/team/api";
import { errorMessage } from "@/lib/errors";
import type { Me } from "@/types/api";

export function SettingsPage() {
  const me = useCurrentUser();
  const { authFetch, setUser } = useAuth();

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
      </header>

      <SettingsForm
        title="Profile"
        description={`Signed in as ${me.email}.`}
        label="Name"
        initial={me.name}
        onSave={async (name) => {
          setUser(await authFetch<Me>("/api/v1/users/me", { method: "PATCH", body: JSON.stringify({ name }) }));
        }}
      />

      {me.role === "ADMIN" ? (
        <SettingsForm
          title="Organization"
          description="Visible to everyone in your organization."
          label="Organization name"
          initial={me.organization.name}
          onSave={async (name) => {
            const org = await teamApi(authFetch).renameOrganization(name);
            setUser({ ...me, organization: { id: org.id, name: org.name } });
          }}
        />
      ) : (
        <section className="rounded-lg border border-border bg-surface p-5">
          <h2 className="text-sm font-medium">Organization</h2>
          <p className="mt-1 text-sm text-muted">{me.organization.name}. Only admins can change organization settings.</p>
        </section>
      )}
    </div>
  );
}

function SettingsForm({ title, description, label, initial, onSave }: {
  title: string;
  description: string;
  label: string;
  initial: string;
  onSave: (value: string) => Promise<void>;
}) {
  const [status, setStatus] = useState<{ kind: "idle" | "saving" | "saved" } | { kind: "error"; message: string }>({ kind: "idle" });

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = String(new FormData(event.currentTarget).get("value")).trim();
    setStatus({ kind: "saving" });
    try {
      await onSave(value);
      setStatus({ kind: "saved" });
    } catch (err) {
      setStatus({ kind: "error", message: errorMessage(err) });
    }
  }

  return (
    <section className="rounded-lg border border-border bg-surface p-5">
      <h2 className="text-sm font-medium">{title}</h2>
      <p className="mt-1 text-sm text-muted">{description}</p>
      <form onSubmit={onSubmit} className="mt-4 flex flex-col gap-3">
        {status.kind === "error" && <Alert>{status.message}</Alert>}
        {status.kind === "saved" && <Alert tone="success">Saved.</Alert>}
        <Field label={label} name="value" defaultValue={initial} required maxLength={120} />
        <div>
          <Button type="submit" disabled={status.kind === "saving"}>
            {status.kind === "saving" ? "Saving…" : "Save"}
          </Button>
        </div>
      </form>
    </section>
  );
}
