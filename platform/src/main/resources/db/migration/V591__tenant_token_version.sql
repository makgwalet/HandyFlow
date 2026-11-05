-- Token version per tenant ("tcv" claim). Bumping it makes every access token minted
-- earlier for that tenant stale, so permission or entitlement changes take effect at
-- once instead of when the old tokens expire. Old tokens carry no claim and count as 0.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
