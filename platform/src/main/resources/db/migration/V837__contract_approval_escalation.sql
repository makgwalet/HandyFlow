-- Approval Escalation Engine for contracting.
-- Reminds the people who can decide a waiting approval, escalates it when it has waited too long, lets an administrator reassign it,
-- skips people who are out of office, and measures how long decisions take. It NEVER decides anything: it only adds reminders,
-- escalation marks and audit records. Off until an administrator switches it on, so nothing changes by itself.

-- One row per company. No row = the defaults in EscalationSettings (switched off).
CREATE TABLE contract_escalation_settings (
    tenant_id             UUID PRIMARY KEY,
    enabled               BOOLEAN     NOT NULL DEFAULT FALSE,
    -- When it was last switched on. Time before this does not count, so switching it on does not flood people about old requests.
    enabled_since         TIMESTAMPTZ,
    reminder1_hours       INTEGER     NOT NULL DEFAULT 24,
    reminder2_hours       INTEGER     NOT NULL DEFAULT 48,
    escalate_hours        INTEGER     NOT NULL DEFAULT 72,
    -- BUSINESS_HOURS: the waiting clock only runs in working hours, on working days that are not holidays or closure days.
    -- CALENDAR: plain elapsed hours.
    clock_mode            VARCHAR(14) NOT NULL DEFAULT 'BUSINESS_HOURS',
    work_start            TIME        NOT NULL DEFAULT '08:00',
    work_end              TIME        NOT NULL DEFAULT '17:00',
    work_days             VARCHAR(27) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    skip_public_holidays  BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Comma-separated user ids to tell on escalation. Empty = the contracts administrators.
    escalation_user_ids   VARCHAR(1000) NOT NULL DEFAULT '',
    updated_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_escalation_hours  CHECK (reminder1_hours >= 1 AND reminder1_hours < reminder2_hours AND reminder2_hours < escalate_hours AND escalate_hours <= 720),
    CONSTRAINT chk_escalation_clock  CHECK (clock_mode IN ('BUSINESS_HOURS', 'CALENDAR')),
    CONSTRAINT chk_escalation_window CHECK (work_start < work_end)
);

-- Days this company is closed on top of the public holidays (year-end shutdown, a company day).
CREATE TABLE contract_closure_days (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    closed_on   DATE        NOT NULL,
    reason      VARCHAR(200),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_closure_day UNIQUE (tenant_id, closed_on)
);

-- A person who cannot approve for a while. Their waiting approvals go to the delegate, or to the escalation contacts if there is none.
CREATE TABLE contract_approver_absences (
    id                UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    from_date         DATE        NOT NULL,
    to_date           DATE        NOT NULL,
    delegate_user_id  UUID,
    reason            VARCHAR(200),
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at      TIMESTAMPTZ,
    cancelled_by      UUID,
    CONSTRAINT chk_absence_window   CHECK (to_date >= from_date),
    CONSTRAINT chk_absence_delegate CHECK (delegate_user_id IS NULL OR delegate_user_id <> user_id)
);
CREATE INDEX idx_contract_absences_tenant ON contract_approver_absences (tenant_id, from_date, to_date) WHERE cancelled_at IS NULL;

-- Where each approval stands. Assignment only decides who is reminded first; it does not stop anyone else with the right role from deciding.
ALTER TABLE contract_approvals ADD COLUMN assigned_to         UUID;
ALTER TABLE contract_approvals ADD COLUMN assigned_by         UUID;
ALTER TABLE contract_approvals ADD COLUMN assigned_at         TIMESTAMPTZ;
-- 0 nothing sent, 1 first reminder, 2 second reminder, 3 escalated. Only ever goes up.
ALTER TABLE contract_approvals ADD COLUMN escalation_stage    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE contract_approvals ADD COLUMN escalation_stage_at TIMESTAMPTZ;
ALTER TABLE contract_approvals ADD CONSTRAINT chk_contract_approval_stage CHECK (escalation_stage BETWEEN 0 AND 3);
CREATE INDEX idx_contract_approvals_waiting ON contract_approvals (tenant_id, requested_at) WHERE status = 'PENDING';
