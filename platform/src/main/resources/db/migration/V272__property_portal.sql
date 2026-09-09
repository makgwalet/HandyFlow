-- V272__property_portal.sql
-- Property tenant portal, per the design proposal agreed with the
-- product owner: view-only, identity anchored on Lease.lesseeEmail,
-- reusing the shared PortalUser/PortalJwtService/PortalJwtFilter
-- infrastructure already proven across Warehousing/Training Provider/
-- Facilities Management/Recruitment Agency's own portals. Same table
-- shape as those modules' own grant tables (see V258's
-- whse_portal_access_grants as the direct template), scoped to a
-- lease rather than a client since Property's tenants are individual
-- lessees, not companies.

CREATE TABLE prop_portal_access_grants (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    lease_id                    UUID NOT NULL REFERENCES leases (id),
    portal_user_id              UUID,
    invite_email                VARCHAR(255) NOT NULL,
    status                      VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    invite_token                VARCHAR(100) UNIQUE,
    invite_token_expires_at     TIMESTAMPTZ,
    invited_by                  UUID,
    invited_at                  TIMESTAMPTZ NOT NULL,
    accepted_at                 TIMESTAMPTZ,
    revoked_by                  UUID,
    revoked_at                  TIMESTAMPTZ
);

CREATE INDEX idx_prop_portal_grants_lease ON prop_portal_access_grants (tenant_id, lease_id);
CREATE INDEX idx_prop_portal_grants_user ON prop_portal_access_grants (portal_user_id) WHERE status = 'ACTIVE';

-- No new permission needed — PROPERTY_MANAGE already exists (seeded in
-- V269) and already gates every other write action in PropertyController.
-- Portal invite/revoke isn't meaningfully more sensitive than the rest
-- of lease management, unlike Fuel's margin report (V271), which
-- reveals wholesale cost and genuinely warranted its own tier.
