-- V255__marketing_journeys.sql  (rename to your next Flyway version)

-- Hidden per-step campaigns for journey emails.
ALTER TABLE mkt_campaigns ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'CAMPAIGN';
CREATE INDEX IF NOT EXISTS idx_mkt_campaigns_kind ON mkt_campaigns (tenant_id, kind) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS mkt_journeys (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    name              VARCHAR(150) NOT NULL,
    description       TEXT,
    trigger_type      VARCHAR(30)  NOT NULL,   -- OPTED_IN | SEGMENT_ENTRY | OPT_IN_ANNIVERSARY
    segment_id        UUID,
    segment_name      VARCHAR(150),
    status            VARCHAR(15)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ACTIVE | PAUSED | ARCHIVED
    steps             JSONB        NOT NULL,
    reentry_days      INTEGER,                 -- NULL = a contact can never re-enter
    min_gap_hours     INTEGER      NOT NULL DEFAULT 24,
    window_start_hour INTEGER      NOT NULL DEFAULT 8,
    window_end_hour   INTEGER      NOT NULL DEFAULT 17,
    skip_weekends     BOOLEAN      NOT NULL DEFAULT true,
    activated_at      TIMESTAMPTZ,
    created_by        UUID,
    updated_by        UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_mkt_journeys_tenant ON mkt_journeys (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_mkt_journeys_active ON mkt_journeys (status) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS mkt_journey_enrollments (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    journey_id    UUID         NOT NULL REFERENCES mkt_journeys (id),
    preference_id UUID         NOT NULL,
    email         VARCHAR(320) NOT NULL,
    status        VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE | COMPLETED | EXITED | CANCELLED
    current_step  INTEGER      NOT NULL DEFAULT 0,
    next_run_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enrolled_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    exit_reason   VARCHAR(300),
    locked_at     TIMESTAMPTZ,
    error_count   INTEGER      NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);
-- A contact can be ACTIVE in a journey at most once (race-safe enrolment).
CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_je_active ON mkt_journey_enrollments (journey_id, preference_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_mkt_je_due ON mkt_journey_enrollments (next_run_at) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_mkt_je_journey ON mkt_journey_enrollments (journey_id, status);
CREATE INDEX IF NOT EXISTS idx_mkt_je_pref ON mkt_journey_enrollments (preference_id);

CREATE TABLE IF NOT EXISTS mkt_journey_step_log (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    journey_id          UUID         NOT NULL,
    enrollment_id       UUID         NOT NULL,
    preference_id       UUID         NOT NULL,
    step_index          INTEGER      NOT NULL,
    step_type           VARCHAR(15)  NOT NULL,
    outcome             VARCHAR(30)  NOT NULL,   -- SENT | WAITING | GOAL_CHECKED | GOAL_REACHED | CONSENT_EXIT
    campaign_contact_id UUID,
    detail              VARCHAR(300),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_jsl_enrollment ON mkt_journey_step_log (enrollment_id, created_at);
CREATE INDEX IF NOT EXISTS idx_mkt_jsl_pref_sent  ON mkt_journey_step_log (tenant_id, preference_id, created_at DESC) WHERE outcome = 'SENT';