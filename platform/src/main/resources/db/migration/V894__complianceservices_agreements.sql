-- Compliance Services: agreements. An agreement is the engagement that follows an accepted quote. It copies the
-- scope as quoted, records the term and the billing arrangement, and is activated by a person who says how the
-- client confirmed. One quote makes at most one live agreement. Nothing here invoices: the schedule is a preview
-- until billing events are built.

CREATE TABLE IF NOT EXISTS compliance_agreements (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    client_id            UUID NOT NULL REFERENCES compliance_service_clients(id),
    quote_id             UUID NOT NULL REFERENCES compliance_quotes(id),
    agreement_number     VARCHAR(30) NOT NULL,
    title                VARCHAR(255),
    status               VARCHAR(12) NOT NULL DEFAULT 'DRAFT',      -- DRAFT, ACTIVE, SUSPENDED, ENDED, CANCELLED
    start_date           DATE NOT NULL,
    end_date             DATE,
    billing_frequency    VARCHAR(12) NOT NULL,                      -- ONCE, MONTHLY, QUARTERLY, ANNUALLY
    billing_amount       NUMERIC(14,2) NOT NULL,                    -- ex VAT, each time the client is billed
    first_billing_date   DATE NOT NULL,
    auto_renew           BOOLEAN NOT NULL DEFAULT FALSE,
    notice_days          INTEGER NOT NULL DEFAULT 30,
    notes                TEXT,
    terms                TEXT,
    quoted_professional  NUMERIC(14,2) NOT NULL DEFAULT 0,          -- what the quote said, for comparison
    quoted_total         NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency             VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    created_by           UUID,
    activated_by         UUID,
    activated_at         TIMESTAMPTZ,
    activation_note      TEXT,
    suspended_reason     TEXT,
    ended_by             UUID,
    ended_at             TIMESTAMPTZ,
    end_reason           TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_agreement_number UNIQUE (tenant_id, agreement_number),
    CONSTRAINT ck_compliance_agreement_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'ENDED', 'CANCELLED')),
    CONSTRAINT ck_compliance_agreement_frequency CHECK (billing_frequency IN ('ONCE', 'MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    CONSTRAINT ck_compliance_agreement_amount CHECK (billing_amount > 0),
    CONSTRAINT ck_compliance_agreement_term CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_compliance_agreement_first CHECK (first_billing_date >= start_date AND (end_date IS NULL OR status = 'ENDED' OR first_billing_date <= end_date)),
    CONSTRAINT ck_compliance_agreement_notice CHECK (notice_days >= 0 AND notice_days <= 365),
    CONSTRAINT ck_compliance_agreement_activated CHECK (status IN ('DRAFT', 'CANCELLED') OR activated_at IS NOT NULL),
    CONSTRAINT ck_compliance_agreement_ended CHECK (status NOT IN ('ENDED') OR ended_at IS NOT NULL)
);
-- one quote, one live agreement: a cancelled agreement frees the quote to be used again
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_agreement_quote ON compliance_agreements(tenant_id, quote_id) WHERE status <> 'CANCELLED';
CREATE INDEX IF NOT EXISTS idx_compliance_agreements_client ON compliance_agreements(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_compliance_agreements_status ON compliance_agreements(tenant_id, status);

CREATE TABLE IF NOT EXISTS compliance_agreement_lines (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    agreement_id    UUID NOT NULL REFERENCES compliance_agreements(id) ON DELETE CASCADE,
    service_code    VARCHAR(60) NOT NULL,
    service_name    VARCHAR(255),
    service_version INTEGER,
    pricing_model   VARCHAR(20) NOT NULL,
    description     VARCHAR(500),
    professional    NUMERIC(14,2) NOT NULL DEFAULT 0,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_agreement_line_amount CHECK (professional >= 0)
);
CREATE INDEX IF NOT EXISTS idx_compliance_agreement_lines ON compliance_agreement_lines(agreement_id);
