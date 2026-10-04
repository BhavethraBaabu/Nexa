# Nexa

**Where meetings become momentum.**

Nexa is an AI-powered meeting-to-work platform. It turns meeting transcripts into structured tasks, decisions, owners, deadlines and risks. After a person approves them, it carries out the actions in tools like Jira and Slack.

> **Status:** Phases 0–3 are complete: foundation, authentication and RBAC, meetings, and AI analysis. See the [roadmap](#roadmap) and [PRD.md](PRD.md).

## Planned features

- AI meeting analysis
- Action-item extraction
- Decision tracking
- RAG-powered meeting search
- Jira automation
- Slack automation
- Human-in-the-loop AI
- Role-based access control
- Audit logging
- Observability

## Architecture

```
                 Browser
                    │
                    ▼
          Next.js frontend (:3000)
                    │  REST /api/v1
                    ▼
        Spring Boot modular monolith (:8080)
                    │
       ┌────────────┼─────────────┐
       ▼            ▼             ▼
 PostgreSQL      Redis*        Kafka*
 + pgvector
                                   * later phases
```

See [docs/architecture.md](docs/architecture.md).

## Tech stack

Java 21 · Spring Boot 4 · PostgreSQL 17 · pgvector · Flyway · Claude (Anthropic Java SDK) · Next.js 16 · TypeScript · Tailwind CSS · Docker · GitHub Actions
Planned: Redis · Kafka · Terraform · AWS

## Local setup

### Prerequisites

- Java 21
- Node.js 24
- PostgreSQL 17 with pgvector, either through Docker or installed natively

### 1. Start PostgreSQL

**With Docker:**

```bash
docker compose up -d postgres
```

**With Homebrew (macOS):**

```bash
brew install postgresql@17 pgvector
brew services start postgresql@17
psql -d postgres -c "CREATE ROLE nexa LOGIN PASSWORD 'nexa';" -c "CREATE DATABASE nexa OWNER nexa;" -c "CREATE DATABASE nexa_test OWNER nexa;"
```

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

- Health: http://localhost:8080/actuator/health
- API: http://localhost:8080/api/v1/system/info

### 3. Run the frontend

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Open http://localhost:3000 and create an organization.

To turn on AI analysis, set an Anthropic API key before starting the backend. Without one, meetings still work but analysis reports that AI isn't configured:

```bash
export LLM_API_KEY=sk-ant-...
```

In local development, password-reset and invitation emails aren't sent. The backend prints the links to its console instead (look for `[DEV MAIL]`).

### Full stack in Docker

```bash
docker compose --profile app up --build
```

## Testing

```bash
cd backend && ./mvnw verify            # unit + integration tests (needs Postgres), JaCoCo report in target/site/jacoco
cd frontend && npm test && npm run lint && npm run typecheck
LLM_API_KEY=sk-ant-... ./mvnw test -Pevaluation   # (backend) AI quality report on /evaluation, calls the paid API
```

## Project structure

```
nexa-ai/
├── backend/          Spring Boot API (modular monolith, com.nexa.*)
├── frontend/         Next.js + TypeScript app
├── infrastructure/   Local DB init, Terraform (Phase 10)
├── evaluation/       AI evaluation transcripts, expected results, runner instructions
├── docs/             Architecture, API and AI docs
├── .github/          CI workflows
├── docker-compose.yml
├── .env.example
└── PRD.md            Product requirements (source of truth)
```

## Roadmap

| Phase | Scope | Status |
|------:|-------|--------|
| 0 | Foundation | ✅ Done |
| 1 | Authentication & organizations | ✅ Done |
| 2 | Meetings | ✅ Done |
| 3 | AI analysis | ✅ Done |
| 4 | Tasks & decisions | Next |
| 5 | Human approval | |
| 6 | Jira | |
| 7 | Slack | |
| 8 | RAG | |
| 9 | Agent | |
| 10 | Production hardening | |

## Screenshots

_Added as features ship._
