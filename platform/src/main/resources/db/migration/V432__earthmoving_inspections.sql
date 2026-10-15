-- E12: pre-use inspections. One row per inspection of one machine, one row per checklist item. Append-only: an
-- inspection is a record of what the inspector found, so it is never edited. A failed item links to the incident it raised.
CREATE TABLE IF NOT EXISTS earthmoving_inspections (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    asset_id          UUID NOT NULL REFERENCES earthmoving_assets(id),
    inspector_user_id UUID,
    meter_reading     NUMERIC(10,1) CHECK (meter_reading IS NULL OR meter_reading >= 0),
    result            VARCHAR(10) NOT NULL CHECK (result IN ('PASS', 'DEFECTS', 'UNSAFE')),
    notes             TEXT,
    performed_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_em_inspections_asset ON earthmoving_inspections (tenant_id, asset_id, performed_at DESC);

CREATE TABLE IF NOT EXISTS earthmoving_inspection_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    inspection_id UUID NOT NULL REFERENCES earthmoving_inspections(id),
    position      INTEGER NOT NULL,
    item          VARCHAR(80) NOT NULL,
    outcome       VARCHAR(8) NOT NULL CHECK (outcome IN ('OK', 'DEFECT', 'UNSAFE')),
    comment       TEXT,
    incident_id   UUID,
    CONSTRAINT chk_em_insp_item_comment CHECK (outcome = 'OK' OR length(btrim(coalesce(comment, ''))) >= 5)
);
CREATE INDEX IF NOT EXISTS idx_em_inspection_items_insp ON earthmoving_inspection_items (inspection_id, position);
