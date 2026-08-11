-- V217__add_checkpoint_qr_secret.sql
-- (Rename to your actual next migration number before applying.)
--
-- Moves QR signing from a site-wide secret (Site.qrSecret) to a per-checkpoint
-- secret. WHY: with a site-wide secret, the only way to invalidate one
-- compromised checkpoint's QR code was to rotate the secret for the ENTIRE
-- site, forcing every other checkpoint at that site to be reprinted too --
-- a bad trade-off for "one sticker got stolen off a gate." A per-checkpoint
-- secret means regenerating one checkpoint's QR touches only that checkpoint.
--
-- GOOD TIMING NOTE: Site.requireSignedQr (added last session) has not yet
-- been enabled in production anywhere -- there are no real signed QR codes
-- printed and mounted yet using the old site-secret formula. This migration
-- can land cleanly with no live-code invalidation concern.

ALTER TABLE security_checkpoints
    ADD COLUMN qr_secret VARCHAR(64);

-- Backfill: every existing checkpoint gets its own random secret, same
-- generation shape as Site.qrSecret (UUID with hyphens stripped).
UPDATE security_checkpoints
SET qr_secret = replace(gen_random_uuid()::text, '-', '')
WHERE qr_secret IS NULL;

ALTER TABLE security_checkpoints
    ALTER COLUMN qr_secret SET NOT NULL;

COMMENT ON COLUMN security_checkpoints.qr_secret IS
    'Per-checkpoint HMAC signing secret for this checkpoint''s QR payload. Replaces the old site-wide Site.qrSecret for this purpose (Site.qrSecret is unused for QR signing as of this migration, though the column remains for now in case anything else references it). Rotated independently via Checkpoint.regenerateQr() -- see CheckpointScanService/SiteController''s qr-secret/regenerate endpoint.';