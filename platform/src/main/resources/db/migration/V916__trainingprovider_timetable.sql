-- Training provider: timetable slots for a cohort. A slot is a dated block of teaching with an optional venue, facilitator and programme module.
-- Slots are cancelled with a reason, never deleted. Clash checks (venue, facilitator) and the facilitator's standing on the slot's date are
-- made by the service; the table keeps the shape of a slot honest.

CREATE TABLE trainprov_timetable_slots (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    cohort_id         UUID NOT NULL REFERENCES trainprov_cohorts(id),
    slot_date         DATE NOT NULL,
    start_time        TIME NOT NULL,
    end_time          TIME NOT NULL,
    title             VARCHAR(200) NOT NULL,
    module_id         UUID REFERENCES trainprov_programme_modules(id),
    venue_id          UUID REFERENCES trainprov_venues(id),
    practitioner_id   UUID REFERENCES trainprov_practitioners(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    cancelled_reason  TEXT,
    cancelled_by      UUID,
    cancelled_at      TIMESTAMPTZ,
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_slot_times CHECK (end_time > start_time),
    CONSTRAINT ck_trainprov_slot_title CHECK (COALESCE(char_length(btrim(title)), 0) >= 2),
    CONSTRAINT ck_trainprov_slot_cancelled CHECK (
        (status = 'SCHEDULED' AND cancelled_reason IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND COALESCE(char_length(btrim(cancelled_reason)), 0) >= 10 AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL))
);
CREATE INDEX idx_trainprov_slot_cohort ON trainprov_timetable_slots (tenant_id, cohort_id, slot_date, start_time);
CREATE INDEX idx_trainprov_slot_venue ON trainprov_timetable_slots (tenant_id, venue_id, slot_date) WHERE venue_id IS NOT NULL;
CREATE INDEX idx_trainprov_slot_person ON trainprov_timetable_slots (tenant_id, practitioner_id, slot_date) WHERE practitioner_id IS NOT NULL;
