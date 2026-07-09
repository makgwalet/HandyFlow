-- V___lease_expiry_notice_tracking.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs the lease-expiry scheduler — nothing previously tracked whether a
-- lease had already been notified at a given expiry threshold (90/60/30
-- days out), so there was no way to send a progressively-more-urgent notice
-- as expiry approaches without either spamming daily once a lease crossed
-- into range, or never sending anything at all (which is what was
-- happening — no scheduler existed for this at all).

ALTER TABLE leases
    ADD COLUMN IF NOT EXISTS last_expiry_notice_days INT,
    ADD COLUMN IF NOT EXISTS last_expiry_notice_at   TIMESTAMP;