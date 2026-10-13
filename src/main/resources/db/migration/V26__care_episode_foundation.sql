-- Additive foundation: existing consultations and appointments are preserved.
CREATE SCHEMA IF NOT EXISTS care;
CREATE TABLE care.care_episode (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('AWAITING_PAYMENT','READY','ACTIVE','CLOSED')),
    follow_up_seconds BIGINT NOT NULL CHECK (follow_up_seconds > 0),
    started_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    retention_expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK ((started_at IS NULL AND expires_at IS NULL) OR
           (started_at IS NOT NULL AND expires_at IS NOT NULL AND started_at >= created_at
            AND expires_at = started_at + follow_up_seconds * INTERVAL '1 second')),
    CHECK (status <> 'ACTIVE' OR started_at IS NOT NULL),
    CHECK (status NOT IN ('AWAITING_PAYMENT','READY') OR started_at IS NULL),
    CHECK (retention_expires_at > created_at)
);
CREATE INDEX idx_care_episode_patient ON care.care_episode(patient_id, created_at);
CREATE INDEX idx_care_episode_org ON care.care_episode(organization_id, status);
CREATE INDEX idx_care_episode_retention ON care.care_episode(retention_expires_at);
CREATE TABLE care.care_visit (
    id UUID PRIMARY KEY,
    episode_id UUID NOT NULL REFERENCES care.care_episode(id),
    professional_id UUID NOT NULL REFERENCES professional.professional(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    follow_up BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    retention_expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (scheduled_at >= created_at),
    CHECK (started_at IS NULL OR started_at >= created_at),
    CHECK ((status IN ('SCHEDULED','CANCELLED') AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL AND completed_at >= started_at)),
    CHECK (retention_expires_at > created_at)
);
-- One initial visit; a cancelled initial visit can be replaced.
CREATE UNIQUE INDEX uq_care_visit_initial ON care.care_visit(episode_id)
    WHERE NOT follow_up AND status <> 'CANCELLED';
CREATE INDEX idx_care_visit_calendar ON care.care_visit(professional_id, scheduled_at);
CREATE INDEX idx_care_visit_episode ON care.care_visit(episode_id);
CREATE INDEX idx_care_visit_retention ON care.care_visit(retention_expires_at);
