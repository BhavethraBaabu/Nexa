"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, inputClass } from "@/components/ui/field";
import { useAuth } from "@/features/auth/auth-provider";
import { teamApi } from "@/features/team/api";
import { errorMessage } from "@/lib/errors";
import { workApi, type TaskUpdate } from "@/lib/work-api";
import type { Level, Member, TaskStatus } from "@/types/api";

export interface EditableTask {
  id: string;
  title: string;
  description: string | null;
  owner: { id: string; name: string } | null;
  ownerName: string | null;
  priority: Level;
  status: TaskStatus;
  deadline: string | null;
  deadlineText: string | null;
}

const STATUSES: TaskStatus[] = ["SUGGESTED", "OPEN", "IN_PROGRESS", "DONE", "CANCELLED"];
const STATUS_LABEL: Record<TaskStatus, string> = {
  SUGGESTED: "Suggested (not yet accepted)",
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  DONE: "Done",
  CANCELLED: "Dismissed",
};

/** Edit a task before approving its actions (PRD section 15): title, description, owner, priority, deadline. */
export function TaskEditor({ task, onClose, onSaved }: { task: EditableTask | null; onClose: () => void; onSaved: () => void }) {
  const { authFetch } = useAuth();
  const api = useMemo(() => workApi(authFetch), [authFetch]);
  const [members, setMembers] = useState<Member[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!task) return;
    teamApi(authFetch).members().then(setMembers, () => setMembers([]));
  }, [task, authFetch]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!task) return;
    const form = new FormData(event.currentTarget);
    const ownerId = String(form.get("ownerId") ?? "");
    const deadline = String(form.get("deadline") ?? "");
    const update: TaskUpdate = {
      title: String(form.get("title")).trim(),
      description: String(form.get("description") ?? ""),
      priority: form.get("priority") as Level,
      status: form.get("status") as TaskStatus,
    };
    if (ownerId) update.ownerId = ownerId;
    else update.clearOwner = true;
    if (deadline) update.deadline = deadline;
    else update.clearDeadline = true;

    setSaving(true);
    setError(null);
    try {
      await api.updateTask(task.id, update);
      onSaved();
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={task !== null} title="Edit task" onClose={onClose}>
      {task && (
        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          {error && <Alert>{error}</Alert>}
          <Field label="Title" name="title" defaultValue={task.title} required maxLength={300} />
          <div className="flex flex-col gap-1.5">
            <label htmlFor="task-description" className="text-sm font-medium">Description</label>
            <textarea id="task-description" name="description" rows={3} defaultValue={task.description ?? ""} maxLength={4000} className={inputClass} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="task-owner" className="text-sm font-medium">Owner</label>
              <select id="task-owner" name="ownerId" defaultValue={task.owner?.id ?? ""} className={inputClass}>
                <option value="">Unassigned</option>
                {members.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
              </select>
              {!task.owner && task.ownerName && (
                <p className="text-xs text-muted">The transcript named “{task.ownerName}”, who isn&apos;t a member.</p>
              )}
            </div>
            <Field
              label="Deadline"
              name="deadline"
              type="date"
              defaultValue={task.deadline ?? ""}
              hint={task.deadlineText ? `Said in the meeting: “${task.deadlineText}”` : undefined}
            />
            <div className="flex flex-col gap-1.5">
              <label htmlFor="task-priority" className="text-sm font-medium">Priority</label>
              <select id="task-priority" name="priority" defaultValue={task.priority} className={inputClass}>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="task-status" className="text-sm font-medium">Status</label>
              <select id="task-status" name="status" defaultValue={task.status} className={inputClass}>
                {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
              </select>
            </div>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Saving…" : "Save changes"}</Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
