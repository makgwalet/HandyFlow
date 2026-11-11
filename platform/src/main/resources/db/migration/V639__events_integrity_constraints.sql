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

-- Repair duplicate ticket numbers left by the old countActive()+1 numbering, then add the unique index.
-- The earliest registration keeps its number; each later one gets the next free number for that event
-- (same prefix, sequence continuing after the highest in use). Nothing is deleted or cancelled.
WITH ranked AS (
    SELECT id, event_id, ticket_number,
           ROW_NUMBER() OVER (PARTITION BY event_id, ticket_number ORDER BY created_at, id) AS rn
    FROM event_guests
), dups AS (
    SELECT id, event_id, ticket_number,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY ticket_number, id) AS n
    FROM ranked WHERE rn > 1
), mx AS (
    SELECT event_id, COALESCE(MAX((substring(ticket_number FROM '(\d+)$'))::bigint), 0) AS m
    FROM event_guests GROUP BY event_id
)
UPDATE event_guests g
SET ticket_number = regexp_replace(d.ticket_number, '\d+$', '') || lpad((mx.m + d.n)::text, 4, '0')
FROM dups d JOIN mx ON mx.event_id = d.event_id
WHERE g.id = d.id;

-- One ticket number per event
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_guests_ticket
    ON event_guests (event_id, ticket_number);

-- QR tokens are random UUIDs and must never collide
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_guests_qr
    ON event_guests (qr_code);

-- Backstop for the service-level duplicate check: same person (email + name)
-- cannot hold two live registrations for one event. Existing duplicates are real
-- registrations that a person must resolve (cancel one), so they are not changed
-- here: when any exist the index is skipped with a warning instead of failing the
-- whole migration. Run the second diagnostic above, resolve the rows, then create it:
--   CREATE UNIQUE INDEX uq_event_guests_active_identity ON event_guests (event_id, lower(email), lower(full_name))
--       WHERE email IS NOT NULL AND status NOT IN ('CANCELLED', 'NO_SHOW');
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM event_guests WHERE email IS NOT NULL AND status NOT IN ('CANCELLED', 'NO_SHOW')
               GROUP BY event_id, lower(email), lower(full_name) HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'uq_event_guests_active_identity NOT created: duplicate live registrations exist; resolve them and create it by hand';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_event_guests_active_identity
            ON event_guests (event_id, lower(email), lower(full_name))
            WHERE email IS NOT NULL AND status NOT IN ('CANCELLED', 'NO_SHOW');
    END IF;
END $$;

-- Fast capacity / stats counts
CREATE INDEX IF NOT EXISTS idx_event_guests_event_status
    ON event_guests (event_id, status);

-- Last line of defence against overselling, even if a code path bypasses the service
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_event_tier_sold_within_quantity') THEN
        ALTER TABLE event_ticket_tiers
            ADD CONSTRAINT chk_event_tier_sold_within_quantity
            CHECK (quantity_sold >= 0 AND quantity_sold <= quantity) NOT VALID;   -- guards every new write; rows already over are left for a person to fix
    END IF;
END $$;