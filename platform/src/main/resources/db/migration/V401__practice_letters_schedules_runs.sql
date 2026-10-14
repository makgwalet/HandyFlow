-- Finance F22: engagement letters, recurring service schedules and the runs they produce.
CREATE TABLE IF NOT EXISTS acc_engagement_letters (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    client_id         UUID NOT NULL REFERENCES acc_clients (id) ON DELETE CASCADE,
    version           INT NOT NULL,
    title             VARCHAR(150) NOT NULL,
    scope             TEXT NOT NULL,
    exclusions        TEXT,
    fee_basis         VARCHAR(10) NOT NULL CHECK (fee_basis IN ('FIXED', 'RETAINER', 'HOURLY', 'MIXED')),
    fee_amount        NUMERIC(15,2),
    fee_notes         TEXT,
    start_date        DATE NOT NULL,
    end_date          DATE,
    status            VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'SUPERSEDED', 'ENDED')),
    body_text         TEXT,
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_by           UUID,
    sent_at           TIMESTAMPTZ,
    accepted_by_name  VARCHAR(150),
    accepted_method   VARCHAR(12) CHECK (accepted_method IN ('SIGNED_COPY', 'EMAIL', 'OTHER')),
    accepted_on       DATE,
    accepted_recorded_by UUID,
    decline_reason    VARCHAR(500),
    ended_at          TIMESTAMPTZ,
    CONSTRAINT uq_acc_letter_version UNIQUE (client_id, version)
);
CREATE INDEX IF NOT EXISTS idx_acc_letters_client ON acc_engagement_letters (tenant_id, client_id, version DESC);
-- At most one accepted letter per client.
CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_letter_accepted ON acc_engagement_letters (client_id) WHERE status = 'ACCEPTED';

CREATE TABLE IF NOT EXISTS acc_service_schedules (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    client_id      UUID NOT NULL REFERENCES acc_clients (id) ON DELETE CASCADE,
    service_type   VARCHAR(40) NOT NULL,
    description    VARCHAR(300),
    frequency      VARCHAR(10) NOT NULL CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    first_date     DATE NOT NULL,
    end_date       DATE,
    fee_amount     NUMERIC(15,2),
    include_vat    BOOLEAN NOT NULL DEFAULT TRUE,
    due_days       INT NOT NULL DEFAULT 30,
    budget_hours   NUMERIC(6,2),
    owner_user_id  UUID,
    letter_id      UUID,
    status         VARCHAR(8) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acc_sched_tenant ON acc_service_schedules (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_acc_sched_client ON acc_service_schedules (client_id);

-- One row per occurrence; the unique key means a repeated job cannot make the same run twice.
CREATE TABLE IF NOT EXISTS acc_service_runs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    schedule_id   UUID NOT NULL REFERENCES acc_service_schedules (id) ON DELETE CASCADE,
    client_id     UUID NOT NULL,
    run_index     INT NOT NULL,
    run_date      DATE NOT NULL,
    status        VARCHAR(8) NOT NULL DEFAULT 'DUE' CHECK (status IN ('DUE', 'BILLED', 'SKIPPED')),
    fee_note_id   UUID,
    handled_by    UUID,
    handled_at    TIMESTAMPTZ,
    note          VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_acc_service_run UNIQUE (schedule_id, run_index)
);
CREATE INDEX IF NOT EXISTS idx_acc_runs_due ON acc_service_runs (tenant_id, status, run_date);
