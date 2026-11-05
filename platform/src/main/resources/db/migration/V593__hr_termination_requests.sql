-- Ending someone's employment is decided by a second HR manager. The request waits here; the employee is untouched
-- until it is approved. A business with a single HR manager ends employment directly and the exception is recorded
-- in audit_events (SOLE_APPROVER_SELF_APPROVAL), so no request row is written for it.
CREATE TABLE IF NOT EXISTS hr_termination_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    employee_id       UUID         NOT NULL,
    end_date          DATE         NOT NULL,
    reason            VARCHAR(200) NOT NULL,
    end_type          VARCHAR(40),
    notice_given_on   DATE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_by      UUID         NOT NULL,
    requested_at      TIMESTAMP    NOT NULL DEFAULT now(),
    decided_by        UUID,
    decided_at        TIMESTAMP,
    decision_note     VARCHAR(500),
    CONSTRAINT chk_hr_termination_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_hr_termination_requests_pending ON hr_termination_requests (tenant_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_hr_termination_requests_employee ON hr_termination_requests (tenant_id, employee_id);
