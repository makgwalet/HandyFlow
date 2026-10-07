-- Weekly working hours for a practitioner. A practitioner with NO rows here is not restricted (unchanged behaviour);
-- once rows exist, bookings must fit inside one of the windows for that weekday (clinic time, Africa/Johannesburg).
-- A weekday may have several windows (for example a lunch break). day_of_week: 1 = Monday ... 7 = Sunday.
CREATE TABLE IF NOT EXISTS clinic_practitioner_working_hours (
    id              UUID PRIMARY KEY,
    tenant_id       UUID      NOT NULL,
    practitioner_id UUID      NOT NULL REFERENCES clinic_practitioners(id),
    day_of_week     SMALLINT  NOT NULL,
    from_time       TIME      NOT NULL,
    to_time         TIME      NOT NULL,
    updated_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_clinic_wh_day   CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_clinic_wh_order CHECK (to_time > from_time)
);
CREATE INDEX IF NOT EXISTS idx_clinic_wh_lookup
    ON clinic_practitioner_working_hours (tenant_id, practitioner_id, day_of_week);
