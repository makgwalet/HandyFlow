-- =============================================================================
-- V101__booking_service_staff_index.sql
-- Performance index on the booking_service_staff join table.
--
-- WHY now and not in V23?
-- V23 created the table but didn't add an index on staff_id.
-- SlotEngine queries "which services can this staff member perform?" using
-- staff_id — without an index this is a full table scan as the table grows.
-- A covering index on staff_id makes this O(log N).
--
-- The PRIMARY KEY on (service_id, staff_id) already covers "which staff
-- can perform this service?" (leading with service_id).
-- This index covers the reverse: "which services can this staff member do?"
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_booking_service_staff_staff
    ON booking_service_staff (staff_id);

COMMENT ON TABLE booking_service_staff IS
    'Staff–service skill assignments. '
    'A staff member only appears in available slots for services they are assigned to. '
    'If a service has NO entries here, ALL staff are eligible (backwards-compatible default).';
