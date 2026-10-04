"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { inputClass } from "@/components/ui/field";
import { useAuth, useCurrentUser } from "@/features/auth/auth-provider";
import { errorMessage } from "@/lib/errors";
import type { Invitation, Member, Role } from "@/types/api";
import { ROLES, roleLabel, teamApi } from "../api";

type Load = { state: "loading" } | { state: "error"; message: string } | { state: "ready"; members: Member[]; invitations: Invitation[] };

const dateFormat = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" });

export function TeamPage() {
  const me = useCurrentUser();
  const { authFetch } = useAuth();
  const api = useMemo(() => teamApi(authFetch), [authFetch]);
  const isAdmin = me.role === "ADMIN";

  const [load, setLoad] = useState<Load>({ state: "loading" });
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [members, invitations] = await Promise.all([api.members(), isAdmin ? api.invitations() : Promise.resolve([])]);
      setLoad({ state: "ready", members, invitations });
    } catch (err) {
      setLoad({ state: "error", message: errorMessage(err) });
    }
  }, [api, isAdmin]);

  useEffect(() => {
    // Loading data on mount is the intended side effect here.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
  }, [refresh]);

  async function run(id: string, action: () => Promise<unknown>, success?: string) {
    setBusyId(id);
    setActionError(null);
    setNotice(null);
    try {
      await action();
      if (success) setNotice(success);
      await refresh();
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Team</h1>
        <p className="mt-1 text-sm text-muted">
          {isAdmin ? "Invite people and manage what they can do." : "People in your organization."}
        </p>
      </header>

      {actionError && <Alert>{actionError}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      {isAdmin && (
        <InviteForm
          onInvite={(email, role) => run("invite", () => api.invite(email, role), `Invitation sent to ${email}.`)}
          busy={busyId === "invite"}
        />
      )}

      {load.state === "loading" && (
        <p className="text-sm text-muted" role="status">
          Loading team…
        </p>
      )}
      {load.state === "error" && (
        <div className="flex items-center gap-3">
          <Alert>{load.message}</Alert>
          <Button variant="secondary" onClick={() => void refresh()}>
            Retry
          </Button>
        </div>
      )}

      {load.state === "ready" && (
        <>
          <section aria-labelledby="members-heading" className="overflow-hidden rounded-lg border border-border bg-surface">
            <h2 id="members-heading" className="border-b border-border px-5 py-3 text-sm font-medium">
              Members <span className="text-muted">({load.members.length})</span>
            </h2>
            <ul className="divide-y divide-border">
              {load.members.map((member) => {
                const isSelf = member.id === me.id;
                return (
                  <li key={member.id} className="flex flex-col gap-3 px-5 py-3 sm:flex-row sm:items-center">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">
                        {member.name} {isSelf && <span className="font-normal text-muted">(you)</span>}
                      </p>
                      <p className="truncate text-xs text-muted">
                        {member.email} · Joined {dateFormat.format(new Date(member.joinedAt))}
                      </p>
                    </div>
                    {isAdmin && !isSelf ? (
                      <div className="flex items-center gap-2">
                        <label className="sr-only" htmlFor={`role-${member.id}`}>
                          Role for {member.name}
                        </label>
                        <select
                          id={`role-${member.id}`}
                          value={member.role}
                          disabled={busyId === member.id}
                          onChange={(e) =>
                            run(member.id, () => api.changeRole(member.id, e.target.value as Role), `${member.name} is now ${roleLabel(e.target.value as Role).toLowerCase()}.`)
                          }
                          className={`${inputClass} w-auto py-1.5`}
                        >
                          {ROLES.map((role) => (
                            <option key={role} value={role}>
                              {roleLabel(role)}
                            </option>
                          ))}
                        </select>
                        <Button
                          variant="danger"
                          disabled={busyId === member.id}
                          onClick={() => {
                            if (window.confirm(`Remove ${member.name} from the organization? They will be signed out immediately.`)) {
                              void run(member.id, () => api.removeMember(member.id), `${member.name} was removed.`);
                            }
                          }}
                        >
                          Remove
                        </Button>
                      </div>
                    ) : (
                      <span className="rounded-full bg-surface-strong px-2.5 py-0.5 text-xs font-medium">{roleLabel(member.role)}</span>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>

          {isAdmin && (
            <section aria-labelledby="invites-heading" className="overflow-hidden rounded-lg border border-border bg-surface">
              <h2 id="invites-heading" className="border-b border-border px-5 py-3 text-sm font-medium">
                Pending invitations <span className="text-muted">({load.invitations.length})</span>
              </h2>
              {load.invitations.length === 0 ? (
                <p className="px-5 py-4 text-sm text-muted">No pending invitations.</p>
              ) : (
                <ul className="divide-y divide-border">
                  {load.invitations.map((invitation) => (
                    <li key={invitation.id} className="flex items-center gap-3 px-5 py-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm">{invitation.email}</p>
                        <p className="text-xs text-muted">
                          {roleLabel(invitation.role)} · Expires {dateFormat.format(new Date(invitation.expiresAt))}
                        </p>
                      </div>
                      <Button
                        variant="secondary"
                        disabled={busyId === invitation.id}
                        onClick={() => run(invitation.id, () => api.revokeInvitation(invitation.id), "Invitation revoked.")}
                      >
                        Revoke
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
        </>
      )}
    </div>
  );
}

function InviteForm({ onInvite, busy }: { onInvite: (email: string, role: Role) => Promise<void>; busy: boolean }) {
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await onInvite(String(form.get("email")).trim(), form.get("role") as Role);
    formElement.reset();
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-5 sm:flex-row sm:items-end">
      <div className="flex flex-1 flex-col gap-1.5">
        <label htmlFor="invite-email" className="text-sm font-medium">
          Invite by email
        </label>
        <input id="invite-email" name="email" type="email" required placeholder="name@company.com" className={inputClass} />
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="invite-role" className="text-sm font-medium">
          Role
        </label>
        <select id="invite-role" name="role" defaultValue="MEMBER" className={`${inputClass} sm:w-36`}>
          {ROLES.map((role) => (
            <option key={role} value={role}>
              {roleLabel(role)}
            </option>
          ))}
        </select>
      </div>
      <Button type="submit" disabled={busy}>
        {busy ? "Sending…" : "Send invite"}
      </Button>
    </form>
  );
}
