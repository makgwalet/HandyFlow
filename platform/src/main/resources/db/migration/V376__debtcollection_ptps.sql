-- RENAME to the next free Flyway version AFTER the Chunk 1 work-items migration.
-- Same base-column ASSUMPTION as Chunk 1 (id, tenant_id, created_at, updated_at, version) — align with AggregateRoot.
-- Chunk 1 note: WorkItemType gained PTP_DUE; type is VARCHAR(30) with no CHECK constraint, so no DDL change is needed.

CREATE TABLE debtcollection_ptps (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID          NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    case_id                UUID          NOT NULL REFERENCES debtcollection_cases (id),
    series_id              UUID          NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    amount                 NUMERIC(15,2) NOT NULL,
    due_date               DATE          NOT NULL,
    frequency              VARCHAR(15)   NOT NULL,
    occurrences_remaining  INT           NOT NULL DEFAULT 0,
    source                 VARCHAR(20)   NOT NULL,
    terms                  TEXT,
    grace_days             INT           NOT NULL DEFAULT 2,
    baseline_outstanding   NUMERIC(15,2),
    amount_received        NUMERIC(15,2) NOT NULL DEFAULT 0,
    resolved_date          DATE,
    resolution_note        VARCHAR(500),
    created_by             UUID,
    created_by_name        VARCHAR(255)
);
CREATE INDEX ix_dc_ptps_case ON debtcollection_ptps (tenant_id, case_id);
CREATE INDEX ix_dc_ptps_active_due ON debtcollection_ptps (status, due_date);
CREATE INDEX ix_dc_ptps_resolved ON debtcollection_ptps (tenant_id, resolved_date) WHERE resolved_date IS NOT NULL;
-- Hard guarantee behind PtpService's "one open promise per case" check (closes the race between two collectors).
CREATE UNIQUE INDEX uq_dc_ptps_one_open_per_case ON debtcollection_ptps (case_id) WHERE status IN ('PROPOSED','ACTIVE');

CREATE TABLE debtcollection_ptp_receipts (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    ptp_id              UUID          NOT NULL REFERENCES debtcollection_ptps (id),
    case_id             UUID          NOT NULL,
    amount              NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    received_date       DATE          NOT NULL,
    reference           VARCHAR(100),
    notes               TEXT,
    recorded_by_user_id UUID
);
CREATE INDEX ix_dc_ptp_receipts_ptp ON debtcollection_ptp_receipts (ptp_id);

CREATE TABLE debtcollection_ptp_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    ptp_id           UUID         NOT NULL REFERENCES debtcollection_ptps (id),
    case_id          UUID         NOT NULL,
    event_type       VARCHAR(40)  NOT NULL,
    detail           TEXT,
    actor_user_id    UUID,
    actor_user_name  VARCHAR(255)
);
CREATE INDEX ix_dc_ptp_events_ptp ON debtcollection_ptp_events (ptp_id, created_at);
