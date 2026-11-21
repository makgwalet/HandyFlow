-- SEC chunk 13: optimistic locking on the firearm register, and the lost-firearm SAPS workflow.
ALTER TABLE security_armoury
    ADD COLUMN IF NOT EXISTS version             BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lost_reported_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS saps_case_number    VARCHAR(60),
    ADD COLUMN IF NOT EXISTS saps_report_due_at  TIMESTAMPTZ;

-- Firearms already marked LOST: the clock starts from when they were last updated.
UPDATE security_armoury
   SET lost_reported_at   = updated_at,
       saps_report_due_at = updated_at + INTERVAL '24 hours'
 WHERE status = 'LOST' AND lost_reported_at IS NULL;

COMMENT ON COLUMN security_armoury.saps_report_due_at IS
    'Lost or stolen firearms must be reported to SAPS within 24 hours; set when the loss is reported.';
