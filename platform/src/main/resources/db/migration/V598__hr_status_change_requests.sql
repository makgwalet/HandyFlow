-- Employment-impacting status changes (suspension, reinstatement) need independent approval (owner decision of 5 Nov 2026).
-- Termination has its own table (V593) and keeps its two-person approval.
-- An EMERGENCY security or safety suspension takes effect at once (status APPLIED, emergency = true) and must be reviewed
-- by someone other than the person who made it by review_due_at (one business day). Rows are never deleted.
CREATE TABLE IF NOT EXISTS hr_status_change_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    employee_id     UUID         NOT NULL,
    from_status     VARCHAR(20)  NOT NULL,
    to_status       VARCHAR(20)  NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    emergency       BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    requested_by    UUID,
    requested_at    TIMESTAMP    NOT NULL DEFAULT now(),
    decided_by      UUID,
    decided_at      TIMESTAMP,
    decision_note   VARCHAR(1000),
    applied_at      TIMESTAMP,
    sole_exception  BOOLEAN      NOT NULL DEFAULT FALSE,
    review_due_at   TIMESTAMP,
    review_outcome  VARCHAR(12),
    reviewed_by     UUID,
    reviewed_at     TIMESTAMP,
    review_note     VARCHAR(1000),
    CONSTRAINT chk_hr_status_change_status CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_hr_status_change_to CHECK (to_status IN ('SUSPENDED', 'ACTIVE')),
    CONSTRAINT chk_hr_status_change_review CHECK (review_outcome IS NULL OR review_outcome IN ('CONFIRMED', 'REVERSED')),
    CONSTRAINT chk_hr_status_change_emergency CHECK (NOT emergency OR (to_status = 'SUSPENDED' AND review_due_at IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_status_change_open ON hr_status_change_requests (tenant_id, employee_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_hr_status_change_review ON hr_status_change_requests (tenant_id, review_due_at) WHERE emergency AND review_outcome IS NULL;
