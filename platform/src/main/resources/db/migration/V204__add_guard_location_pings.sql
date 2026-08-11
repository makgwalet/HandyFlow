-- V216__add_guard_location_pings.sql
-- (Rename to your actual next migration number before applying.)
--
-- Backend-only pass: ingestion + storage. No map/overview query endpoint
-- yet -- that's the next stage. See conversation for the staged plan.
--
-- Two tables, not one:
--
-- 1. security_guard_location_pings -- append-only history, same convention
--    as ArmouryLog/CheckpointLog elsewhere in this module. Useful later for
--    the audit doc's own "anomaly detection on patrol patterns" idea
--    (consistently rushed rounds, routes never varied), not needed for the
--    live-map query itself.
--
-- 2. security_guard_current_location -- single row per guard, upserted on
--    every ping (INSERT ... ON CONFLICT (guard_id) DO UPDATE). This is what
--    the future "who's where right now" map query will read. WHY a separate
--    denormalized table rather than querying the history table for each
--    guard's most recent row? At 1000+ guards pinging every 5 minutes, that
--    query runs constantly (every dashboard refresh) -- doing it against an
--    ever-growing history table means it gets slower over time even though
--    the actual answer ("where is everyone RIGHT NOW") only ever needs one
--    row per guard. This table is O(guards); the history table would be
--    O(pings-ever-recorded) for the same question.

CREATE TABLE security_guard_location_pings (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    guard_id           UUID NOT NULL,
    shift_id           UUID,
    device_session_id  UUID NOT NULL,
    latitude           NUMERIC(10,7) NOT NULL,
    longitude          NUMERIC(10,7) NOT NULL,
    accuracy_metres    NUMERIC(8,2),
    recorded_at        TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guard_location_pings_guard_time
    ON security_guard_location_pings (tenant_id, guard_id, recorded_at DESC);

COMMENT ON TABLE security_guard_location_pings IS
    'Append-only GPS ping history from the guard app, recorded every ~5 minutes while a DeviceSession is open. Not queried by the live map (see security_guard_current_location) -- this is the audit trail / future patrol-pattern-analysis input.';

CREATE TABLE security_guard_current_location (
    guard_id     UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    shift_id     UUID,
    site_id      UUID,
    latitude     NUMERIC(10,7) NOT NULL,
    longitude    NUMERIC(10,7) NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guard_current_location_tenant_site
    ON security_guard_current_location (tenant_id, site_id);
CREATE INDEX idx_guard_current_location_tenant_recorded
    ON security_guard_current_location (tenant_id, recorded_at);

COMMENT ON TABLE security_guard_current_location IS
    'One row per guard, upserted on every GPS ping -- the table the future live-map overview query reads from. recorded_at is used to determine staleness: a guard whose recorded_at is older than the liveness threshold (5 minutes, matching the guard app''s ping interval) should be treated as offline/stale by the map UI, not shown as currently live. That staleness filtering is NOT enforced here -- this table just stores the latest known position; the read-side query (not yet built) applies the 5-minute cutoff.';