-- Events hardening: the guest, payment and check-in code uses statuses the original V25 CHECK constraints reject,
-- and the newer request limits are wider than the original column widths. Without this, reserving a seat, expiring a
-- hold, transferring a ticket or scanning an unpaid ticket fails at the database.

-- ── Guest statuses: RESERVED (paid checkout hold), EXPIRED (hold ran out), TRANSFERRED (ticket moved) ────────────
ALTER TABLE event_guests DROP CONSTRAINT IF EXISTS event_guests_status_check;
ALTER TABLE event_guests ADD CONSTRAINT event_guests_status_check
    CHECK (status IN ('REGISTERED','CONFIRMED','CHECKED_IN','CANCELLED','NO_SHOW','RESERVED','EXPIRED','TRANSFERRED'));

ALTER TABLE event_guests DROP CONSTRAINT IF EXISTS event_guests_payment_status_check;
ALTER TABLE event_guests ADD CONSTRAINT event_guests_payment_status_check
    CHECK (payment_status IN ('FREE','PENDING','PAID','REFUNDED','PARTIALLY_REFUNDED'));

-- ── Check-in results the service records on every scan ───────────────────────────────────────────────────────────
ALTER TABLE event_check_ins DROP CONSTRAINT IF EXISTS event_check_ins_result_check;
ALTER TABLE event_check_ins ADD CONSTRAINT event_check_ins_result_check
    CHECK (result IN ('SUCCESS','ALREADY_CHECKED_IN','CANCELLED_TICKET','TRANSFERRED_TICKET',
                      'PAYMENT_PENDING','EXPIRED_TICKET','NOT_FOUND','WALK_IN'));

-- ── Widths: match the request limits (email 254, phone 50, dietary 500) ───────────────────────────────────────────
ALTER TABLE event_guests ALTER COLUMN email                TYPE VARCHAR(254);
ALTER TABLE event_guests ALTER COLUMN phone                TYPE VARCHAR(50);
ALTER TABLE event_guests ALTER COLUMN dietary_requirements TYPE VARCHAR(500);
ALTER TABLE event_check_ins ALTER COLUMN location    TYPE VARCHAR(200);
ALTER TABLE event_check_ins ALTER COLUMN scan_device TYPE VARCHAR(100);
ALTER TABLE event_vendors ALTER COLUMN contact_email TYPE VARCHAR(254);
ALTER TABLE event_vendors ALTER COLUMN contact_phone TYPE VARCHAR(50);

-- ── An expired hold must not block the same person registering again ─────────────────────────────────────────────
DROP INDEX IF EXISTS uq_event_guests_active_identity;
CREATE UNIQUE INDEX uq_event_guests_active_identity
    ON event_guests (event_id, lower(email), lower(full_name))
    WHERE email IS NOT NULL AND status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED');

-- ── Sanity constraints the DTOs now also enforce ─────────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_event_tier_price_nonnegative') THEN
        ALTER TABLE event_ticket_tiers ADD CONSTRAINT chk_event_tier_price_nonnegative CHECK (price >= 0) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_events_capacity_positive') THEN
        ALTER TABLE events ADD CONSTRAINT chk_events_capacity_positive CHECK (venue_capacity IS NULL OR venue_capacity > 0) NOT VALID;
    END IF;
END $$;
