-- ============================================================================
-- Debt Collection (internal) module — baseline schema + catalogue seed.
--
-- *** VERSION NUMBER NOT CONFIRMED — READ BEFORE APPLYING ***
-- V256 follows directly from V255 (legalcompliance, this same engagement,
-- itself unconfirmed against the live flyway_schema_history). If
-- V255__legalcompliance_module.sql has already been renamed after checking
-- the real next-free version number, renumber this file to match — it must
-- immediately follow whatever legalcompliance actually lands on.
--
-- *** MODULE CATALOGUE / PERMISSION SEED SHAPE — SAME CAVEAT AS V255 ***
-- Mirrors V255's own INSERT shape (module_catalogue + permissions +
-- role_permissions), inferred from AdminLookupService.createModule()'s
-- confirmed logic, not copied from a directly-verified baseline-module seed
-- migration. Same "worth a diff before shipping" flag as V255.
--
-- Module key "debtcollection" (lowercase, no separator) matches the
-- confirmed convention (crm, security, hr, payrollbureau, contracting,
-- legalcompliance). Permissions DEBTCOLLECTION_READ/_MANAGE/_ADMIN.
-- ============================================================================

-- ── Debt collection cases ───────────────────────────────────────────────────

CREATE TABLE debtcollection_cases (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    case_number             VARCHAR(30) NOT NULL,
    customer_id             UUID,
    debtor_name             VARCHAR(255) NOT NULL,
    debtor_email            VARCHAR(255),
    debtor_phone            VARCHAR(50),
    status                  VARCHAR(25) NOT NULL,
    total_outstanding       NUMERIC(15,2) NOT NULL,
    opened_date             DATE NOT NULL,
    closed_date             DATE,
    closure_reason          VARCHAR(25),
    assigned_to_user_id     UUID,
    assigned_to_user_name   VARCHAR(255),
    linked_contract_id      UUID,
    last_contact_date       DATE,
    next_action_date        DATE,
    write_off_amount        NUMERIC(15,2),
    notes                   TEXT,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ,
    deleted_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_dc_case_number UNIQUE (tenant_id, case_number)
);

CREATE INDEX idx_dc_cases_tenant ON debtcollection_cases (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_dc_cases_status ON debtcollection_cases (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_dc_cases_customer ON debtcollection_cases (tenant_id, customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_dc_cases_next_action ON debtcollection_cases (tenant_id, next_action_date) WHERE deleted_at IS NULL;

-- Element collection backing DebtCollectionCase.linkedInvoiceIds — id references into `invoicing`'s own invoices table, never a foreign key across module schemas (this codebase does not enforce cross-module FKs, consistent with facade-mediated access).
CREATE TABLE debtcollection_case_invoices (
    case_id     UUID NOT NULL REFERENCES debtcollection_cases (id),
    invoice_id  UUID NOT NULL,
    PRIMARY KEY (case_id, invoice_id)
);

-- ── Collection contact log (append-only compliance trail) ─────────────────

CREATE TABLE debtcollection_contact_logs (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    case_id                     UUID NOT NULL REFERENCES debtcollection_cases (id),
    contact_date                DATE NOT NULL,
    contact_method              VARCHAR(20) NOT NULL,
    outcome                     VARCHAR(25) NOT NULL,
    notes                       TEXT,
    promised_payment_date       DATE,
    promised_payment_amount     NUMERIC(15,2),
    recorded_by_user_id         UUID NOT NULL,
    recorded_by_user_name       VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dc_contact_logs_case ON debtcollection_contact_logs (tenant_id, case_id);

-- ── Payment plans ───────────────────────────────────────────────────────────

CREATE TABLE debtcollection_payment_plans (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    case_id                 UUID NOT NULL REFERENCES debtcollection_cases (id),
    status                  VARCHAR(15) NOT NULL,
    total_agreed_amount     NUMERIC(15,2) NOT NULL,
    installment_amount      NUMERIC(15,2) NOT NULL,
    frequency               VARCHAR(15) NOT NULL,
    start_date              DATE NOT NULL,
    next_due_date           DATE,
    number_of_installments  INTEGER NOT NULL,
    installments_paid       INTEGER NOT NULL DEFAULT 0,
    notes                   TEXT,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dc_payment_plans_case ON debtcollection_payment_plans (tenant_id, case_id);
CREATE INDEX idx_dc_payment_plans_due ON debtcollection_payment_plans (next_due_date) WHERE status = 'ACTIVE';

-- ── Module catalogue + permission seed ─────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('debtcollection', 'Debt Collection',
        'Formal, staff-managed collection cases for the business''s own overdue invoices — contact log, payment plans, write-off/legal handover, on top of invoicing''s automatic reminders.',
        0, 'Banknote', 'FINANCE', 999, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'DEBTCOLLECTION_READ',   'View Debt Collection cases'),
    (gen_random_uuid(), 'DEBTCOLLECTION_MANAGE', 'Create and manage Debt Collection cases'),
    (gen_random_uuid(), 'DEBTCOLLECTION_ADMIN',  'Full administrative access to Debt Collection')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('DEBTCOLLECTION_READ', 'DEBTCOLLECTION_MANAGE', 'DEBTCOLLECTION_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- NOTE — monthly_price left at 0, same open-pricing-track caveat as V255.
