-- Client portal links used to be valid forever. Add an expiry.
ALTER TABLE security_sites ADD COLUMN IF NOT EXISTS portal_expires_at TIMESTAMPTZ;

-- Links already out in the world get 30 days from now, then must be renewed by a manager.
UPDATE security_sites
   SET portal_expires_at = NOW() + INTERVAL '30 days'
 WHERE portal_enabled = TRUE AND portal_token IS NOT NULL AND portal_expires_at IS NULL;
