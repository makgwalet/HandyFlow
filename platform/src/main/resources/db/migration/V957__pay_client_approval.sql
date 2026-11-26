-- V957: client approval of pay runs (phase 5). A client can be set to need its own sign-off before a run is finalised.
-- APPROVED (by the bureau) -> AWAITING_CLIENT -> CLIENT_APPROVED -> FINALISED. The client can decline, which reopens the run
-- to DRAFT with their reason. The approver is a portal user, so there is no foreign key to the bureau's users.
ALTER TABLE pay_clients ADD COLUMN client_approval_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pay_runs
    ADD COLUMN sent_to_client_at     TIMESTAMPTZ,
    ADD COLUMN sent_to_client_by     UUID,
    ADD COLUMN client_approved_by    UUID,
    ADD COLUMN client_approved_at    TIMESTAMPTZ,
    ADD COLUMN client_declined_at    TIMESTAMPTZ,
    ADD COLUMN client_decline_reason VARCHAR(500);

ALTER TABLE pay_runs DROP CONSTRAINT IF EXISTS pay_runs_status_check;
ALTER TABLE pay_runs ADD CONSTRAINT pay_runs_status_check
    CHECK (status IN ('DRAFT','CALCULATED','APPROVED','AWAITING_CLIENT','CLIENT_APPROVED','FINALISED','REVERSED'));
