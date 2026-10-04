-- Phase 3: AI analysis jobs and the structured results they produce (PRD sections 10-14, 28, 35).

CREATE TABLE meeting_analyses (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    meeting_id       UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    requested_by     UUID         NOT NULL REFERENCES users (id),
    model            VARCHAR(100),
    prompt_version   VARCHAR(40)  NOT NULL,
    input_tokens     INTEGER,
    output_tokens    INTEGER,
    duration_ms      INTEGER,
    error_code       VARCHAR(40),
    -- Safe to show to users; never contains provider internals or transcript text.
    error_message    VARCHAR(500),
    -- The model's validated-before-persisting JSON output, kept for debugging and evaluation.
    raw_output       TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ
);

CREATE INDEX idx_meeting_analyses_meeting ON meeting_analyses (meeting_id, created_at DESC);
CREATE INDEX idx_meeting_analyses_status ON meeting_analyses (status);

CREATE TABLE tasks (
    id                UUID PRIMARY KEY,
    organization_id   UUID          NOT NULL REFERENCES organizations (id),
    meeting_id        UUID          NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    analysis_id       UUID REFERENCES meeting_analyses (id) ON DELETE SET NULL,
    title             VARCHAR(300)  NOT NULL,
    description       TEXT,
    owner_id          UUID REFERENCES users (id),
    -- The name as extracted from the transcript, even when it matched no member.
    owner_name        VARCHAR(120),
    owner_status      VARCHAR(20)   NOT NULL CHECK (owner_status IN ('RESOLVED', 'UNRESOLVED', 'UNASSIGNED')),
    priority          VARCHAR(10)   NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status            VARCHAR(20)   NOT NULL,
    deadline          DATE,
    deadline_text     VARCHAR(200),
    deadline_status   VARCHAR(20)   NOT NULL CHECK (deadline_status IN ('NONE', 'RESOLVED', 'NEEDS_REVIEW')),
    ai_confidence     NUMERIC(3, 2) NOT NULL CHECK (ai_confidence BETWEEN 0 AND 1),
    evidence          TEXT,
    position          INTEGER       NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_tasks_meeting ON tasks (meeting_id, position);
CREATE INDEX idx_tasks_org_owner ON tasks (organization_id, owner_id);

CREATE TABLE decisions (
    id               UUID PRIMARY KEY,
    organization_id  UUID          NOT NULL REFERENCES organizations (id),
    meeting_id       UUID          NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    analysis_id      UUID REFERENCES meeting_analyses (id) ON DELETE SET NULL,
    decision         TEXT          NOT NULL,
    context          TEXT,
    ai_confidence    NUMERIC(3, 2) NOT NULL CHECK (ai_confidence BETWEEN 0 AND 1),
    evidence         TEXT,
    position         INTEGER       NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_decisions_meeting ON decisions (meeting_id, position);
CREATE INDEX idx_decisions_org_created ON decisions (organization_id, created_at DESC);

CREATE TABLE risks (
    id               UUID PRIMARY KEY,
    organization_id  UUID          NOT NULL REFERENCES organizations (id),
    meeting_id       UUID          NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    analysis_id      UUID REFERENCES meeting_analyses (id) ON DELETE SET NULL,
    description      TEXT          NOT NULL,
    severity         VARCHAR(10)   NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    ai_confidence    NUMERIC(3, 2) NOT NULL CHECK (ai_confidence BETWEEN 0 AND 1),
    evidence         TEXT,
    position         INTEGER       NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_risks_meeting ON risks (meeting_id, position);

CREATE TABLE questions (
    id               UUID PRIMARY KEY,
    organization_id  UUID          NOT NULL REFERENCES organizations (id),
    meeting_id       UUID          NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    analysis_id      UUID REFERENCES meeting_analyses (id) ON DELETE SET NULL,
    question         TEXT          NOT NULL,
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('UNRESOLVED', 'RESOLVED')),
    ai_confidence    NUMERIC(3, 2) NOT NULL CHECK (ai_confidence BETWEEN 0 AND 1),
    evidence         TEXT,
    position         INTEGER       NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_questions_meeting ON questions (meeting_id, position);
