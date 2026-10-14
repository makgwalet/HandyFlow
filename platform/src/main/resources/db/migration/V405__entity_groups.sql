-- Finance F26: groups of companies. Every company stays its own tenant with its own books. A company becomes part of
-- a group only by accepting an invitation, and the group's parent can then read that company's account TOTALS (never its
-- journal lines) to consolidate. Leaving the group, or being removed, ends that access at once.
CREATE TABLE entity_groups (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(120) NOT NULL,
    parent_tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    created_by       UUID,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE entity_group_members (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID NOT NULL REFERENCES entity_groups(id) ON DELETE CASCADE,
    tenant_id         UUID NOT NULL UNIQUE REFERENCES tenants(id),   -- a company is in at most one group
    role              VARCHAR(12) NOT NULL CHECK (role IN ('PARENT','SUBSIDIARY')),
    ownership_percent NUMERIC(5,2) NOT NULL DEFAULT 100 CHECK (ownership_percent > 0 AND ownership_percent <= 100),
    joined_by         UUID,
    joined_at         TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_entity_group_members_group ON entity_group_members (group_id);

CREATE TABLE entity_group_invites (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID NOT NULL REFERENCES entity_groups(id) ON DELETE CASCADE,
    code_hash         VARCHAR(64) NOT NULL UNIQUE,                   -- SHA-256 of the code; the code is shown once
    label             VARCHAR(120),
    ownership_percent NUMERIC(5,2) NOT NULL DEFAULT 100 CHECK (ownership_percent > 0 AND ownership_percent <= 100),
    expires_at        TIMESTAMP NOT NULL,
    used_at           TIMESTAMP,
    used_by_tenant_id UUID REFERENCES tenants(id),
    created_by        UUID,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Companies with different charts of accounts map their account codes onto one group line.
CREATE TABLE entity_group_account_map (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id     UUID NOT NULL REFERENCES entity_groups(id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    account_code VARCHAR(20) NOT NULL,
    group_code   VARCHAR(20) NOT NULL,
    group_name   VARCHAR(120),
    UNIQUE (group_id, tenant_id, account_code)
);

-- Intercompany pairs to eliminate: an account in one company against its mirror in another.
CREATE TABLE entity_group_eliminations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID NOT NULL REFERENCES entity_groups(id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    tenant_a_id    UUID NOT NULL REFERENCES tenants(id),
    account_code_a VARCHAR(20) NOT NULL,
    tenant_b_id    UUID NOT NULL REFERENCES tenants(id),
    account_code_b VARCHAR(20) NOT NULL,
    created_by     UUID,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_elim_two_companies CHECK (tenant_a_id <> tenant_b_id)
);
CREATE INDEX idx_entity_group_elims_group ON entity_group_eliminations (group_id);
