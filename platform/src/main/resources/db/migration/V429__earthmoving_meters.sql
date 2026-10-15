-- E6: each machine has a meter type (hours or kilometres). The existing current_hours / last_service_hours /
-- service_interval_hours columns now hold the reading in that unit, so no data moves and existing machines stay HOURS.
-- Every change to the reading is kept in earthmoving_meter_readings; corrections add a row, they never rewrite one.
ALTER TABLE earthmoving_assets ADD COLUMN IF NOT EXISTS meter_type VARCHAR(12) NOT NULL DEFAULT 'HOURS';
DO $$ BEGIN
    ALTER TABLE earthmoving_assets ADD CONSTRAINT chk_em_asset_meter_type CHECK (meter_type IN ('HOURS', 'KILOMETRES'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS earthmoving_meter_readings (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    asset_id         UUID NOT NULL REFERENCES earthmoving_assets(id),
    meter_type       VARCHAR(12) NOT NULL,
    reading          NUMERIC(10,1) NOT NULL CHECK (reading >= 0),
    previous_reading NUMERIC(10,1),
    source           VARCHAR(16) NOT NULL CHECK (source IN ('MANUAL', 'SHIFT', 'SERVICE', 'CORRECTION', 'TYPE_CHANGE')),
    reason           TEXT,
    actor_user_id    UUID,
    recorded_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_em_reading_reason CHECK (source NOT IN ('CORRECTION', 'TYPE_CHANGE') OR length(btrim(coalesce(reason, ''))) >= 5)
);
CREATE INDEX IF NOT EXISTS idx_em_meter_readings_asset ON earthmoving_meter_readings (tenant_id, asset_id, recorded_at DESC);
