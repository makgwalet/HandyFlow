-- Events Chunk 1: integrity constraints.
-- RENAME this file to your next Flyway version, e.g. V212__events_integrity_constraints.sql
--
-- RUN THESE DIAGNOSTICS FIRST. The old countActive()+1 ticket numbering could
-- have produced duplicates after cancellations; if either query returns rows,
-- fix that data before applying this migration or the unique indexes will fail.
--
--   SELECT event_id, ticket_number, COUNT(*)
--   FROM event_guests GROUP BY event_id, ticket_number HAVING COUNT(*) > 1;
--
--   SELECT event_id, lower(email), lower(full_name), COUNT(*)
--   FROM event_guests
--   WHERE email IS NOT NULL AND status NOT IN ('CANCELLED','NO_SHOW')
--   GROUP BY 1,2,3 HAVING COUNT(*) > 1;
--
--   SELECT id FROM event_ticket_tiers WHERE quantity_sold < 0 OR quantity_sold > quantity;

-- One ticket number per event
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_guests_ticket
    ON event_guests (event_id, ticket_number);

-- QR tokens are random UUIDs and must never collide
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_guests_qr
    ON event_guests (qr_code);

-- Backstop for the service-level duplicate check: same person (email + name)
-- cannot hold two live registrations for one event.
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_guests_active_identity
    ON event_guests (event_id, lower(email), lower(full_name))
    WHERE email IS NOT NULL AND status NOT IN ('CANCELLED', 'NO_SHOW');

-- Fast capacity / stats counts
CREATE INDEX IF NOT EXISTS idx_event_guests_event_status
    ON event_guests (event_id, status);

-- Last line of defence against overselling, even if a code path bypasses the service
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_event_tier_sold_within_quantity') THEN
        ALTER TABLE event_ticket_tiers
            ADD CONSTRAINT chk_event_tier_sold_within_quantity
            CHECK (quantity_sold >= 0 AND quantity_sold <= quantity);
    END IF;
END $$;