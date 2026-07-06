-- V___fleet_service_date_and_trip_alert.sql
-- (rename to the next available Flyway version number in your sequence)

-- Without this, Vehicle.isDueForService()'s day-based interval check had
-- nothing to compare serviceIntervalDays against — the "time-based
-- interval" field was accepted on the create form and stored, but
-- structurally could never actually trigger. See Vehicle.java's Javadoc.
ALTER TABLE fleet_vehicles
    ADD COLUMN IF NOT EXISTS last_service_date DATE;

-- Backfill: for vehicles with existing service history, set last_service_date
-- from their most recent service record so the day-based interval doesn't
-- start counting from NULL (which isDueForService() correctly treats as "no
-- day-based check possible yet", but that undercounts vehicles that have
-- actually been serviced before this column existed).
UPDATE fleet_vehicles v
SET last_service_date = (
    SELECT MAX(s.service_date)
    FROM fleet_services s
    WHERE s.vehicle_id = v.id AND s.deleted_at IS NULL
)
WHERE v.last_service_date IS NULL;

-- Backs FleetNotificationScheduler's "forgotten trip" idempotency — without
-- this, every scheduler run would re-notify for every still-active long
-- trip every time it runs. See Trip.java's Javadoc.
ALTER TABLE fleet_trips
    ADD COLUMN IF NOT EXISTS long_running_alert_sent BOOLEAN NOT NULL DEFAULT FALSE;
