-- V96__booking_number_sequence.sql
-- Fixes the COUNT(*)+1 race condition in BookingNumberGenerator.
--
-- WHY a counter table instead of a Postgres SEQUENCE?
-- A global Postgres sequence (CREATE SEQUENCE) is not partitioned by
-- tenant or year.  You'd get BK-2026-00001 for one tenant and
-- BK-2026-00002 for a different tenant — numbers are globally unique
-- but not per-tenant, which breaks the expected numbering format.
--
-- This table gives us an atomic per-tenant-per-year counter using
-- INSERT ... ON CONFLICT ... DO UPDATE RETURNING, which is a single
-- atomic SQL statement — no race condition possible.
--
-- WHY ON CONFLICT rather than a lock?
-- SELECT ... FOR UPDATE requires a row to already exist.  The first
-- booking of a year would find no row and you'd need a separate INSERT
-- anyway.  INSERT ... ON CONFLICT handles both "first of year" and
-- "subsequent" atomically in one round-trip.

CREATE TABLE booking_number_seq (
    tenant_id  UUID NOT NULL,
    year       INT  NOT NULL,
    last_seq   INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

COMMENT ON TABLE booking_number_seq IS
    'Atomic per-tenant-per-year counter for booking numbers. '
    'Updated via INSERT ON CONFLICT DO UPDATE to avoid race conditions.';
