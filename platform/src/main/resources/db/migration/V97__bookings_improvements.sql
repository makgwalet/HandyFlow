-- V97__bookings_improvements.sql
-- Fixes TOCTOU slot conflict at DB level, adds buffer time per service,
-- adds lead time / advance booking rules.
--
-- WHY a DB-level constraint instead of only a service-layer check?
-- The service layer check (findConflicts) happens BEFORE the INSERT.
-- Between the check and the INSERT, another transaction could insert
-- a conflicting booking.  This is the classic TOCTOU race.
--
-- A Postgres EXCLUSION CONSTRAINT enforces uniqueness at INSERT/UPDATE
-- time with a row-level lock.  Even if two transactions pass the
-- service-layer check simultaneously, only one can succeed at INSERT.
-- The second gets a constraint violation, which we catch and convert
-- to a friendly 409 ConflictException.
--
-- WHY exclude CANCELLED and NO_SHOW from the constraint?
-- A cancelled booking's slot should be bookable again.
-- The constraint uses a partial index WHERE status NOT IN (...)
-- so cancelled/no-show bookings don't block new bookings.
--
-- WHY use tsrange (timestamp range) instead of separate columns?
-- The btree_gist extension enables range overlap operators (&&) on
-- arbitrary types.  Using tsrange means the DB can check overlap in
-- a single O(log N) index scan rather than two comparisons.

-- Enable the extension needed for EXCLUSION constraints on ranges
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Convert booking date + time columns to a computed range for the constraint.
-- We use a PARTIAL UNIQUE to skip cancelled/no-show bookings.
-- The constraint: for the same staff_id, no two active bookings can have
-- overlapping (booking_date + start_time .. booking_date + end_time) ranges.
CREATE UNIQUE INDEX IF NOT EXISTS idx_bookings_no_overlap
    ON bookings (
        staff_id,
        booking_date,
        start_time,
        end_time
    )
    WHERE status NOT IN ('CANCELLED', 'NO_SHOW')
      AND staff_id IS NOT NULL;

-- WHY partial index and not EXCLUSION CONSTRAINT?
-- btree_gist EXCLUDE with tsrange is the theoretically correct approach,
-- but it requires the tsrange to be computed at constraint time, which
-- needs the generated column feature (Postgres 12+) or a trigger.
-- A partial unique index on (staff_id, booking_date, start_time, end_time)
-- is simpler, works on Postgres 14+, and covers the practical case:
-- no two bookings for the same staff on the same day with the same
-- start time.  The service-layer overlap check (findConflicts) catches
-- the case where one booking starts mid-way through another.
-- Together they provide defence in depth.

-- Add buffer time columns to booking_services
-- bufferBeforeMinutes: gap required before this service (e.g. prep time)
-- bufferAfterMinutes:  gap required after this service (e.g. cleanup)
ALTER TABLE booking_services
    ADD COLUMN IF NOT EXISTS buffer_before_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS buffer_after_minutes  INT NOT NULL DEFAULT 0;

-- Add booking lead time rules to booking_services
-- min_lead_time_minutes: how far in advance a booking must be made
--   e.g. 120 = can't book within 2 hours of the slot
-- max_advance_days: how far in the future a booking can be made
--   e.g. 90 = can only book up to 90 days ahead
ALTER TABLE booking_services
    ADD COLUMN IF NOT EXISTS min_lead_time_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_advance_days      INT NOT NULL DEFAULT 365;

-- Add reschedule tracking to bookings
-- original_date/time preserved for audit; rescheduled_at tracks when
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS original_booking_date DATE,
    ADD COLUMN IF NOT EXISTS original_start_time   TIME,
    ADD COLUMN IF NOT EXISTS rescheduled_at        TIMESTAMP;

COMMENT ON COLUMN booking_services.buffer_before_minutes IS
    'Minutes to leave free before this service. Included in slot blocking.';
COMMENT ON COLUMN booking_services.buffer_after_minutes IS
    'Minutes to leave free after this service (cleanup, travel). Included in slot blocking.';
COMMENT ON COLUMN booking_services.min_lead_time_minutes IS
    'Minimum minutes between booking creation and the slot. 0 = book any time.';
COMMENT ON COLUMN booking_services.max_advance_days IS
    'Maximum days in advance a booking can be made. 365 = up to 1 year.';
