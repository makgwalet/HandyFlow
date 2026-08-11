-- V210__add_shift_supervisor_actions.sql
-- (Rename V210 to your actual next migration number before applying.)
--
-- Two things this migration enables:
--
-- 1. EDGE-TRIGGERED ALERT DEDUP
--    NoShowAlertScheduler runs every 5 minutes and previously had no memory of
--    what it already alerted on -- every overdue shift got a fresh notification
--    on every run until it resolved. At 1000+ guards with clustered shift-change
--    times this could produce hundreds of emails/SMS in a single morning.
--    These three columns let the scheduler mark "already alerted" per condition,
--    same edge-trigger pattern already proven in the CRM module
--    (CustomerConsent.expiryReminderSentAt) and this module's own
--    PsiraComplianceScheduler / ArmouryComplianceScheduler.
--
-- 2. SUPERVISOR INTERRUPT TRACKING
--    Three new supervisor-only actions on a shift, each independently auditable
--    via security_audit_log (entity_type=SHIFT):
--      - Dismiss a no-show alert (guard called in sick, etc.) -- no status change.
--      - Force-close overtime -- completes an ACTIVE shift that's run long,
--        bypassing scan-count enforcement (the guard isn't there to scan anymore).
--      - Pull a guard from site mid-shift -- a genuine supervisor-initiated
--        interrupt of an ACTIVE shift, distinct from the guard's own complete().

ALTER TABLE security_shifts
    ADD COLUMN late_alert_sent_at      TIMESTAMPTZ,
    ADD COLUMN no_show_alert_sent_at   TIMESTAMPTZ,
    ADD COLUMN overtime_alert_sent_at  TIMESTAMPTZ,

    ADD COLUMN no_show_dismissed_at    TIMESTAMPTZ,
    ADD COLUMN no_show_dismissed_by    UUID,
    ADD COLUMN no_show_dismiss_reason  VARCHAR(500),

    ADD COLUMN overtime_closed_at      TIMESTAMPTZ,
    ADD COLUMN overtime_closed_by      UUID,
    ADD COLUMN overtime_close_reason   VARCHAR(500),

    ADD COLUMN pulled_at               TIMESTAMPTZ,
    ADD COLUMN pulled_by               UUID,
    ADD COLUMN pull_reason             VARCHAR(500);

COMMENT ON COLUMN security_shifts.late_alert_sent_at IS
    'Edge-trigger: set once this shift has been included in a LATE digest notification. Prevents re-alerting on every 5-minute scheduler run.';
COMMENT ON COLUMN security_shifts.no_show_alert_sent_at IS
    'Edge-trigger for NO_SHOW digest inclusion. Independent of late_alert_sent_at -- a shift can cross the LATE threshold first, then separately cross the NO_SHOW threshold.';
COMMENT ON COLUMN security_shifts.overtime_alert_sent_at IS
    'Edge-trigger for OVERTIME digest inclusion.';
COMMENT ON COLUMN security_shifts.pulled_at IS
    'Set when a supervisor interrupts an ACTIVE shift mid-shift ("pull from site"). Distinct from the guard-driven complete() path: skips scan-count enforcement, requires a reason, and is always audited.';

-- Index to keep the scheduler's "not yet alerted" queries cheap at scale.
CREATE INDEX idx_security_shifts_no_show_alert_pending
    ON security_shifts (tenant_id, status, start_at)
    WHERE no_show_alert_sent_at IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_security_shifts_overtime_alert_pending
    ON security_shifts (tenant_id, status, end_at)
    WHERE overtime_alert_sent_at IS NULL AND deleted_at IS NULL;