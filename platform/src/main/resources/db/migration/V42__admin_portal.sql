-- V42__admin_portal.sql
-- Superadmin portal — separate auth from tenant users.
-- WHY separate table? Admin users are HandyFlow staff, not tenants.
-- They have no tenant_id and their JWT carries ROLE_SUPERADMIN claim.
-- TOTP (Google Authenticator) required on every login — no exceptions.

CREATE TABLE admin_users (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,   -- bcrypt
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(30)  NOT NULL DEFAULT 'SUPERADMIN'
        CHECK (role IN ('SUPERADMIN','SUPPORT','FINANCE','READ_ONLY')),
    -- TOTP
    totp_secret     VARCHAR(100),            -- base32 encoded TOTP secret
    totp_enabled    BOOLEAN     NOT NULL DEFAULT false,
    totp_verified_at TIMESTAMP,             -- when TOTP was first successfully verified
    -- Session tracking
    last_login_at   TIMESTAMP,
    last_login_ip   VARCHAR(45),
    failed_attempts INT         NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_admin_users PRIMARY KEY (id)
);

-- ── Impersonation sessions ────────────────────────────────────────────────────
-- WHY a table? Every impersonation session must be logged and auditable.
-- Impersonation is read-only — admin can VIEW what tenant sees, not modify.
CREATE TABLE admin_impersonation_sessions (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    admin_user_id   UUID        NOT NULL REFERENCES admin_users(id),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    admin_email     VARCHAR(255) NOT NULL,
    tenant_slug     VARCHAR(100) NOT NULL,
    reason          TEXT,
    started_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMP,
    ip_address      VARCHAR(45),

    CONSTRAINT pk_admin_impersonation PRIMARY KEY (id)
);

CREATE INDEX idx_admin_impersonation_admin  ON admin_impersonation_sessions(admin_user_id);
CREATE INDEX idx_admin_impersonation_tenant ON admin_impersonation_sessions(tenant_id);
CREATE INDEX idx_admin_impersonation_time   ON admin_impersonation_sessions(started_at DESC);

-- ── Superadmin audit log ──────────────────────────────────────────────────────
-- Every superadmin action is logged here. Immutable — no UPDATE or DELETE.
CREATE TABLE admin_audit_log (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    admin_user_id   UUID        NOT NULL REFERENCES admin_users(id),
    admin_email     VARCHAR(255) NOT NULL,
    action          VARCHAR(100) NOT NULL,  -- e.g. EXTEND_PILOT, SUSPEND_TENANT
    target_type     VARCHAR(30),            -- TENANT, MODULE, ADMIN_USER
    target_id       VARCHAR(100),           -- UUID or slug of the target
    target_name     VARCHAR(255),           -- human-readable name for display
    details         JSONB,                  -- additional context
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_admin_audit_admin  ON admin_audit_log(admin_user_id);
CREATE INDEX idx_admin_audit_time   ON admin_audit_log(created_at DESC);
CREATE INDEX idx_admin_audit_target ON admin_audit_log(target_type, target_id);

-- ── Announcements ─────────────────────────────────────────────────────────────
CREATE TABLE admin_announcements (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,
    body            TEXT        NOT NULL,
    type            VARCHAR(20)  NOT NULL DEFAULT 'INFO'
        CHECK (type IN ('INFO','WARNING','MAINTENANCE','NEW_FEATURE')),
    -- Targeting
    target_all      BOOLEAN     NOT NULL DEFAULT true,
    target_slugs    TEXT[],                 -- specific tenant slugs, null = all
    -- Scheduling
    publish_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP,
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_by      UUID        REFERENCES admin_users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_admin_announcements PRIMARY KEY (id)
);

CREATE INDEX idx_admin_announcements_active ON admin_announcements(active, publish_at);

-- ── Seed default superadmin ───────────────────────────────────────────────────
-- WHY seed here? Need at least one admin to log in and create others.
-- Password: Admin@HandyFlow2026! (bcrypt hash below — CHANGE ON FIRST LOGIN)
-- TOTP: disabled by default — enable on first login via /api/v1/admin/auth/totp/setup
INSERT INTO admin_users (email, password_hash, full_name, role, totp_enabled)
VALUES (
    'superadmin@handyflow.co.za',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBpj6SvBq3rBqe',
    'HandyFlow Superadmin',
    'SUPERADMIN',
    false
);
