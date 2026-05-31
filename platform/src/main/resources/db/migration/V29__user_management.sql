-- V29__user_management.sql
-- Adds phone, job_title, department to users.
-- Creates user_invitations table for invite-based onboarding.
-- WHY invitations instead of direct create-with-password?
-- The inviting admin doesn't set the new user's password.
-- The new user receives an email with a secure token and sets their own password.
-- This is the correct security pattern.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone        VARCHAR(30),
    ADD COLUMN IF NOT EXISTS job_title    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS department   VARCHAR(100);

-- Additional system permissions for user management UI
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'USER_INVITE',    'Invite new users to the tenant'),
    (gen_random_uuid(), 'USER_DEACTIVATE','Deactivate / reactivate users')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE user_invitations (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    job_title       VARCHAR(100),
    department      VARCHAR(100),
    role_id         UUID            NOT NULL,
    invited_by      UUID            NOT NULL,   -- user_id of the admin who sent invite
    token           VARCHAR(255)    NOT NULL,   -- secure random token sent in email link
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMP       NOT NULL,
    accepted_at     TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_invitations PRIMARY KEY (id),
    CONSTRAINT fk_inv_tenant    FOREIGN KEY (tenant_id)  REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_role      FOREIGN KEY (role_id)    REFERENCES roles(id),
    CONSTRAINT fk_inv_invited_by FOREIGN KEY (invited_by) REFERENCES users(id),
    CONSTRAINT uq_inv_token     UNIQUE (token),
    CONSTRAINT chk_inv_status   CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','CANCELLED'))
);

CREATE INDEX idx_inv_tenant   ON user_invitations(tenant_id);
CREATE INDEX idx_inv_token    ON user_invitations(token);
CREATE INDEX idx_inv_email    ON user_invitations(tenant_id, email);
