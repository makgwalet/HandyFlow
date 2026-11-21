-- SEC chunk 14: ammunition accounting on firearm issue/return, and the SAPS overdue alert marker.
ALTER TABLE security_armoury_logs
    ADD COLUMN IF NOT EXISTS ammo_calibre            VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ammo_rounds             INTEGER CHECK (ammo_rounds IS NULL OR ammo_rounds >= 0),
    ADD COLUMN IF NOT EXISTS ammo_unaccounted_reason TEXT;

COMMENT ON COLUMN security_armoury_logs.ammo_rounds IS
    'ISSUE: rounds handed out with the firearm. RETURN: rounds handed back. NULL = not recorded.';

ALTER TABLE security_armoury
    ADD COLUMN IF NOT EXISTS saps_overdue_alerted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_armoury_lost_no_case
    ON security_armoury (saps_report_due_at)
    WHERE status = 'LOST' AND saps_case_number IS NULL;
