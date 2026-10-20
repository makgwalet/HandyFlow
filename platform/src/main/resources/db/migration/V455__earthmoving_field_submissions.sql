-- Earthmoving: field capture. Entries made on a phone (possibly offline) arrive with a client-made submission id. The server re-checks each one when it
-- syncs and records the outcome. Nothing is overwritten: a conflicting entry is kept beside the server's data and flagged for review.
CREATE TABLE earthmoving_field_submissions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID           NOT NULL,
    submission_id    UUID           NOT NULL,
    kind             VARCHAR(30)    NOT NULL,
    asset_id         UUID,
    captured_at      TIMESTAMPTZ    NOT NULL,
    received_at      TIMESTAMPTZ    NOT NULL,
    submitted_by     UUID           NOT NULL,
    meter_value      NUMERIC(12, 2),
    latitude         NUMERIC(9, 6),
    longitude        NUMERIC(9, 6),
    note             TEXT,
    status           VARCHAR(10)    NOT NULL,
    reason           TEXT,
    reviewed_by      UUID,
    reviewed_at      TIMESTAMPTZ,
    review_decision  VARCHAR(20),
    review_note      TEXT,
    CONSTRAINT chk_em_field_status   CHECK (status IN ('SYNCED', 'REJECTED', 'CONFLICT')),
    CONSTRAINT chk_em_field_decision CHECK (review_decision IS NULL OR review_decision IN ('KEPT_SERVER', 'APPLIED')),
    CONSTRAINT chk_em_field_review   CHECK ((review_decision IS NULL) = (reviewed_by IS NULL)),
    CONSTRAINT chk_em_field_only_conflict CHECK (review_decision IS NULL OR status = 'CONFLICT'),
    CONSTRAINT chk_em_field_reason   CHECK (status = 'SYNCED' OR reason IS NOT NULL)
);
-- The same entry sent twice (a retry after a dropped connection) is one entry.
CREATE UNIQUE INDEX uq_em_field_submission ON earthmoving_field_submissions (tenant_id, submission_id);
CREATE INDEX idx_em_field_open_conflicts ON earthmoving_field_submissions (tenant_id, received_at DESC) WHERE status = 'CONFLICT' AND review_decision IS NULL;
CREATE INDEX idx_em_field_mine ON earthmoving_field_submissions (tenant_id, submitted_by, received_at DESC);

-- Readings applied from a phone are marked FIELD, so a late field entry is compared with the time it was captured, not the time it reached the server.
ALTER TABLE earthmoving_meter_readings DROP CONSTRAINT IF EXISTS earthmoving_meter_readings_source_check;
ALTER TABLE earthmoving_meter_readings DROP CONSTRAINT IF EXISTS chk_em_reading_source;
ALTER TABLE earthmoving_meter_readings ADD CONSTRAINT chk_em_reading_source CHECK (source IN ('MANUAL', 'SHIFT', 'SERVICE', 'CORRECTION', 'TYPE_CHANGE', 'FIELD'));
