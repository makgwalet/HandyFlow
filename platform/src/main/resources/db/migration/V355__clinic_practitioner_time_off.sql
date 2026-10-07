-- Time a practitioner is not available: leave, training, a standing lunch hour, a day at another site.
-- Times are stored the same way as clinic_appointments.scheduled_at (TIMESTAMP), so the two can be compared
-- directly. A block that no longer applies is cancelled, not deleted, so the record shows who was away when.
CREATE TABLE IF NOT EXISTS clinic_practitioner_time_off (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    practitioner_id UUID         NOT NULL REFERENCES clinic_practitioners(id),
    starts_at       TIMESTAMP    NOT NULL,
    ends_at         TIMESTAMP    NOT NULL,
    reason          VARCHAR(200),
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    cancelled_at    TIMESTAMP,
    cancelled_by    UUID,
    CONSTRAINT chk_clinic_time_off_order CHECK (ends_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_clinic_time_off_lookup
    ON clinic_practitioner_time_off (tenant_id, practitioner_id, starts_at)
    WHERE cancelled_at IS NULL;
