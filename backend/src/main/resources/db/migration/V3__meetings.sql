-- Phase 2: meetings and participants (PRD sections 8, 9 and 28).

CREATE TABLE meetings (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organizations (id),
    title             VARCHAR(200) NOT NULL,
    -- A calendar date: relative deadlines ("by Friday") are resolved against it.
    meeting_date      DATE         NOT NULL,
    duration_minutes  INTEGER CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 1440),
    transcript        TEXT         NOT NULL,
    summary           TEXT,
    key_points        TEXT[]       NOT NULL DEFAULT '{}',
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    created_by        UUID         NOT NULL REFERENCES users (id),
    analyzed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_meetings_org_date ON meetings (organization_id, meeting_date DESC, created_at DESC);

-- Participants are recorded by name as written; user_id is set only when the name
-- matches exactly one member of the organization.
CREATE TABLE meeting_participants (
    id          UUID PRIMARY KEY,
    meeting_id  UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    user_id     UUID REFERENCES users (id),
    position    INTEGER      NOT NULL
);

CREATE INDEX idx_meeting_participants_meeting ON meeting_participants (meeting_id);
