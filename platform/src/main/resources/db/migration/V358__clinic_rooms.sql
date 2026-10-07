-- Consulting rooms. An appointment may name a room; a room can hold one live booking at a time.
-- Rooms are switched off (active = false), never deleted, so past appointments keep their room name.
-- Nothing is pre-filled: the clinic adds its own rooms.
CREATE TABLE IF NOT EXISTS clinic_rooms (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    name       VARCHAR(60)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_clinic_rooms_name CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_rooms_name ON clinic_rooms (tenant_id, lower(name));

ALTER TABLE clinic_appointments ADD COLUMN IF NOT EXISTS room_id UUID REFERENCES clinic_rooms(id);
CREATE INDEX IF NOT EXISTS idx_clinic_appt_room ON clinic_appointments (tenant_id, room_id, scheduled_at) WHERE room_id IS NOT NULL;
