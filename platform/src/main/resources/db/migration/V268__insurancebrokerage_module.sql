-- ============================================================================
-- V268__insurancebrokerage_module.sql
-- Insurance Brokerage (Increment 8b) — the PROVIDER half of Module 8.
--
-- VERSION NUMBER: V268 is an ASSUMED-NEXT-SEQUENTIAL PLACEHOLDER, same
-- standing caveat every migration in this engagement carries. V267 was
-- `insurance` (internal, Increment 8a) — also never confirmed against a
-- real Flyway history table, because /platform/src/main/resources/ has
-- been excluded from this session's GitHub sync filter for the entire
-- engagement. CONFIRM the real next version number against your own
-- checkout's `flyway_schema_history` before applying this file.
--
-- module_catalogue / permission-seed rows are DELIBERATELY NOT INCLUDED
-- below, for the same reason every prior migration in this engagement
-- has left them out: this session has never had read access to a real
-- migration that performs that INSERT, so its exact column shape has
-- never been directly confirmed. Copy the exact INSERT shape from your
-- own V266 (Legal Practice) or V267 (`insurance`, internal) migration
-- before applying this file — do not guess the schema here.
-- ============================================================================

CREATE TABLE insbrok_insurers (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    contact_name    VARCHAR(255),
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    notes           VARCHAR(2000),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_insbrok_insurers_tenant ON insbrok_insurers (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE insbrok_clients (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    client_name                     VARCHAR(255) NOT NULL,
    client_type                     VARCHAR(20) NOT NULL,       -- INDIVIDUAL | COMMERCIAL
    registration_or_id_number       VARCHAR(100),
    contact_name                    VARCHAR(255),
    contact_email                   VARCHAR(255),
    contact_phone                   VARCHAR(50),
    address                         VARCHAR(500),
    default_commission_rate_pct     NUMERIC(5,2),
    notes                           VARCHAR(2000),
    active                          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    deleted_at                      TIMESTAMPTZ
);
CREATE INDEX idx_insbrok_clients_tenant ON insbrok_clients (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE insbrok_policies (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    client_id                   UUID NOT NULL REFERENCES insbrok_clients (id),
    insurer_id                  UUID NOT NULL REFERENCES insbrok_insurers (id),
    policy_number               VARCHAR(100),
    quote_reference              VARCHAR(100),
    line_of_business            VARCHAR(30) NOT NULL,          -- MOTOR | PROPERTY | HOME | LIABILITY | COMMERCIAL_ASSET | OTHER
    asset_type                  VARCHAR(100),
    asset_reference              VARCHAR(255),
    sum_insured                 NUMERIC(15,2),
    premium_amount               NUMERIC(15,2),
    premium_frequency            VARCHAR(20),                   -- MONTHLY | QUARTERLY | ANNUAL
    excess_amount                NUMERIC(15,2),
    commission_rate_pct          NUMERIC(5,2),
    start_date                   DATE,
    expiry_date                  DATE,
    status                       VARCHAR(20) NOT NULL,          -- QUOTE | BOUND | ACTIVE | LAPSED | CANCELLED | EXPIRED | RENEWED
    bound_at                     TIMESTAMPTZ,
    activated_at                 TIMESTAMPTZ,
    cancelled_date                DATE,
    cancel_reason                 VARCHAR(1000),
    renewal_of_policy_id          UUID REFERENCES insbrok_policies (id),
    expiry_reminder_sent_at       TIMESTAMPTZ,
    notes                         VARCHAR(2000),
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_insbrok_policies_tenant ON insbrok_policies (tenant_id);
CREATE INDEX idx_insbrok_policies_client ON insbrok_policies (tenant_id, client_id);
CREATE INDEX idx_insbrok_policies_status ON insbrok_policies (tenant_id, status);
CREATE INDEX idx_insbrok_policies_expiry ON insbrok_policies (expiry_date) WHERE status IN ('ACTIVE','LAPSED');
CREATE INDEX idx_insbrok_policies_renewal_of ON insbrok_policies (renewal_of_policy_id);

CREATE TABLE insbrok_commission_invoices (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES insbrok_clients (id),
    policy_id       UUID NOT NULL REFERENCES insbrok_policies (id),
    invoice_number  VARCHAR(50) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    invoice_date    DATE NOT NULL,
    due_date        DATE NOT NULL,
    subtotal        NUMERIC(15,2) NOT NULL,
    vat_amount      NUMERIC(15,2) NOT NULL,
    total           NUMERIC(15,2) NOT NULL,
    amount_paid     NUMERIC(15,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | SENT | PARTIAL | PAID
    sent_at         TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_insbrok_commission_invoices_tenant ON insbrok_commission_invoices (tenant_id);
CREATE INDEX idx_insbrok_commission_invoices_client ON insbrok_commission_invoices (tenant_id, client_id);
CREATE UNIQUE INDEX uq_insbrok_commission_invoices_policy ON insbrok_commission_invoices (policy_id);

-- ============================================================================
-- NOT INCLUDED — copy from your own checkout before applying:
--   INSERT INTO module_catalogue (...) VALUES ('insurancebrokerage', ...);
--   INSERT INTO permission (...) VALUES ('INSURANCEBROKERAGE_READ', ...),
--                                        ('INSURANCEBROKERAGE_MANAGE', ...),
--                                        ('INSURANCEBROKERAGE_ADMIN', ...);
-- ============================================================================
