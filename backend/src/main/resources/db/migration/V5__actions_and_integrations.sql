-- Phases 4-7: editable tasks, AI-suggested actions with human approval, and Jira/Slack
-- integrations (PRD sections 15-18, 28, 51-52).

ALTER TABLE tasks ADD COLUMN edited_by UUID REFERENCES users (id);
ALTER TABLE tasks ADD COLUMN edited_at TIMESTAMPTZ;

CREATE INDEX idx_tasks_org_status ON tasks (organization_id, status);
CREATE INDEX idx_tasks_org_deadline ON tasks (organization_id, deadline);

-- One row per connected provider per organization. Tokens are AES-GCM encrypted by the
-- application and never returned by the API.
CREATE TABLE integrations (
    id                       UUID PRIMARY KEY,
    organization_id          UUID         NOT NULL REFERENCES organizations (id),
    provider                 VARCHAR(20)  NOT NULL CHECK (provider IN ('JIRA', 'SLACK', 'GITHUB')),
    status                   VARCHAR(20)  NOT NULL CHECK (status IN ('CONNECTED', 'ERROR')),
    encrypted_access_token   TEXT         NOT NULL,
    encrypted_refresh_token  TEXT,
    expires_at               TIMESTAMPTZ,
    -- Jira cloud ID or Slack team ID, plus a display name.
    external_workspace_id    VARCHAR(100) NOT NULL,
    external_workspace_name  VARCHAR(200),
    -- Provider settings chosen by an admin, e.g. Jira project and issue type, Slack channel.
    config                   TEXT         NOT NULL DEFAULT '{}',
    connected_by             UUID         NOT NULL REFERENCES users (id),
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_integrations_org_provider UNIQUE (organization_id, provider)
);

CREATE TABLE ai_actions (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organizations (id),
    meeting_id        UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    task_id           UUID REFERENCES tasks (id) ON DELETE CASCADE,
    action_type       VARCHAR(30)  NOT NULL CHECK (action_type IN ('CREATE_JIRA_ISSUE', 'SEND_SLACK_MESSAGE', 'DRAFT_EMAIL')),
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    -- meetingId + taskId + actionType (PRD section 52): the same action can never exist twice.
    idempotency_key   VARCHAR(120) NOT NULL,
    requested_by      UUID         NOT NULL REFERENCES users (id),
    approved_by       UUID REFERENCES users (id),
    approved_at       TIMESTAMPTZ,
    attempts          INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ,
    executed_at       TIMESTAMPTZ,
    error_code        VARCHAR(40),
    error_message     VARCHAR(500),
    -- Output of actions that produce content rather than an external record (email drafts).
    result            TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_ai_actions_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_ai_actions_org_status ON ai_actions (organization_id, status, created_at DESC);
CREATE INDEX idx_ai_actions_meeting ON ai_actions (meeting_id);
CREATE INDEX idx_ai_actions_due ON ai_actions (status, next_attempt_at);

-- The record an executed action created in another system (PRD section 4.4).
CREATE TABLE external_actions (
    id            UUID PRIMARY KEY,
    ai_action_id  UUID         NOT NULL REFERENCES ai_actions (id) ON DELETE CASCADE,
    provider      VARCHAR(20)  NOT NULL,
    external_id   VARCHAR(200) NOT NULL,
    external_url  TEXT,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_external_actions_action UNIQUE (ai_action_id)
);
