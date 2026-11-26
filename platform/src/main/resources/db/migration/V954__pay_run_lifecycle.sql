-- V954: pay run lifecycle (phase 2; audit D5).
-- DRAFT -> CALCULATED -> APPROVED -> FINALISED, and FINALISED -> REVERSED. Who did each step and when is kept on the run.
-- Runs that were PROCESSED under the old two-state flow had their payslips available and possibly emailed, so they become FINALISED;
-- nobody approved them, so approved_by stays empty for them.
ALTER TABLE pay_runs
    ADD COLUMN calculated_by   UUID,
    ADD COLUMN approved_by     UUID,
    ADD COLUMN approved_at     TIMESTAMPTZ,
    ADD COLUMN finalised_by    UUID,
    ADD COLUMN finalised_at    TIMESTAMPTZ,
    ADD COLUMN reversed_by     UUID,
    ADD COLUMN reversed_at     TIMESTAMPTZ,
    ADD COLUMN reversal_reason VARCHAR(500);

UPDATE pay_runs SET status = 'FINALISED', finalised_at = processed_at WHERE status = 'PROCESSED';

ALTER TABLE pay_runs DROP CONSTRAINT IF EXISTS pay_runs_status_check;
ALTER TABLE pay_runs ADD CONSTRAINT pay_runs_status_check
    CHECK (status IN ('DRAFT','CALCULATED','APPROVED','FINALISED','REVERSED'));
