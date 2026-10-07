-- V331__security_round_numbers_per_route_and_nfc_per_tenant.sql
--
-- 1. Patrol round numbers are now per route. Rounds are generated for every active route of the site, each numbered
--    1, 2, 3 from the start of the shift, so two active routes at one site produced two "round 1" rows and the
--    shift could not start (unique on shift and round number). A round is now unique per shift, route and number.
--    Rounds without a route (older data) stay unique per shift and number: NULLS NOT DISTINCT treats the null routes
--    as equal.
-- 2. An NFC tag or Bluetooth beacon only has to be unique within a tenant. The old indexes were global, so one
--    company's tag could block another company from using the same physical tag id. The scan lookup is already
--    tenant scoped.

ALTER TABLE security_patrol_rounds
    DROP CONSTRAINT IF EXISTS security_patrol_rounds_shift_id_round_number_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_patrol_round_shift_route_number
    ON security_patrol_rounds (shift_id, route_id, round_number) NULLS NOT DISTINCT;

DROP INDEX IF EXISTS uq_checkpoint_nfc;
DROP INDEX IF EXISTS uq_checkpoint_ble;

CREATE UNIQUE INDEX IF NOT EXISTS uq_checkpoint_nfc_per_tenant
    ON security_checkpoints (tenant_id, nfc_tag_uid)
    WHERE nfc_tag_uid IS NOT NULL AND active = true;

CREATE UNIQUE INDEX IF NOT EXISTS uq_checkpoint_ble_per_tenant
    ON security_checkpoints (tenant_id, ble_beacon_id)
    WHERE ble_beacon_id IS NOT NULL AND active = true;
