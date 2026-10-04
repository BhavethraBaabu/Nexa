import type { AiAction, AuditEntry, IntegrationInfo, PageResponse, Task, TaskFilter, TaskStatus, Level } from "@/types/api";

type AuthFetch = <T>(path: string, init?: RequestInit) => Promise<T>;

export interface TaskUpdate {
  title?: string;
  description?: string;
  ownerId?: string;
  clearOwner?: boolean;
  priority?: Level;
  deadline?: string;
  clearDeadline?: boolean;
  status?: TaskStatus;
}

/** Tasks, actions, integrations and audit log (Phases 4-7). */
export function workApi(authFetch: AuthFetch) {
  const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) });
  return {
    tasks: (filter: TaskFilter, page = 0) => authFetch<PageResponse<Task>>(`/api/v1/tasks?filter=${filter}&page=${page}&size=50`),
    updateTask: (id: string, update: TaskUpdate) => authFetch<Task>(`/api/v1/tasks/${id}`, json("PATCH", update)),

    pendingActions: () => authFetch<PageResponse<AiAction>>("/api/v1/actions/pending?size=100"),
    actionHistory: (page = 0) => authFetch<PageResponse<AiAction>>(`/api/v1/actions/history?page=${page}&size=50`),
    meetingActions: (meetingId: string) => authFetch<AiAction[]>(`/api/v1/meetings/${meetingId}/actions`),
    approve: (ids: string[]) => authFetch<AiAction[]>("/api/v1/actions/approve", json("POST", { ids })),
    reject: (ids: string[]) => authFetch<AiAction[]>("/api/v1/actions/reject", json("POST", { ids })),
    retry: (id: string) => authFetch<AiAction>(`/api/v1/actions/${id}/retry`, json("POST")),

    integrations: () => authFetch<IntegrationInfo[]>("/api/v1/integrations"),
    connect: (provider: "jira" | "slack") => authFetch<{ authorizeUrl: string }>(`/api/v1/integrations/${provider}/connect`, json("POST")),
    disconnect: (provider: "jira" | "slack") => authFetch<void>(`/api/v1/integrations/${provider}`, json("DELETE")),
    jiraProjects: () => authFetch<{ key: string; name: string }[]>("/api/v1/integrations/jira/projects"),
    jiraIssueTypes: (projectKey: string) =>
      authFetch<{ id: string; name: string }[]>(`/api/v1/integrations/jira/projects/${encodeURIComponent(projectKey)}/issue-types`),
    configureJira: (config: { projectKey: string; projectName: string; issueTypeId: string; issueTypeName: string; defaultPriority: string }) =>
      authFetch<IntegrationInfo>("/api/v1/integrations/jira/config", json("PUT", config)),
    slackChannels: () => authFetch<{ id: string; name: string }[]>("/api/v1/integrations/slack/channels"),
    configureSlack: (config: { channelId: string; channelName: string }) =>
      authFetch<IntegrationInfo>("/api/v1/integrations/slack/config", json("PUT", config)),

    auditLog: (page = 0) => authFetch<PageResponse<AuditEntry>>(`/api/v1/audit-logs?page=${page}&size=50`),
  };
}

export const ACTION_LABEL: Record<AiAction["type"], string> = {
  CREATE_JIRA_ISSUE: "Create Jira issue",
  SEND_SLACK_MESSAGE: "Send Slack notification",
  DRAFT_EMAIL: "Draft follow-up email",
};

export const titleCase = (value: string) => value.charAt(0) + value.slice(1).toLowerCase().replaceAll("_", " ");
