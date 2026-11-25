-- Training provider: attendance per timetable slot and cohort member. One row per learner per slot. A correction changes the row and
-- writes an immutable history line with a reason. Attendance is a record of who was there. It is not a competence decision.

CREATE TABLE trainprov_attendance (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    slot_id      UUID NOT NULL REFERENCES trainprov_timetable_slots(id),
    member_id    UUID NOT NULL REFERENCES trainprov_cohort_members(id),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')),
    note         TEXT,
    recorded_by  UUID NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_attendance UNIQUE (slot_id, member_id),
    CONSTRAINT ck_trainprov_attendance_excused CHECK (status <> 'EXCUSED' OR COALESCE(char_length(btrim(note)), 0) >= 10)
);
CREATE INDEX idx_trainprov_attendance_slot ON trainprov_attendance (tenant_id, slot_id);
CREATE INDEX idx_trainprov_attendance_member ON trainprov_attendance (tenant_id, member_id);

CREATE TABLE trainprov_attendance_events (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    attendance_id  UUID NOT NULL REFERENCES trainprov_attendance(id),
    action         VARCHAR(20) NOT NULL CHECK (action IN ('RECORDED', 'CORRECTED')),
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    reason         TEXT,
    acted_by       UUID NOT NULL,
    acted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_trainprov_attendance_event_reason CHECK (action = 'RECORDED' OR COALESCE(char_length(btrim(reason)), 0) >= 10)
);
CREATE INDEX idx_trainprov_attendance_event ON trainprov_attendance_events (tenant_id, attendance_id, acted_at);
