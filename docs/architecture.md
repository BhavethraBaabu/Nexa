# Architecture

Nexa is a **modular monolith** (PRD section 31): one Spring Boot deployable, split into packages with clear boundaries, so it can be broken into services later if needed.

## Backend modules (`com.nexa`)

| Package | Responsibility | Phase |
|---------|----------------|-------|
| `common` | API models, error handling, security config, correlation IDs | 0 ✅ |
| `auth`, `user`, `organization` | Authentication, JWT, tenants, RBAC | 1 ✅ |
| `meeting` | Meetings and transcripts | 2 |
| `ai` | Extraction, validation, embeddings, agent | 3, 8, 9 |
| `task`, `decision`, `risk` | Extracted work items | 4 |
| `audit` | Audit log writer (UI in Phase 5) | 1 ✅ |
| `action` | Approval lifecycle | 5 |
| `integration.jira/slack/github` | External adapters behind a tool interface | 6, 7 |
| `search` | Full-text and semantic search | 8 |
| `notification` | Email (`Mailer`); a logging implementation until delivery is set up | 1 (partial) |

## Cross-cutting foundations (Phase 0)

- **Errors**: every failure returns `ApiError` (`timestamp`, `status`, `error`, `message`, `path`, `correlationId`, optional `violations`). `GlobalExceptionHandler` handles MVC errors, and `JsonSecurityErrorHandler` handles 401/403 raised inside the security filter chain. Internal details are never returned to the client.
- **Correlation IDs**: `CorrelationIdFilter` accepts a safe `X-Correlation-Id` header or generates one. It echoes the ID in the response and puts it in the logging MDC.
- **Security**: stateless and deny-by-default. Public: `GET /actuator/health`, `/actuator/info`, `/api/v1/system/info`. CSRF is off because the API will use bearer tokens (Phase 1). Security headers include CSP, HSTS, X-Frame-Options and nosniff. CORS origins come from `nexa.cors.allowed-origins`. Passwords use a delegating encoder (BCrypt by default).
- **Database**: Flyway owns the schema (`db/migration`), and Hibernate is `ddl-auto: validate`. `open-in-view` is off.
- **Logging**: plain text locally, structured JSON (Logstash format) in prod via Spring Boot's built-in structured logging.
- **Profiles**: `local` (default), `test` (uses the `nexa_test` database), `prod` (all connection settings required from the environment).
- **Observability**: Actuator health (with liveness/readiness probes), info, metrics and Prometheus. Only health and info are public.

## Frontend (`frontend/src`)

```
app/         routes (App Router)
components/  shared UI (AppShell, SidebarNav)
features/    feature modules: dashboard, meetings, tasks, actions, integrations
lib/         api-client (typed fetch + ApiError mapping), config
hooks/       shared hooks
types/       API types mirroring backend DTOs
```

## Authentication and tenancy (Phase 1)

```
Browser ──(Bearer JWT)──▶ BearerTokenAuthenticationFilter ──▶ JwtUserAuthenticationConverter
                                                              │ reload user, check ACTIVE,
                                                              │ check org claim matches
                                                              ▼
                                                     AuthenticatedUser(userId, organizationId, role)
                                                              │
                                     @PreAuthorize("hasRole('ADMIN')")  ·  ADMIN > MANAGER > MEMBER
                                                              ▼
                                         Services query with current.organizationId()
```

- **Tenant isolation**: services never take an organization ID from the client. They use `AuthenticatedUser.organizationId()` and repository methods such as `findByIdAndOrganizationId`. Integration tests cover cross-organization access.
- **Tokens**: access JWTs are short-lived and only an identifier. Refresh, password-reset and invitation tokens are 256-bit random values, and only their SHA-256 hashes are stored.
- **Audit**: `AuditService` writes to `audit_logs` inside the caller's transaction. It records registration, logins (successful and failed), logout, token-reuse detection, password resets, invitations, role changes and removals.
- **Frontend session**: the access token is held in memory only. On load, `AuthProvider` restores the session through `/auth/refresh` and refreshes again before the token expires. Simultaneous refresh calls share one request, because sending the same refresh token twice looks like theft and ends the session.
