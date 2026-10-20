-- Rename to the next free Flyway version, e.g. V128__fleet_driver_scoring.sql

-- 1. Trips get a real link to a Driver (nullable: historical and free-text-only trips keep working).
ALTER TABLE fleet_trips ADD COLUMN driver_id UUID;

-- Backfill ONLY unambiguous matches: a trip's free-text driver_name equals exactly one
-- active driver's full name (case-insensitive) within the same tenant. Anything ambiguous
-- (two drivers with the same name) or unmatched stays NULL rather than guessing.
UPDATE fleet_trips t
SET driver_id = m.driver_id
FROM (
    SELECT t2.id AS trip_id, MIN(d.id::text)::uuid AS driver_id
    FROM fleet_trips t2
    JOIN fleet_drivers d
      ON d.tenant_id = t2.tenant_id
     AND d.deleted_at IS NULL
     AND LOWER(TRIM(d.first_name || ' ' || d.last_name)) = LOWER(TRIM(t2.driver_name))
    WHERE t2.driver_id IS NULL AND t2.driver_name IS NOT NULL
    GROUP BY t2.id
    HAVING COUNT(*) = 1
) m
WHERE t.id = m.trip_id;

CREATE INDEX idx_fleet_trips_driver ON fleet_trips (tenant_id, driver_id, start_at DESC) WHERE driver_id IS NOT NULL;

-- 2. Indexes for the per-driver score queries on tables from earlier chunks.
CREATE INDEX idx_fleet_accidents_driver
    ON fleet_accidents (tenant_id, driver_id, occurred_at DESC) WHERE driver_id IS NOT NULL;
CREATE INDEX idx_fleet_inspections_driver
    ON fleet_inspections (tenant_id, driver_id, completed_at) WHERE driver_id IS NOT NULL AND status = 'COMPLETED';

-- 3. Per-tenant score configuration (one row per tenant; absent row = built-in defaults).
CREATE TABLE fleet_driver_score_settings (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID        NOT NULL UNIQUE,
    weight_safety        INTEGER     NOT NULL DEFAULT 25,
    weight_inspection    INTEGER     NOT NULL DEFAULT 20,
    weight_accidents     INTEGER     NOT NULL DEFAULT 25,
    weight_fuel          INTEGER     NOT NULL DEFAULT 10,
    weight_trips         INTEGER     NOT NULL DEFAULT 10,
    weight_compliance    INTEGER     NOT NULL DEFAULT 10,
    lookback_days        INTEGER     NOT NULL DEFAULT 90,
    excellent_threshold  INTEGER     NOT NULL DEFAULT 90,
    good_threshold       INTEGER     NOT NULL DEFAULT 75,
    fair_threshold       INTEGER     NOT NULL DEFAULT 60,
    updated_at           TIMESTAMPTZ NOT NULL,
    updated_by           UUID,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_driver_score_weights CHECK (
        weight_safety >= 0 AND weight_inspection >= 0 AND weight_accidents >= 0 AND
        weight_fuel >= 0 AND weight_trips >= 0 AND weight_compliance >= 0 AND
        weight_safety + weight_inspection + weight_accidents + weight_fuel + weight_trips + weight_compliance = 100),
    CONSTRAINT chk_driver_score_thresholds CHECK (
        excellent_threshold > good_threshold AND good_threshold > fair_threshold AND fair_threshold > 0
        AND excellent_threshold <= 100),
    CONSTRAINT chk_driver_score_lookback CHECK (lookback_days BETWEEN 7 AND 730)
);