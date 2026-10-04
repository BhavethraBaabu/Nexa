/** Mirrors com.nexa.common.api.ApiError on the backend (PRD section 30). */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId?: string;
  violations?: { field: string; message: string }[];
}

/** Mirrors com.nexa.common.api.PageResponse on the backend. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SystemInfo {
  name: string;
  apiVersion: string;
  tagline: string;
}

export type Role = "ADMIN" | "MANAGER" | "MEMBER";

export interface Me {
  id: string;
  name: string;
  email: string;
  role: Role;
  organization: { id: string; name: string };
}

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  /** Seconds until the access token expires. */
  expiresIn: number;
  user: Me;
}

export interface Organization {
  id: string;
  name: string;
  memberCount: number;
  createdAt: string;
}

export interface Member {
  id: string;
  name: string;
  email: string;
  role: Role;
  joinedAt: string;
}

export interface Invitation {
  id: string;
  email: string;
  role: Role;
  invitedBy: string;
  expiresAt: string;
  createdAt: string;
}

export interface InvitationPreview {
  email: string;
  role: Role;
  organizationName: string;
  invitedByName: string | null;
}

export type MeetingStatus = "UPLOADED" | "PROCESSING" | "COMPLETED" | "FAILED";
export type ConfidenceLevel = "HIGH" | "MEDIUM" | "LOW";
export type Level = "LOW" | "MEDIUM" | "HIGH";

export interface MeetingSummary {
  id: string;
  title: string;
  meetingDate: string;
  durationMinutes: number | null;
  status: MeetingStatus;
  participantCount: number;
  createdAt: string;
}

export interface AnalysisRun {
  id: string;
  meetingId: string;
  status: "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
  errorCode: string | null;
  errorMessage: string | null;
  retryable: boolean;
  model: string | null;
  promptVersion: string;
  durationMs: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface ActionItem {
  id: string;
  title: string;
  description: string | null;
  owner: { id: string; name: string } | null;
  ownerName: string | null;
  ownerStatus: "RESOLVED" | "UNRESOLVED" | "UNASSIGNED";
  priority: Level;
  status: string;
  deadline: string | null;
  deadlineText: string | null;
  deadlineStatus: "NONE" | "RESOLVED" | "NEEDS_REVIEW";
  confidence: number;
  confidenceLevel: ConfidenceLevel;
  evidence: string | null;
}

interface Scored {
  id: string;
  confidence: number;
  confidenceLevel: ConfidenceLevel;
  evidence: string | null;
}

export interface MeetingDetail {
  id: string;
  title: string;
  meetingDate: string;
  durationMinutes: number | null;
  status: MeetingStatus;
  transcript: string;
  participants: { name: string; userId: string | null }[];
  createdBy: { id: string; name: string } | null;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  analysis: {
    latestRun: AnalysisRun;
    summary: string | null;
    keyPoints: string[];
    analyzedAt: string | null;
    actionItems: ActionItem[];
    decisions: (Scored & { decision: string; context: string | null })[];
    risks: (Scored & { description: string; severity: Level })[];
    questions: (Scored & { question: string; status: string })[];
  } | null;
}

export interface Dashboard {
  meetingsThisWeek: number;
  totalMeetings: number;
  actionItems: number;
  completedTasks: number;
  overdueTasks: number;
  pendingApprovals: number;
  recentMeetings: { id: string; title: string; meetingDate: string; status: MeetingStatus }[];
  recentDecisions: { id: string; meetingId: string; decision: string }[];
}

export type TaskStatus = "SUGGESTED" | "OPEN" | "IN_PROGRESS" | "DONE" | "CANCELLED";
export type TaskFilter = "ALL" | "MINE" | "TEAM" | "OVERDUE" | "COMPLETED" | "PENDING_APPROVAL" | "SUGGESTED";
export type Provider = "JIRA" | "SLACK" | "GITHUB";

export interface Task {
  id: string;
  title: string;
  description: string | null;
  owner: { id: string; name: string } | null;
  ownerName: string | null;
  ownerStatus: "RESOLVED" | "UNRESOLVED" | "UNASSIGNED";
  priority: Level;
  status: TaskStatus;
  deadline: string | null;
  deadlineText: string | null;
  deadlineStatus: "NONE" | "RESOLVED" | "NEEDS_REVIEW";
  overdue: boolean;
  confidence: number;
  confidenceLevel: ConfidenceLevel;
  evidence: string | null;
  meeting: { id: string; title: string; meetingDate: string } | null;
  pendingActions: number;
  externalLinks: { provider: Provider; id: string; url: string | null }[];
  canEdit: boolean;
  editedBy: { id: string; name: string } | null;
  editedAt: string | null;
  createdAt: string;
}

export type ActionType = "CREATE_JIRA_ISSUE" | "SEND_SLACK_MESSAGE" | "DRAFT_EMAIL";
export type ActionStatus = "PENDING" | "APPROVED" | "REJECTED" | "EXECUTING" | "COMPLETED" | "FAILED" | "CANCELLED";

export interface AiAction {
  id: string;
  type: ActionType;
  status: ActionStatus;
  provider: Provider | null;
  meeting: { id: string; title: string; meetingDate: string } | null;
  task: {
    id: string;
    title: string;
    ownerName: string | null;
    ownerResolved: boolean;
    priority: Level;
    deadline: string | null;
    deadlineStatus: string;
    status: TaskStatus;
  } | null;
  requestedBy: { id: string; name: string } | null;
  approvedBy: { id: string; name: string } | null;
  approvedAt: string | null;
  executedAt: string | null;
  attempts: number;
  nextAttemptAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  retryable: boolean;
  external: { provider: Provider; id: string; url: string | null } | null;
  result: string | null;
  canApprove: boolean;
  blockedReason: string | null;
  createdAt: string;
}

export interface IntegrationInfo {
  provider: Provider;
  available: boolean;
  connected: boolean;
  status: "CONNECTED" | "ERROR" | null;
  workspaceName: string | null;
  configured: boolean;
  config: Record<string, string | null> | null;
  connectedAt: string | null;
}

export interface AuditEntry {
  id: string;
  timestamp: string;
  userId: string | null;
  userName: string | null;
  action: string;
  resourceType: string;
  resourceId: string | null;
  metadata: Record<string, unknown>;
}
