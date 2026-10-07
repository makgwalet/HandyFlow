-- Whole days when the whole clinic takes no bookings (public holidays, a staff day, maintenance).
-- Days are calendar dates in clinic time (Africa/Johannesburg), both ends inclusive. Cancelled, never deleted.
-- Nothing is pre-filled: the clinic enters its own closures.
CREATE TABLE IF NOT EXISTS clinic_closures (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    first_day    DATE         NOT NULL,
    last_day     DATE         NOT NULL,
    reason       VARCHAR(200),
    created_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMP,
    cancelled_by UUID,
    CONSTRAINT chk_clinic_closures_order CHECK (last_day >= first_day)
);
CREATE INDEX IF NOT EXISTS idx_clinic_closures_lookup
    ON clinic_closures (tenant_id, first_day, last_day)
    WHERE cancelled_at IS NULL;
