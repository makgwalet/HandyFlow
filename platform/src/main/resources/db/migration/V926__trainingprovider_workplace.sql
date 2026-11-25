-- Training provider: workplace learning. A placement ties a learner's enrolment to a host workplace and a mentor. It moves planned, active, completed,
-- or is cancelled with a reason. A logbook records dated hours and what was done. The mentor's confirmation is recorded by staff and names the mentor.
-- Hours are totalled, never compared with a threshold, and logged hours are not a competency decision. Nothing is deleted: entries are voided with a reason.

CREATE TABLE trainprov_workplace_placements (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    placement_no    VARCHAR(30) NOT NULL,
    enrolment_id    UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    host_name       VARCHAR(200) NOT NULL,
    host_contact    VARCHAR(300),
    mentor_name     VARCHAR(200) NOT NULL,
    mentor_contact  VARCHAR(300),
    agreement_ref   VARCHAR(200),
    start_date      DATE NOT NULL,
    end_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    started_on      DATE,
    ended_on        DATE,
    cancel_reason   TEXT,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_placement_no UNIQUE (tenant_id, placement_no),
    CONSTRAINT ck_trainprov_placement_names CHECK (COALESCE(char_length(btrim(host_name)), 0) >= 2 AND COALESCE(char_length(btrim(mentor_name)), 0) >= 2),
    CONSTRAINT ck_trainprov_placement_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_trainprov_placement_status CHECK (
        (status = 'PLANNED' AND started_on IS NULL AND ended_on IS NULL AND cancel_reason IS NULL)
        OR (status = 'ACTIVE' AND started_on IS NOT NULL AND ended_on IS NULL AND cancel_reason IS NULL)
        OR (status = 'COMPLETED' AND started_on IS NOT NULL AND ended_on IS NOT NULL AND ended_on >= started_on AND cancel_reason IS NULL)
        OR (status = 'CANCELLED' AND ended_on IS NULL AND COALESCE(char_length(btrim(cancel_reason)), 0) >= 10))
);
-- One active placement per enrolment at a time.
CREATE UNIQUE INDEX uq_trainprov_placement_active ON trainprov_workplace_placements (tenant_id, enrolment_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_placement_enrolment ON trainprov_workplace_placements (tenant_id, enrolment_id);

CREATE TABLE trainprov_workplace_logs (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    placement_id        UUID NOT NULL REFERENCES trainprov_workplace_placements(id),
    log_date            DATE NOT NULL,
    hours               NUMERIC(4, 2) NOT NULL CHECK (hours > 0 AND hours <= 24),
    activity            TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'RECORDED' CHECK (status IN ('RECORDED', 'VOID')),
    void_reason         TEXT,
    void_by             UUID,
    confirmed_on        DATE,
    confirmed_by_name   VARCHAR(200),
    confirmation_by     UUID,
    entered_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_log_activity CHECK (COALESCE(char_length(btrim(activity)), 0) >= 10),
    CONSTRAINT ck_trainprov_log_void CHECK (
        (status = 'RECORDED' AND void_reason IS NULL AND void_by IS NULL)
        OR (status = 'VOID' AND COALESCE(char_length(btrim(void_reason)), 0) >= 10 AND void_by IS NOT NULL)),
    CONSTRAINT ck_trainprov_log_confirm CHECK (
        (confirmed_on IS NULL AND confirmed_by_name IS NULL AND confirmation_by IS NULL)
        OR (confirmed_on IS NOT NULL AND COALESCE(char_length(btrim(confirmed_by_name)), 0) >= 2 AND confirmation_by IS NOT NULL AND confirmed_on >= log_date))
);
-- One live entry per placement per day.
CREATE UNIQUE INDEX uq_trainprov_log_day ON trainprov_workplace_logs (tenant_id, placement_id, log_date) WHERE status = 'RECORDED';
CREATE INDEX idx_trainprov_log_placement ON trainprov_workplace_logs (tenant_id, placement_id, log_date);
