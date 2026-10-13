-- RENAME to the next free Flyway version AFTER the Chunk 5 legal-handover migration.
-- Same base-column ASSUMPTION as earlier chunks (id, tenant_id, created_at, updated_at, version) — align with AggregateRoot.

CREATE TABLE debtcollection_recovery_entries (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID          NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    case_id                UUID          NOT NULL REFERENCES debtcollection_cases (id),
    entry_type             VARCHAR(15)   NOT NULL,
    amount                 NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    allocated_principal    NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (allocated_principal >= 0),
    allocated_fees         NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (allocated_fees >= 0),
    effective_date         DATE          NOT NULL,
    source                 VARCHAR(15),
    external_reference     VARCHAR(100),
    reversal_of_id         UUID REFERENCES debtcollection_recovery_entries (id),
    fee_rule_code          VARCHAR(60),
    fee_rule_version       INT,
    ptp_id                 UUID,
    notes                  TEXT,
    recorded_by_user_id    UUID,
    recorded_by_user_name  VARCHAR(255),
    -- A payment or its reversal is split exactly between principal and fees.
    CONSTRAINT ck_dc_recovery_split CHECK (
        entry_type NOT IN ('PAYMENT','REVERSAL') OR allocated_principal + allocated_fees = amount)
);
CREATE INDEX ix_dc_recovery_case ON debtcollection_recovery_entries (tenant_id, case_id, effective_date DESC);
CREATE INDEX ix_dc_recovery_date ON debtcollection_recovery_entries (tenant_id, effective_date);
-- Idempotent payments: one PAYMENT per external reference per tenant.
CREATE UNIQUE INDEX uq_dc_recovery_payment_ref ON debtcollection_recovery_entries (tenant_id, external_reference)
    WHERE entry_type = 'PAYMENT' AND external_reference IS NOT NULL;
-- A payment can be reversed at most once.
CREATE UNIQUE INDEX uq_dc_recovery_one_reversal ON debtcollection_recovery_entries (reversal_of_id)
    WHERE reversal_of_id IS NOT NULL;

CREATE TABLE debtcollection_fee_rules (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ,
    version                 BIGINT        NOT NULL DEFAULT 0,
    code                    VARCHAR(60)   NOT NULL,
    rule_version            INT           NOT NULL,
    description             VARCHAR(255)  NOT NULL,
    calc_method             VARCHAR(25)   NOT NULL,
    fixed_amount            NUMERIC(15,2),
    percent_of_outstanding  NUMERIC(7,4),
    max_per_case            NUMERIC(15,2),
    max_charges_per_month   INT,
    effective_from          DATE          NOT NULL,
    effective_to            DATE,
    legal_basis_note        TEXT          NOT NULL,
    status                  VARCHAR(15)   NOT NULL,
    created_by              UUID          NOT NULL,
    approved_by             UUID,
    approved_by_name        VARCHAR(255),
    approved_at             TIMESTAMPTZ,
    retired_reason          VARCHAR(500)
);
CREATE UNIQUE INDEX uq_dc_fee_rules_version ON debtcollection_fee_rules (tenant_id, code, rule_version);
-- Exactly one chargeable version per code.
CREATE UNIQUE INDEX uq_dc_fee_rules_one_approved ON debtcollection_fee_rules (tenant_id, code) WHERE status = 'APPROVED';

CREATE TABLE debtcollection_case_audit_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    case_id          UUID         NOT NULL,
    event_type       VARCHAR(40)  NOT NULL,
    detail           TEXT,
    actor_user_id    UUID,
    actor_user_name  VARCHAR(255)
);
CREATE INDEX ix_dc_case_audit_case ON debtcollection_case_audit_events (tenant_id, case_id, created_at DESC);

CREATE TABLE debtcollection_exposure_snapshots (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ,
    version           BIGINT        NOT NULL DEFAULT 0,
    snapshot_date     DATE          NOT NULL,
    total_outstanding NUMERIC(15,2) NOT NULL,
    active_cases      INT           NOT NULL,
    bucket_0_30       NUMERIC(15,2) NOT NULL,
    bucket_31_60      NUMERIC(15,2) NOT NULL,
    bucket_61_90      NUMERIC(15,2) NOT NULL,
    bucket_91_120     NUMERIC(15,2) NOT NULL,
    bucket_121_plus   NUMERIC(15,2) NOT NULL
);
CREATE UNIQUE INDEX uq_dc_exposure_snapshot_day ON debtcollection_exposure_snapshots (tenant_id, snapshot_date);
