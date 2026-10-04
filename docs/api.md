# API

Base path: `/api/v1`. All responses are JSON. Every response includes an `X-Correlation-Id` header.

## Error format

```json
{
  "timestamp": "2026-10-03T20:30:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Meeting title is required",
  "path": "/api/v1/meetings",
  "correlationId": "5b1c...",
  "violations": [{ "field": "title", "message": "Meeting title is required" }]
}
```

Error codes: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL_ERROR`.

## Pagination

Collection endpoints return `PageResponse`: `{ content, page, size, totalElements, totalPages }`.

## Endpoints available now

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/system/info` | Public | Service name and API version |
| GET | `/actuator/health` | Public | Health status |
| GET | `/actuator/info` | Public | Build/app info |
| GET | `/actuator/metrics`, `/actuator/prometheus` | Authenticated | Metrics |

The full planned API is in PRD section 29.
