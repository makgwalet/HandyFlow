-- V215__add_site_qr_enforcement.sql
-- (Rename to your actual next migration number before applying.)
--
-- Replaces CheckpointScanService.ENFORCE_QR_HMAC (a single hardcoded
-- boolean affecting every tenant/site simultaneously) with a per-site flag.
-- WHY per-site rather than global?
-- Flipping HMAC enforcement globally requires every site's physical QR
-- stickers to already be reprinted with signed payloads before the flip --
-- otherwise every scan at every unreprinted site fails immediately. Sites
-- reprint on their own schedule (a supervisor visits the site, swaps the
-- stickers), not synchronized to a code deploy. A global flag makes that a
-- single all-or-nothing outage window; a per-site flag lets each site's
-- admin enable enforcement only once THAT site's codes are actually signed.

ALTER TABLE security_sites
    ADD COLUMN IF NOT EXISTS require_signed_qr BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN security_sites.require_signed_qr IS
    'Per-site QR HMAC enforcement (replaces the old global ENFORCE_QR_HMAC constant). When true, CheckpointScanService.scan() rejects any QR scan whose payload is not a validly-signed {checkpointId}:{siteId}:{signature} string. Default false for backward compatibility with existing unsigned QR stickers -- flip only after reprinting this site''s checkpoints via GET /sites/{siteId}/checkpoints/{checkpointId}/qr-payload.';