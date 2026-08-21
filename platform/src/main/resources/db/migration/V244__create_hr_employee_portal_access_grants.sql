-- backlog 3.4 — Employee Self-Service Portal
-- Direct structural mirror of pay_portal_access_grants /
-- acc_portal_access_grants / auditor_access_grants — same shape, same
-- 7-day invite-token expiry convention. employee_id replaces
-- pay_client_id/client_id: this grant is scoped to exactly ONE employee
-- record, not a whole client's data, since the access shape here is
-- "one person, their own record" rather than "one business contact, all
-- of their company's data."
CREATE TABLE hr_employee_portal_access_grants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    employee_id             UUID NOT NULL,
    portal_user_id          UUID,
    invite_email            VARCHAR(255) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_token            VARCHAR(255) UNIQUE,
    invite_token_expires_at TIMESTAMPTZ,
    invited_by              UUID,
    invited_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at             TIMESTAMPTZ,
    revoked_by              UUID,
    revoked_at              TIMESTAMPTZ
);

CREATE INDEX idx_hr_portal_grants_tenant ON hr_employee_portal_access_grants (tenant_id);
CREATE INDEX idx_hr_portal_grants_employee ON hr_employee_portal_access_grants (employee_id);