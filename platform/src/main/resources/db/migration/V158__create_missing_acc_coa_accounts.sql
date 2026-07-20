-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Fixes a real startup failure: "Schema-validation: missing table
-- [acc_coa_accounts]".
--
-- acc_coa_accounts IS defined in V58__accountant_module.sql (confirmed
-- by reading that file directly), but Flyway reports schema version 157
-- as already up to date — meaning it believes V58 already ran
-- successfully. If the table's CREATE TABLE statement were present in
-- V58 when it actually executed against this database, the table would
-- exist. The most likely explanation: the V58 file was edited to add
-- this table's definition sometime AFTER V58 had already been applied
-- and recorded in Flyway's history on this specific database — Flyway
-- doesn't re-run an already-applied migration just because its file
-- changed later.
--
-- Cross-check: acc_journals.period_id has a
-- "NOT NULL REFERENCES acc_periods(id)" constraint, and journals
-- already work in this app (created, reviewed, posted) — that FK
-- constraint couldn't exist if acc_periods were missing too. So
-- acc_periods is guarded here with IF NOT EXISTS as a zero-cost safety
-- net, not because there's direct evidence it's also broken — only
-- acc_coa_accounts is confirmed missing by the actual error.
--
-- Column definitions copied exactly from the real V58 file — not
-- re-derived or guessed.

CREATE TABLE IF NOT EXISTS acc_coa_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    account_code    VARCHAR(20) NOT NULL,
    account_name    VARCHAR(200) NOT NULL,
    account_type    VARCHAR(20) NOT NULL
        CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    sub_type        VARCHAR(50),
    vat_applicable  BOOLEAN NOT NULL DEFAULT false,
    vat_type        VARCHAR(10)
        CHECK (vat_type IN ('OUTPUT','INPUT','EXEMPT','ZERO_RATED') OR vat_type IS NULL),
    tax_schedule    VARCHAR(30),
    parent_id       UUID REFERENCES acc_coa_accounts(id),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, account_code)
);

CREATE TABLE IF NOT EXISTS acc_periods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    period_year     INTEGER NOT NULL,
    period_month    INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    status          VARCHAR(15) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLOSED','LOCKED')),
    closed_at       TIMESTAMP,
    closed_by       UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, period_year, period_month)
);