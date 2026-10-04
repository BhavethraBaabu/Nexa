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
