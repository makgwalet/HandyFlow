-- V767: supplier-facing quotation submission.
-- A supplier's own staff are invited by email (single-use token, 7 days) and log in through the shared portal identity
-- (portal_users). What they can reach is decided only by this grant table: the supplier is taken from the grant, never from the
-- request. One grant = one person at one supplier of one company; the same person may hold grants at several.

CREATE TABLE IF NOT EXISTS sc_supplier_portal_grants (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID         NOT NULL,
    supplier_id              UUID         NOT NULL REFERENCES sc_suppliers(id),
    portal_user_id           UUID,
    invite_email             VARCHAR(255) NOT NULL,
    status                   VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    invite_token             VARCHAR(100),
    invite_token_expires_at  TIMESTAMPTZ,
    invited_by               UUID,
    invited_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    accepted_at              TIMESTAMPTZ,
    revoked_by               UUID,
    revoked_at               TIMESTAMPTZ,
    CONSTRAINT chk_sc_sup_grant_status CHECK (status IN ('PENDING','ACTIVE','REVOKED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sc_sup_grant_token ON sc_supplier_portal_grants (invite_token) WHERE invite_token IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sc_sup_grant_email ON sc_supplier_portal_grants (tenant_id, supplier_id, lower(invite_email)) WHERE status <> 'REVOKED';
CREATE INDEX IF NOT EXISTS idx_sc_sup_grant_user ON sc_supplier_portal_grants (portal_user_id) WHERE status = 'ACTIVE';
