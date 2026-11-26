-- Training provider: timetable overlap guard.
-- 1. Slots get real timestamps with a time zone (starts_at, ends_at, time_zone). slot_date, start_time and end_time stay as the local wall-clock
--    values the screens use; the service writes both sets together. Existing rows are back-filled from their date and times in the
--    zone they were entered in, Africa/Johannesburg.
-- 2. Overrides: a clash with another booking is refused unless an administrator approves it with a written reason. Each approved clash is one
--    row here: what was booked, what it clashed with, who approved it, when and why. Rows are only added, never changed or removed.

ALTER TABLE trainprov_timetable_slots ADD COLUMN time_zone VARCHAR(60) NOT NULL DEFAULT 'Africa/Johannesburg';
ALTER TABLE trainprov_timetable_slots ADD COLUMN starts_at TIMESTAMPTZ;
ALTER TABLE trainprov_timetable_slots ADD COLUMN ends_at TIMESTAMPTZ;
UPDATE trainprov_timetable_slots
   SET starts_at = (slot_date + start_time) AT TIME ZONE time_zone,
       ends_at   = (slot_date + end_time)   AT TIME ZONE time_zone;
ALTER TABLE trainprov_timetable_slots ALTER COLUMN starts_at SET NOT NULL;
ALTER TABLE trainprov_timetable_slots ALTER COLUMN ends_at SET NOT NULL;
ALTER TABLE trainprov_timetable_slots ADD CONSTRAINT ck_trainprov_slot_instants CHECK (ends_at > starts_at);
ALTER TABLE trainprov_timetable_slots ADD CONSTRAINT ck_trainprov_slot_zone CHECK (COALESCE(char_length(btrim(time_zone)), 0) >= 3);
CREATE INDEX idx_trainprov_slot_venue_at ON trainprov_timetable_slots (tenant_id, venue_id, starts_at, ends_at) WHERE venue_id IS NOT NULL AND status = 'SCHEDULED';
CREATE INDEX idx_trainprov_slot_person_at ON trainprov_timetable_slots (tenant_id, practitioner_id, starts_at, ends_at) WHERE practitioner_id IS NOT NULL AND status = 'SCHEDULED';
CREATE INDEX idx_trainprov_slot_cohort_at ON trainprov_timetable_slots (tenant_id, cohort_id, starts_at, ends_at) WHERE status = 'SCHEDULED';

CREATE TABLE trainprov_timetable_overrides (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    slot_id           UUID NOT NULL REFERENCES trainprov_timetable_slots(id),
    resource_type     VARCHAR(20) NOT NULL CHECK (resource_type IN ('VENUE', 'PRACTITIONER', 'COHORT', 'LEARNER', 'EQUIPMENT', 'ROOM')),
    resource_id       UUID NOT NULL,
    resource_label    VARCHAR(200) NOT NULL,
    clashing_slot_id  UUID NOT NULL REFERENCES trainprov_timetable_slots(id),
    slot_starts_at    TIMESTAMPTZ NOT NULL,
    slot_ends_at      TIMESTAMPTZ NOT NULL,
    clash_starts_at   TIMESTAMPTZ NOT NULL,
    clash_ends_at     TIMESTAMPTZ NOT NULL,
    reason            TEXT NOT NULL,
    approved_by       UUID NOT NULL,
    approved_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_trainprov_override_reason CHECK (COALESCE(char_length(btrim(reason)), 0) >= 10),
    CONSTRAINT ck_trainprov_override_label CHECK (COALESCE(char_length(btrim(resource_label)), 0) >= 1),
    CONSTRAINT ck_trainprov_override_window CHECK (slot_ends_at > slot_starts_at AND clash_ends_at > clash_starts_at)
);
CREATE INDEX idx_trainprov_override_slot ON trainprov_timetable_overrides (tenant_id, slot_id);
CREATE INDEX idx_trainprov_override_clash ON trainprov_timetable_overrides (tenant_id, clashing_slot_id);
