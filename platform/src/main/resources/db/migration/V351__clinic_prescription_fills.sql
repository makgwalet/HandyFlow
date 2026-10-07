-- Repeat handling. A prescription authorises 1 original fill plus `repeats` further fills. Each fill is logged.
-- `dispensed` now means every authorised fill has been used (the active-prescriptions list keeps scripts with repeats left);
-- `dispensed_at` stays the time of the first fill.
ALTER TABLE clinic_prescriptions ADD COLUMN IF NOT EXISTS fills_used INT NOT NULL DEFAULT 0;

-- Prescriptions already marked dispensed before this change were a single fill.
UPDATE clinic_prescriptions SET fills_used = 1 WHERE dispensed AND fills_used = 0;

CREATE TABLE IF NOT EXISTS clinic_prescription_fills (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants(id),
    prescription_id  UUID         NOT NULL REFERENCES clinic_prescriptions(id),
    fill_number      INT          NOT NULL,
    quantity         INT,
    dispensed_by     UUID         REFERENCES users(id),
    note             TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_prescription_fill UNIQUE (prescription_id, fill_number),
    CONSTRAINT chk_fill_number CHECK (fill_number >= 1),
    CONSTRAINT chk_fill_quantity CHECK (quantity IS NULL OR quantity > 0)
);
CREATE INDEX IF NOT EXISTS idx_prescription_fills_rx ON clinic_prescription_fills (prescription_id);
