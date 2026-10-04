import type { AnalysisRun, Dashboard, MeetingDetail, MeetingSummary, PageResponse } from "@/types/api";

type AuthFetch = <T>(path: string, init?: RequestInit) => Promise<T>;

export interface MeetingInput {
  title: string;
  meetingDate: string;
  durationMinutes: number | null;
  participants: string[];
  transcript: string;
}

export function meetingsApi(authFetch: AuthFetch) {
  return {
    list: (page = 0, size = 20) => authFetch<PageResponse<MeetingSummary>>(`/api/v1/meetings?page=${page}&size=${size}`),
    get: (id: string) => authFetch<MeetingDetail>(`/api/v1/meetings/${id}`),
    create: (input: MeetingInput) => authFetch<MeetingDetail>("/api/v1/meetings", { method: "POST", body: JSON.stringify(input) }),
    remove: (id: string) => authFetch<void>(`/api/v1/meetings/${id}`, { method: "DELETE" }),
    analyze: (id: string) => authFetch<AnalysisRun>(`/api/v1/meetings/${id}/analyze`, { method: "POST" }),
    dashboard: () => authFetch<Dashboard>("/api/v1/dashboard"),
    /** Extracts text from a TXT, PDF or DOCX transcript. Nothing is stored server-side. */
    extractTranscript: (file: File) => {
      const body = new FormData();
      body.append("file", file);
      return authFetch<{ fileName: string; text: string; characters: number }>("/api/v1/meetings/transcript-file", {
        method: "POST",
        body,
      });
    },
  };
}

export const dateLabel = (iso: string) =>
  new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" }).format(new Date(`${iso}T00:00:00Z`));
