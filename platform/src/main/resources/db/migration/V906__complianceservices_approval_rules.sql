-- Compliance Services: approval rules for quotes. A rule can only raise who must approve a quote (never lower it, and never skip the discount limits already in force).
-- A rule is proposed by one administrator and takes effect only when a different administrator activates it. Rules are retired with a reason, never deleted.
-- No limits are supplied: the practice sets its own.

CREATE TABLE IF NOT EXISTS compliance_approval_rules (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    name              VARCHAR(120) NOT NULL,
    min_fees          NUMERIC(14,2),
    service_code      VARCHAR(60),
    risk_rating       VARCHAR(10),
    client_id         UUID REFERENCES compliance_service_clients(id),
    required_approver VARCHAR(40) NOT NULL,
    reason            TEXT NOT NULL,
    status            VARCHAR(10) NOT NULL DEFAULT 'PROPOSED',
    proposed_by       UUID NOT NULL,
    proposed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_by      UUID,
    activated_at      TIMESTAMPTZ,
    retired_by        UUID,
    retired_at        TIMESTAMPTZ,
    retire_note       TEXT,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cs_approval_rule_status CHECK (status IN ('PROPOSED', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_cs_approval_rule_approver CHECK (required_approver IN ('COMPLIANCE_MANAGER', 'PRACTICE_OWNER', 'PRACTICE_OWNER_WITH_EXCEPTION')),
    CONSTRAINT ck_cs_approval_rule_risk CHECK (risk_rating IS NULL OR risk_rating IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_cs_approval_rule_fees CHECK (min_fees IS NULL OR min_fees > 0),
    CONSTRAINT ck_cs_approval_rule_condition CHECK (min_fees IS NOT NULL OR service_code IS NOT NULL OR risk_rating IS NOT NULL OR client_id IS NOT NULL),
    CONSTRAINT ck_cs_approval_rule_reason CHECK (length(btrim(reason)) >= 10),
    CONSTRAINT ck_cs_approval_rule_activated CHECK ((status = 'PROPOSED') = (activated_by IS NULL)),
    CONSTRAINT ck_cs_approval_rule_second CHECK (activated_by IS NULL OR activated_by <> proposed_by),
    CONSTRAINT ck_cs_approval_rule_retired CHECK ((status = 'RETIRED') = (retired_by IS NOT NULL AND retired_at IS NOT NULL AND retire_note IS NOT NULL AND length(btrim(retire_note)) >= 10)),
    CONSTRAINT ck_cs_approval_rule_retired_after CHECK (retired_by IS NULL OR status = 'RETIRED')
);
CREATE INDEX IF NOT EXISTS idx_cs_approval_rules_tenant ON compliance_approval_rules(tenant_id, status);

ALTER TABLE compliance_quotes ADD COLUMN IF NOT EXISTS approval_basis TEXT;
