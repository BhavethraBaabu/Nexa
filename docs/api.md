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

Error codes: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `INVALID_TOKEN` (400), `BUSINESS_RULE_VIOLATION` (422), `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL_ERROR`.

## Pagination

Collection endpoints return `PageResponse`: `{ content, page, size, totalElements, totalPages }`.

## Authentication

- **Access token**: a JWT (HS256, 15 minutes) returned in the response body. Send it as `Authorization: Bearer <token>`. The user is reloaded from the database on every request, so removals and role changes apply immediately.
- **Refresh token**: an opaque random value in the `nexa_refresh` cookie (`HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, 14 days). Only its SHA-256 hash is stored. Each refresh replaces it with a new one. If an already-used token is presented, every token from that login is revoked.
- Browser clients must send requests with `credentials: "include"` so the cookie goes along.

Response body for register, login, refresh and invitation acceptance:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "…", "name": "Alice", "email": "alice@acme.com", "role": "ADMIN",
            "organization": { "id": "…", "name": "Acme" } }
}
```

## Endpoints available now

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | Public | Create an organization; the caller becomes its ADMIN (201) |
| POST | `/api/v1/auth/login` | Public | Email and password sign-in |
| POST | `/api/v1/auth/refresh` | Refresh cookie | Replace the refresh token and get a new access token |
| POST | `/api/v1/auth/logout` | Refresh cookie | Revoke the refresh token and clear the cookie (204) |
| POST | `/api/v1/auth/password-reset/request` | Public | Always returns 202, whether or not the email exists |
| POST | `/api/v1/auth/password-reset/confirm` | Public | Set a new password with the emailed token; signs out every session (204) |
| POST | `/api/v1/auth/invitations/preview` | Public | Organization, role and inviter for an invitation token |
| POST | `/api/v1/auth/invitations/accept` | Public | Create the invited account and sign in (201) |
| GET | `/api/v1/users/me` | Any role | Current user and organization |
| PATCH | `/api/v1/users/me` | Any role | Update own name |
| GET | `/api/v1/organizations/current` | Any role | Organization details and member count |
| PATCH | `/api/v1/organizations/current` | ADMIN | Rename the organization |
| GET | `/api/v1/organizations/members` | Any role | Active members |
| POST | `/api/v1/organizations/members/invite` | ADMIN | Invite by email with a role (201) |
| PATCH | `/api/v1/organizations/members/{id}` | ADMIN | Change a member's role |
| DELETE | `/api/v1/organizations/members/{id}` | ADMIN | Remove a member; revokes their sessions (204) |
| GET | `/api/v1/organizations/invitations` | ADMIN | Pending invitations |
| DELETE | `/api/v1/organizations/invitations/{id}` | ADMIN | Revoke an invitation (204) |
| GET | `/api/v1/system/info` | Public | Service name and API version |
| GET | `/actuator/health` | Public | Health status |
| GET | `/actuator/info` | Public | Build/app info |
| GET | `/actuator/metrics`, `/actuator/prometheus` | Authenticated | Metrics |

The full planned API is in PRD section 29.

## Business rules

- Emails are unique across Nexa and compared case-insensitively.
- An organization always keeps at least one ADMIN, so the last admin can't be demoted or removed.
- Admins can't remove themselves.
- Resources belonging to another organization return 404, the same as missing ones.
