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
