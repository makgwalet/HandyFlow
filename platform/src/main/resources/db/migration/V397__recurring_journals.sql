-- Finance F17: recurring journals, accruals (reversed next month) and prepayments (spread evenly).
-- The system only ever makes DRAFT journals, in the name of whoever set the schedule up; a different person posts them.

CREATE TABLE IF NOT EXISTS acc_recurring_journals (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    name                VARCHAR(150) NOT NULL,
    kind                VARCHAR(12) NOT NULL,
    frequency           VARCHAR(10) NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE,
    runs_total          INTEGER,
    runs_done           INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    description         VARCHAR(255) NOT NULL,
    reference           VARCHAR(100),
    lines               TEXT,
    total_amount        NUMERIC(15,2),
    expense_account_id  UUID,
    prepaid_account_id  UUID,
    last_run_at         TIMESTAMPTZ,
    last_error          TEXT,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT acc_rj_kind_chk CHECK (kind IN ('RECURRING','ACCRUAL','PREPAYMENT')),
    CONSTRAINT acc_rj_freq_chk CHECK (frequency IN ('MONTHLY','QUARTERLY','ANNUALLY')),
    CONSTRAINT acc_rj_status_chk CHECK (status IN ('ACTIVE','PAUSED','ENDED'))
);

CREATE INDEX IF NOT EXISTS idx_acc_rj_tenant ON acc_recurring_journals (tenant_id, status);

-- One row per occurrence made; the unique key means a repeated run cannot make the same occurrence twice.
CREATE TABLE IF NOT EXISTS acc_recurring_journal_runs (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    recurring_id         UUID NOT NULL REFERENCES acc_recurring_journals (id),
    run_index            INTEGER NOT NULL,
    run_date             DATE NOT NULL,
    journal_id           UUID NOT NULL,
    reversal_journal_id  UUID,
    amount               NUMERIC(15,2),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT acc_rj_runs_uq UNIQUE (recurring_id, run_index)
);

CREATE INDEX IF NOT EXISTS idx_acc_rj_runs_rec ON acc_recurring_journal_runs (tenant_id, recurring_id);
