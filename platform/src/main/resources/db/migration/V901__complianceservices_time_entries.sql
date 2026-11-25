-- Compliance Services: hours worked for a client. An entry is never edited or deleted; a mistake is voided with a reason and entered again.
-- Hours alone say nothing about cost: rates are held separately and only administrators see them.

CREATE TABLE IF NOT EXISTS compliance_time_entries (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    client_id     UUID NOT NULL REFERENCES compliance_service_clients(id),
    user_id       UUID NOT NULL,
    work_date     DATE NOT NULL,
    minutes       INTEGER NOT NULL,
    service_code  VARCHAR(60),
    kind          VARCHAR(10) NOT NULL DEFAULT 'PLANNED',
    billable      BOOLEAN NOT NULL DEFAULT TRUE,
    note          VARCHAR(1000),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status        VARCHAR(6) NOT NULL DEFAULT 'ACTIVE',
    voided_by     UUID,
    voided_at     TIMESTAMPTZ,
    void_reason   TEXT,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_time_minutes CHECK (minutes BETWEEN 1 AND 720),
    CONSTRAINT ck_compliance_time_kind CHECK (kind IN ('PLANNED', 'REWORK', 'UNPLANNED')),
    CONSTRAINT ck_compliance_time_status CHECK (status IN ('ACTIVE', 'VOID')),
    CONSTRAINT ck_compliance_time_void CHECK ((status = 'VOID') = (voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL AND length(btrim(void_reason)) >= 10))
);
CREATE INDEX IF NOT EXISTS idx_compliance_time_client ON compliance_time_entries(tenant_id, client_id, work_date);
CREATE INDEX IF NOT EXISTS idx_compliance_time_user ON compliance_time_entries(tenant_id, user_id, work_date);
