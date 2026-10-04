"use client";

import { useMemo, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, inputClass } from "@/components/ui/field";
import { useAuth } from "@/features/auth/auth-provider";
import { errorMessage, fieldErrors } from "@/lib/errors";
import { meetingsApi } from "../api";

const MAX_CHARS = 300_000;

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function NewMeetingForm() {
  const { authFetch } = useAuth();
  const api = useMemo(() => meetingsApi(authFetch), [authFetch]);
  const router = useRouter();
  const fileInput = useRef<HTMLInputElement>(null);

  const [transcript, setTranscript] = useState("");
  const [uploading, setUploading] = useState(false);
  const [uploadNote, setUploadNote] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  async function onFile(file: File | undefined) {
    if (!file) return;
    setUploading(true);
    setError(null);
    setUploadNote(null);
    try {
      const result = await api.extractTranscript(file);
      setTranscript(result.text);
      setUploadNote(`Loaded ${result.characters.toLocaleString()} characters from ${result.fileName}. Review before saving.`);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setUploading(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  }

  async function submit(formElement: HTMLFormElement, analyze: boolean) {
    const form = new FormData(formElement);
    const duration = String(form.get("durationMinutes") ?? "").trim();
    setSubmitting(true);
    setError(null);
    setFields({});
    try {
      const meeting = await api.create({
        title: String(form.get("title")).trim(),
        meetingDate: String(form.get("meetingDate")),
        durationMinutes: duration ? Number(duration) : null,
        participants: String(form.get("participants") ?? "")
          .split(",")
          .map((p) => p.trim())
          .filter(Boolean),
        transcript,
      });
      if (analyze) await api.analyze(meeting.id);
      router.push(`/meetings/${meeting.id}`);
    } catch (err) {
      setFields(fieldErrors(err));
      setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">New meeting</h1>
        <p className="mt-1 text-sm text-muted">Paste a transcript or upload a .txt, .pdf or .docx file.</p>
      </header>

      <form
        onSubmit={(e: FormEvent<HTMLFormElement>) => {
          e.preventDefault();
          void submit(e.currentTarget, true);
        }}
        className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-5" noValidate>
        {error && <Alert>{error}</Alert>}
        <Field label="Title" name="title" required maxLength={200} placeholder="Payment Architecture Review" error={fields.title} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label="Date"
            name="meetingDate"
            type="date"
            required
            defaultValue={today()}
            hint="Relative deadlines like “by Friday” are counted from this date."
            error={fields.meetingDate}
          />
          <Field label="Duration (minutes)" name="durationMinutes" type="number" min={1} max={1440} error={fields.durationMinutes} />
        </div>
        <Field label="Participants" name="participants" placeholder="Sarah, John, Mike" hint="Comma-separated. Names that match team members are linked." />

        <div className="flex flex-col gap-1.5">
          <div className="flex items-center justify-between">
            <label htmlFor="transcript" className="text-sm font-medium">Transcript</label>
            <div>
              <input
                ref={fileInput}
                type="file"
                accept=".txt,.pdf,.docx,.vtt,.srt,.md,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                className="sr-only"
                id="transcript-file"
                onChange={(e) => void onFile(e.target.files?.[0])}
              />
              <label htmlFor="transcript-file" className="cursor-pointer text-sm font-medium underline">
                {uploading ? "Reading file…" : "Upload file"}
              </label>
            </div>
          </div>
          <textarea
            id="transcript"
            value={transcript}
            onChange={(e) => setTranscript(e.target.value)}
            rows={14}
            required
            aria-invalid={fields.transcript ? true : undefined}
            placeholder={"Sarah: Let's get started.\nJohn: I'll implement Redis caching by Friday."}
            className={`${inputClass} font-mono text-xs leading-relaxed`}
          />
          <div className="flex justify-between text-xs text-muted">
            <span>{fields.transcript ? <span className="text-danger">{fields.transcript}</span> : uploadNote}</span>
            <span className={transcript.length > MAX_CHARS ? "text-danger" : ""}>
              {transcript.length.toLocaleString()} / {MAX_CHARS.toLocaleString()}
            </span>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={submitting || uploading || !transcript.trim()}>
            {submitting ? "Saving…" : "Save and analyze"}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={submitting || uploading || !transcript.trim()}
            onClick={(e) => {
              const form = e.currentTarget.form;
              if (form) void submit(form, false);
            }}
          >
            Save without analyzing
          </Button>
        </div>
      </form>
    </div>
  );
}
