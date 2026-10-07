-- Who changed which question groups a visit type opens, and what the list was afterwards. Append-only by convention:
-- the application never updates or deletes rows. (clinic_content_audit is keyed to a single group, so it cannot hold this.)
CREATE TABLE IF NOT EXISTS clinic_visit_mapping_audit (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL,
    visit_type VARCHAR(50) NOT NULL,
    action     VARCHAR(10) NOT NULL CHECK (action IN ('SET', 'CLEAR')),
    groups     JSONB       NOT NULL DEFAULT '[]'::jsonb,   -- the practice's list after the change; [] for CLEAR
    actor_id   UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_visit_mapping_audit
    ON clinic_visit_mapping_audit (tenant_id, visit_type, created_at DESC);
