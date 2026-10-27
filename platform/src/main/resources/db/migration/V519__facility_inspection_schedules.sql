-- Recurring inspections: a checklist to be run at a site (optionally against one asset) every N days.
CREATE TABLE IF NOT EXISTS facility_inspection_schedules (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    template_id           UUID        NOT NULL,
    site_id               UUID        NOT NULL,
    asset_id              UUID,
    frequency_days        INTEGER     NOT NULL,
    next_due_date         DATE        NOT NULL,
    last_completed_date   DATE,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    deleted_at            TIMESTAMPTZ,
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_facility_insp_sched_freq CHECK (frequency_days BETWEEN 1 AND 3650)
);
CREATE INDEX IF NOT EXISTS idx_facility_insp_sched_tenant ON facility_inspection_schedules (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_facility_insp_sched_due ON facility_inspection_schedules (next_due_date) WHERE active AND deleted_at IS NULL;
-- One live schedule per checklist, site and asset (a site-wide schedule has no asset).
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_insp_sched_target
    ON facility_inspection_schedules (tenant_id, template_id, site_id, COALESCE(asset_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL;

-- An inspection started for a schedule remembers it, so completing it moves the schedule on.
ALTER TABLE facility_inspections ADD COLUMN IF NOT EXISTS schedule_id UUID;
