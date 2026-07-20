-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Foundational schema for the accountant module's client portal —
-- closes the "client portal" gap from the module audit. This is the
-- FIRST real client-facing login system anywhere in this platform,
-- confirmed directly: Recruiter and Desk both reserve a
-- /api/v1/{module}/portal/** namespace in SecurityConfig.java but
-- neither has an actual login/session system behind it yet.
--
-- Two tables, deliberately split by scope:
--
-- portal_users — SHARED, module-agnostic identity. Email/password only,
-- nothing accountant-specific. A person who is both an accounting
-- client and (eventually) a recruiter candidate can use one login.
-- Password hashing reuses the exact same BCrypt PasswordEncoder bean
-- already defined in SecurityConfig.java — not a new hashing scheme.
--
-- acc_portal_access_grants — accountant-specific, with a real FOREIGN
-- KEY into acc_clients. This is the actual isolation mechanism: there
-- is no shared "module access" table anywhere. Accountant module code
-- only ever queries this table; it has no code path that could even
-- theoretically reach another module's grants, because that table
-- doesn't exist in this module's vocabulary. A future module (e.g.
-- Recruiter) would get its own equivalent grants table with its own FK
-- into its own client/candidate table — not a shared one.
--
-- Registration is invite-only, not open sign-up (this is B2B access to
-- a client's own financial data, granted by the firm, not self-
-- asserted) — matching the same token-based, no-prior-login pattern
-- already used by acc_engagement_letters.signing_token. A grant starts
-- PENDING with portal_user_id NULL (invited by email, before the
-- person has registered), and gets linked to a real portal_user once
-- the invite is accepted — either a brand new portal_user, or an
-- existing one if they already have portal access to a different
-- client or module.

CREATE TABLE portal_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE acc_portal_access_grants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    portal_user_id          UUID REFERENCES portal_users(id),
    client_id               UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    invite_email            VARCHAR(255) NOT NULL,
    status                  VARCHAR(15) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
    invite_token            VARCHAR(255) UNIQUE,
    invite_token_expires_at TIMESTAMPTZ,
    invited_by              UUID,
    invited_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at             TIMESTAMPTZ,
    revoked_by              UUID,
    revoked_at              TIMESTAMPTZ
);

-- Backs the one check every portal endpoint makes: "does this portal
-- user have an ACTIVE grant for this specific client?"
CREATE INDEX idx_acc_portal_grants_user_client
    ON acc_portal_access_grants (portal_user_id, client_id, status);

-- Backs invite-acceptance lookup by token.
CREATE INDEX idx_acc_portal_grants_invite_token
    ON acc_portal_access_grants (invite_token) WHERE invite_token IS NOT NULL;

-- Backs the staff-side "who has portal access to this client" view.
CREATE INDEX idx_acc_portal_grants_client
    ON acc_portal_access_grants (tenant_id, client_id);