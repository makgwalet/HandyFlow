-- =============================================================================
-- V104__security_client_portal.sql
-- Client portal: adds portal_token to security_sites
-- =============================================================================

-- WHY a dedicated portal_token separate from qr_secret?
-- qr_secret is used for HMAC-signing checkpoint QR codes — it must never leave
-- the server.  portal_token is handed to the client so they can view their site's
-- dashboard without a HandyFlow account.  Mixing the two secrets would mean that
-- sharing the portal URL with a client also gives them the ability to forge QR
-- codes.  Keep them separate, rotate them independently.
--
-- WHY UUID and not a short code?
-- The portal URL is: /portal/{token}
-- A UUID gives 2^122 possible values — brute-forcing is computationally infeasible.
-- Short codes (6 characters) give ~2 billion possibilities — trivially enumerable
-- given enough requests.  UUIDs are the right choice for security-sensitive tokens.
--
-- WHY nullable?
-- Existing sites should not get a portal token automatically — the tenant must
-- explicitly generate and share it with a client.  A NULL token means the portal
-- is not enabled for this site.  The generate endpoint creates it on demand.

ALTER TABLE security_sites
    ADD COLUMN IF NOT EXISTS portal_token    VARCHAR(36),
    ADD COLUMN IF NOT EXISTS portal_enabled  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS portal_label    VARCHAR(100); -- client-facing name e.g. "Sandton City Mall — Security Dashboard"

-- Unique index: prevents token collision (astronomically unlikely with UUID but
-- ensures the findByPortalToken query returns at most one row with certainty).
CREATE UNIQUE INDEX IF NOT EXISTS uq_site_portal_token
    ON security_sites(portal_token)
    WHERE portal_token IS NOT NULL;