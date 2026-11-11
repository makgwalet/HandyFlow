-- Events Chunk 3: waitlist, group / walk-in / import / transfer support.
-- RENAME to your next Flyway version (after the Chunk 1 and Chunk 2 migrations),
-- e.g. V214__events_registration_extras.sql

-- ── Guest provenance ────────────────────────────────────────────────────────
-- source: STAFF | GROUP | WALK_IN | IMPORT | WAITLIST | TRANSFER
-- (existing rows become STAFF, which is how they were created)
ALTER TABLE event_guests ADD COLUMN IF NOT EXISTS source           VARCHAR(20) NOT NULL DEFAULT 'STAFF';
ALTER TABLE event_guests ADD COLUMN IF NOT EXISTS group_ref        UUID;
ALTER TABLE event_guests ADD COLUMN IF NOT EXISTS transferred_from UUID;

CREATE INDEX IF NOT EXISTS idx_event_guests_group
    ON event_guests (group_ref) WHERE group_ref IS NOT NULL;

-- A transferred ticket's old holder no longer counts as a live registration,
-- so TRANSFERRED must be excluded from the duplicate-identity index
-- (replaces the Chunk 1 version of this index).
DROP INDEX IF EXISTS uq_event_guests_active_identity;
CREATE UNIQUE INDEX uq_event_guests_active_identity
    ON event_guests (event_id, lower(email), lower(full_name))
    WHERE email IS NOT NULL AND status NOT IN ('CANCELLED', 'NO_SHOW', 'TRANSFERRED');

-- ── Waitlist ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_waitlist (
    id                    UUID         PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    event_id              UUID         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    tier_id               UUID         REFERENCES event_ticket_tiers (id),
    customer_id           UUID,
    full_name             VARCHAR(200) NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    phone                 VARCHAR(50),
    company               VARCHAR(200),
    dietary_requirements  VARCHAR(500),
    status                VARCHAR(20)  NOT NULL DEFAULT 'WAITING',   -- WAITING | PROMOTED | REMOVED
    promoted_guest_id     UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- FIFO scans: "oldest waiting entries for this event"
CREATE INDEX IF NOT EXISTS idx_event_waitlist_fifo
    ON event_waitlist (event_id, status, created_at);

-- The same person cannot be waiting twice for one event
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_waitlist_waiting_identity
    ON event_waitlist (event_id, lower(email), lower(full_name))
    WHERE status = 'WAITING';