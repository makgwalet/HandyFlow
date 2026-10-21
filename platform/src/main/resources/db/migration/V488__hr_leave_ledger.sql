-- HR slice 4: leave as a ledger.
--
-- Until now a balance was a row that was changed in place (entitled, taken, pending), created once when the employee was
-- registered. From the next January no row existed, and with no row the balance check was skipped. This migration:
--   * adds an append-only ledger of leave movements (accruals, carry forward, adjustments, reservations, leave taken),
--   * rebuilds opening balances and leave taken from the old tables so that nobody's balance changes today,
--   * adds the permissions to approve leave and to adjust balances, and
--   * records on each request whether it was a half day and whether a medical certificate was supplied.
-- The old hr_leave_balances table is no longer written; it is kept as the record of what was migrated.

-- ── Permissions ─────────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEAVE_APPROVE', 'Approve, reject and cancel leave requests'),
    (gen_random_uuid(), 'LEAVE_ADJUST',  'Record adjustments to leave balances and cancel leave that has already started')
ON CONFLICT (name) DO NOTHING;

-- Whoever could approve leave before (HR_MANAGE or USER_UPDATE) still can.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name IN ('HR_MANAGE', 'USER_UPDATE')
CROSS JOIN permissions p
WHERE p.name = 'LEAVE_APPROVE'
ON CONFLICT DO NOTHING;

-- Adjusting balances is narrower: HR managers only.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name = 'LEAVE_ADJUST'
ON CONFLICT DO NOTHING;

-- ── Settings: when the ledger starts ───────────────────────────────────────────────────────────
-- Annual leave is earned from the later of the employee's start date and this date, so turning the ledger on does not
-- suddenly grant years of back-dated leave.
CREATE TABLE hr_leave_settings (
    tenant_id          UUID PRIMARY KEY REFERENCES tenants(id),
    ledger_start_date  DATE NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);
INSERT INTO hr_leave_settings (tenant_id, ledger_start_date)
SELECT id, date_trunc('year', now())::date FROM tenants;

-- ── Requests ────────────────────────────────────────────────────────────────────────────────────
ALTER TABLE hr_leave_requests
    ADD COLUMN half_day            BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN medical_certificate BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cancelled_by        UUID,
    ADD COLUMN cancelled_at        TIMESTAMP,
    ADD COLUMN cancel_reason       VARCHAR(300),
    ADD COLUMN version             BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_hr_leave_requests_overlap ON hr_leave_requests (tenant_id, employee_id, start_date, end_date)
    WHERE status IN ('PENDING', 'APPROVED');

-- ── The ledger ──────────────────────────────────────────────────────────────────────────────────
-- days is signed: grants are positive, leave and reservations negative. Rows are never changed or deleted; a mistake is
-- corrected by a later row.
CREATE TABLE hr_leave_ledger (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    employee_id  UUID         NOT NULL REFERENCES hr_employees(id),
    leave_type   VARCHAR(30)  NOT NULL
        CHECK (leave_type IN ('ANNUAL','SICK','FAMILY_RESPONSIBILITY','MATERNITY','PATERNITY','UNPAID','STUDY')),
    entry_date   DATE         NOT NULL,
    kind         VARCHAR(14)  NOT NULL
        CHECK (kind IN ('OPENING','ACCRUAL','CARRY_FORWARD','ADJUSTMENT','RESERVED','RELEASED','TAKEN','REVERSAL')),
    days         NUMERIC(7,2) NOT NULL,
    request_id   UUID REFERENCES hr_leave_requests(id),
    period_key   VARCHAR(80),
    note         VARCHAR(300),
    created_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hr_leave_ledger_employee ON hr_leave_ledger (tenant_id, employee_id, leave_type, entry_date);
CREATE INDEX idx_hr_leave_ledger_request  ON hr_leave_ledger (request_id) WHERE request_id IS NOT NULL;
-- A movement with a key can be posted once: running accrual twice, or approving twice, cannot double-post.
CREATE UNIQUE INDEX uq_hr_leave_ledger_key ON hr_leave_ledger (tenant_id, employee_id, leave_type, kind, period_key)
    WHERE period_key IS NOT NULL;

CREATE FUNCTION hr_leave_ledger_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The leave ledger cannot be changed or deleted. Post a correcting entry instead.';
END;
$$ LANGUAGE plpgsql;
-- Created after the opening balances below are loaded.

-- ── Opening balances from the old tables ───────────────────────────────────────────────────────
INSERT INTO hr_leave_ledger (tenant_id, employee_id, leave_type, entry_date, kind, days, period_key, note)
SELECT b.tenant_id, b.employee_id, b.leave_type, make_date(b.leave_year, 1, 1), 'OPENING', b.entitled_days,
       'opening:' || b.leave_year, 'Entitlement from the previous leave balance table'
FROM hr_leave_balances b
WHERE b.entitled_days <> 0;

-- Leave already taken, one row per approved request, dated its first day.
INSERT INTO hr_leave_ledger (tenant_id, employee_id, leave_type, entry_date, kind, days, request_id, period_key, note)
SELECT r.tenant_id, r.employee_id, r.leave_type, r.start_date, 'TAKEN', -r.days_requested, r.id,
       'tak:' || r.id || ':' || r.start_date, 'Approved before the leave ledger existed'
FROM hr_leave_requests r
WHERE r.status = 'APPROVED';

-- Where the old taken figure disagreed with the approved requests, a correcting entry makes the ledger equal the old
-- balance, so nobody's available days change on the day this runs.
INSERT INTO hr_leave_ledger (tenant_id, employee_id, leave_type, entry_date, kind, days, period_key, note)
SELECT b.tenant_id, b.employee_id, b.leave_type, make_date(b.leave_year, 1, 1), 'ADJUSTMENT',
       COALESCE(t.approved_days, 0) - b.taken_days, 'recon:' || b.leave_year,
       'Reconciles to the taken days on the previous balance table'
FROM hr_leave_balances b
LEFT JOIN (
    SELECT tenant_id, employee_id, leave_type, EXTRACT(YEAR FROM start_date)::int AS yr, SUM(days_requested) AS approved_days
    FROM hr_leave_requests WHERE status = 'APPROVED' GROUP BY tenant_id, employee_id, leave_type, EXTRACT(YEAR FROM start_date)
) t ON t.tenant_id = b.tenant_id AND t.employee_id = b.employee_id AND t.leave_type = b.leave_type AND t.yr = b.leave_year
WHERE COALESCE(t.approved_days, 0) <> b.taken_days;

-- Requests still waiting hold their days.
INSERT INTO hr_leave_ledger (tenant_id, employee_id, leave_type, entry_date, kind, days, request_id, period_key, note)
SELECT r.tenant_id, r.employee_id, r.leave_type, r.start_date, 'RESERVED', -r.days_requested, r.id,
       'res:' || r.id || ':' || r.start_date, 'Waiting for approval before the leave ledger existed'
FROM hr_leave_requests r
WHERE r.status = 'PENDING';

CREATE TRIGGER hr_leave_ledger_no_update BEFORE UPDATE OR DELETE ON hr_leave_ledger
    FOR EACH ROW EXECUTE FUNCTION hr_leave_ledger_immutable();
