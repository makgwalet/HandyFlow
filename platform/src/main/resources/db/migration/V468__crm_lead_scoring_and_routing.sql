-- V468: lead scoring rules (points when a lead matches a condition) and routing rules (who gets a new unassigned lead).
CREATE TABLE crm_scoring_rules (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(120) NOT NULL,
    field       VARCHAR(20)  NOT NULL,
    op          VARCHAR(10)  NOT NULL,
    value       VARCHAR(100) NOT NULL,
    points      INTEGER      NOT NULL CHECK (points BETWEEN -100 AND 100 AND points <> 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_crm_scoring_rules_tenant ON crm_scoring_rules (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_routing_rules (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    name         VARCHAR(120) NOT NULL,
    position     INTEGER      NOT NULL,                 -- rules are tried in this order; the first match wins
    field        VARCHAR(20),                           -- null = matches every lead
    op           VARCHAR(10),
    value        VARCHAR(100),
    assignee_ids TEXT         NOT NULL,                 -- comma-separated user ids, handed out in turn
    next_index   INTEGER      NOT NULL DEFAULT 0,       -- round-robin pointer
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT chk_crm_routing_condition CHECK ((field IS NULL AND op IS NULL AND value IS NULL) OR (field IS NOT NULL AND op IS NOT NULL AND value IS NOT NULL))
);
CREATE INDEX idx_crm_routing_rules_tenant ON crm_routing_rules (tenant_id, position) WHERE deleted_at IS NULL;
