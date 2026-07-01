-- =============================================================================
-- V121__security_phase4.sql
-- Phase 4 — Payroll Export · Multi-Branch Roles · Public API & Webhooks
--
-- Three tracks, all additive (no existing columns touched, no data dropped):
--
-- TRACK A — PAYROLL EXPORT
--   Guard hourly rates (grade-based with override) on security_guards.
--   security_payroll_periods — a supervisor-approved pay period window
--     (weekly/biweekly/monthly) that locks in which shifts count.
--   security_payroll_line_items — one row per completed shift per guard,
--     computed at approval time: hours × rate = amount. Overtime handled as
--     a separate line item when shift duration > standard_hours_per_day.
--   Export endpoint produces CSV or JSON; this migration builds the tables.
--
-- TRACK B — MULTI-BRANCH / REGIONAL MANAGER SCOPING
--   security_branches — a named sub-division of a tenant (region, city,
--     division). Sites are assigned to a branch; guards assigned to a branch
--     via their primary branch. A regional manager sees only their branch.
--   security_branch_assignments — many-to-many: a guard/manager can cover
--     multiple branches (matrix security companies with floating staff).
--   user_branch_scope — links platform users (USER table) to branch scope.
--     NULL branch_id means tenant-wide access (existing behaviour preserved).
--
-- TRACK C — PUBLIC API & WEBHOOK LAYER
--   security_api_keys — per-tenant API keys for client BI tools or third-party
--     integrations. Scoped to specific endpoint prefixes (read-only by default).
--   security_webhook_subscriptions — per-tenant webhook endpoints subscribed
--     to specific event types (ALARM_EVENT, DISPATCH, INCIDENT, SHIFT_MISSED,
--     PATROL_ROUND_MISSED). HandyFlow POSTs a signed payload to the client URL
--     when any matching event fires. Same HMAC pattern as checkpoint QR secrets.
--   security_webhook_deliveries — delivery log (attempt, response, retry).
-- =============================================================================

-- =============================================================================
-- TRACK A: PAYROLL EXPORT
-- =============================================================================

-- Guard pay rate — grade-based with optional per-guard override.
-- WHY on security_guards rather than a separate table?
-- A guard's rate is a 1:1 attribute — there's never more than one active rate
-- per guard at a time. The effective_from column handles rate changes: when
-- a guard gets a pay increase, a new row in security_guard_rate_history
-- captures the old rate, and the current rate stays on the guard record.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS hourly_rate_cents   INTEGER,     -- ZAR cents to avoid float
    ADD COLUMN IF NOT EXISTS rate_effective_from DATE;        -- when this rate took effect

-- Grade-level default rates (tenant-configurable) — the fallback when a guard
-- has no explicit hourly_rate_cents set.
CREATE TABLE IF NOT EXISTS security_grade_rates (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    grade       VARCHAR(5)  NOT NULL,   -- A | B | C | D | E
    hourly_rate_cents INTEGER NOT NULL,  -- ZAR cents
    standard_hours_per_day INTEGER NOT NULL DEFAULT 9,
    effective_from DATE NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,

    CONSTRAINT uq_grade_rate_tenant_grade_date
        UNIQUE (tenant_id, grade, effective_from)
);

CREATE INDEX IF NOT EXISTS idx_grade_rates_tenant
    ON security_grade_rates(tenant_id, grade, effective_from DESC);

-- Rate change history — append-only log of every rate change per guard.
CREATE TABLE IF NOT EXISTS security_guard_rate_history (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    guard_id        UUID        NOT NULL REFERENCES security_guards(id),
    old_rate_cents  INTEGER,
    new_rate_cents  INTEGER     NOT NULL,
    effective_from  DATE        NOT NULL,
    reason          TEXT,
    changed_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Payroll period — the approved window that determines which shifts are paid.
-- A period must be APPROVED before line items can be exported/paid.
-- Lifecycle: DRAFT → APPROVED → EXPORTED → PAID
CREATE TABLE IF NOT EXISTS security_payroll_periods (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    branch_id       UUID,               -- NULL = all branches; set for branch-scoped payroll
    name            VARCHAR(100) NOT NULL,  -- e.g. "June 2026 - Week 2"
    period_type     VARCHAR(20) NOT NULL
        CHECK (period_type IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY')),
    period_start    DATE        NOT NULL,
    period_end      DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'EXPORTED', 'PAID')),
    total_hours     DECIMAL(10,2),      -- computed at approval
    total_amount_cents BIGINT,          -- computed at approval
    approved_by     UUID,
    approved_at     TIMESTAMPTZ,
    exported_at     TIMESTAMPTZ,
    export_format   VARCHAR(10),        -- CSV | JSON
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_payroll_period_tenant_dates
        UNIQUE (tenant_id, period_start, period_end, branch_id)
);

-- Payroll line items — one row per completed shift, created when period is approved.
-- Overtime is a separate line item: same shift_id, line_type='OVERTIME'.
CREATE TABLE IF NOT EXISTS security_payroll_line_items (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    period_id       UUID        NOT NULL REFERENCES security_payroll_periods(id),
    guard_id        UUID        NOT NULL REFERENCES security_guards(id),
    shift_id        UUID        NOT NULL REFERENCES security_shifts(id),

    line_type       VARCHAR(20) NOT NULL DEFAULT 'REGULAR'
        CHECK (line_type IN ('REGULAR', 'OVERTIME', 'ALLOWANCE', 'DEDUCTION')),

    -- Shift snapshot at approval time (avoids drift if shift is later edited)
    shift_start_at  TIMESTAMPTZ NOT NULL,
    shift_end_at    TIMESTAMPTZ NOT NULL,
    hours_worked    DECIMAL(8,2) NOT NULL,
    overtime_hours  DECIMAL(8,2) NOT NULL DEFAULT 0,

    hourly_rate_cents   INTEGER NOT NULL,
    overtime_rate_cents INTEGER NOT NULL DEFAULT 0,   -- typically 1.5× regular
    gross_amount_cents  INTEGER NOT NULL,

    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_line_item_period_shift_type
        UNIQUE (period_id, shift_id, line_type)
);

CREATE INDEX IF NOT EXISTS idx_payroll_lines_period
    ON security_payroll_line_items(period_id);
CREATE INDEX IF NOT EXISTS idx_payroll_lines_guard
    ON security_payroll_line_items(tenant_id, guard_id, created_at DESC);

-- =============================================================================
-- TRACK B: MULTI-BRANCH / REGIONAL MANAGER SCOPING
-- =============================================================================

-- A branch is a named sub-division of a tenant: geographic region, city office,
-- service division (Retail Security vs Industrial Security vs VIP/CP).
-- WHY not sub-tenants? Sub-tenants would mean separate login, separate billing,
-- separate config — that's a different product tier. Branches share a tenant's
-- guards, billing, and config but scope visibility for regional managers.
CREATE TABLE IF NOT EXISTS security_branches (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    name        VARCHAR(150) NOT NULL,
    region      VARCHAR(100),   -- e.g. "Gauteng", "Western Cape"
    description TEXT,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_branch_name_tenant UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_branches_tenant
    ON security_branches(tenant_id) WHERE active = true;

-- Assign a site to a branch. A site belongs to exactly one branch (or none,
-- meaning it's a tenant-wide / unscoped site visible to all managers).
ALTER TABLE security_sites
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES security_branches(id);

CREATE INDEX IF NOT EXISTS idx_sites_branch
    ON security_sites(branch_id) WHERE branch_id IS NOT NULL;

-- Assign a guard's primary branch. Guards can float across branches via
-- security_branch_assignments, but the primary_branch_id on the guard record
-- determines their home branch for payroll and scheduling defaults.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS primary_branch_id UUID REFERENCES security_branches(id);

-- Many-to-many: guards and users can be assigned to multiple branches.
-- role: MANAGER = regional manager scoped to this branch,
--       GUARD   = floating guard available to this branch,
--       VIEWER  = read-only access to this branch's data.
CREATE TABLE IF NOT EXISTS security_branch_assignments (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    branch_id   UUID        NOT NULL REFERENCES security_branches(id),
    entity_type VARCHAR(20) NOT NULL
        CHECK (entity_type IN ('GUARD', 'USER')),
    entity_id   UUID        NOT NULL,   -- guard_id or user_id depending on entity_type
    role        VARCHAR(20) NOT NULL DEFAULT 'GUARD'
        CHECK (role IN ('MANAGER', 'GUARD', 'VIEWER')),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by UUID,
    active      BOOLEAN     NOT NULL DEFAULT true,

    CONSTRAINT uq_branch_assignment UNIQUE (branch_id, entity_type, entity_id)
);

CREATE INDEX IF NOT EXISTS idx_branch_assignments_entity
    ON security_branch_assignments(tenant_id, entity_id, entity_type)
    WHERE active = true;

-- =============================================================================
-- TRACK C: PUBLIC API & WEBHOOK LAYER
-- =============================================================================

-- API keys for client BI tools and third-party integrations.
-- WHY separate from JWT? Clients (site owners, BI tools) need machine-to-machine
-- access that doesn't expire every 24 hours and isn't tied to a user session.
-- API keys are long-lived, scoped, and revocable independently of user accounts.
--
-- scope_prefixes: JSON array of path prefixes the key is allowed to call.
-- Example: ["/api/v1/security/reports", "/api/v1/security/sites"]
-- An empty array = no access. NULL = full read access (tenant-scoped).
CREATE TABLE IF NOT EXISTS security_api_keys (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    name            VARCHAR(100) NOT NULL,   -- "Acme BI Integration", "SAPS Reporting Feed"
    key_hash        VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 of the actual key; key shown once at creation
    key_prefix      VARCHAR(12)  NOT NULL,   -- first 8 chars of key for display ("hf_live_a3b...")
    scope_prefixes  JSONB,                   -- allowed path prefixes; NULL = read-all
    branch_id       UUID         REFERENCES security_branches(id),  -- NULL = all branches
    read_only       BOOLEAN      NOT NULL DEFAULT true,
    active          BOOLEAN      NOT NULL DEFAULT true,
    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,             -- NULL = no expiry
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID,
    revocation_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_api_keys_tenant
    ON security_api_keys(tenant_id) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_api_keys_hash
    ON security_api_keys(key_hash) WHERE active = true;

-- Webhook subscriptions — where to POST events and which events to deliver.
-- event_types: JSONB array of event type strings the subscriber wants.
-- Supported types: ALARM_EVENT | DISPATCH_CREATED | DISPATCH_RESOLVED |
--   INCIDENT_CREATED | INCIDENT_RESOLVED | SHIFT_MISSED | PATROL_ROUND_MISSED |
--   DURESS_TRIGGERED | GUARD_SCREENING_DUE | PSIRA_EXPIRY_WARNING
CREATE TABLE IF NOT EXISTS security_webhook_subscriptions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    name            VARCHAR(100) NOT NULL,
    endpoint_url    TEXT        NOT NULL,
    signing_secret  VARCHAR(100) NOT NULL,   -- HMAC-SHA256 secret for payload signing
    event_types     JSONB       NOT NULL,    -- ["ALARM_EVENT","SHIFT_MISSED"]
    branch_id       UUID        REFERENCES security_branches(id),  -- NULL = all branches
    active          BOOLEAN     NOT NULL DEFAULT true,
    failure_count   INTEGER     NOT NULL DEFAULT 0,  -- consecutive failures; suspend at 10
    suspended_at    TIMESTAMPTZ,             -- set when failure_count >= 10
    last_success_at TIMESTAMPTZ,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_webhooks_tenant_active
    ON security_webhook_subscriptions(tenant_id) WHERE active = true;

-- Webhook delivery log — immutable record of every delivery attempt.
-- WHY store attempts? Clients need evidence of delivery for SLA/compliance.
-- The retry scheduler uses this to find failed deliveries for re-attempt.
CREATE TABLE IF NOT EXISTS security_webhook_deliveries (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    subscription_id     UUID        NOT NULL REFERENCES security_webhook_subscriptions(id),
    event_type          VARCHAR(50) NOT NULL,
    event_id            UUID        NOT NULL,   -- ID of the source entity
    payload_hash        VARCHAR(64),            -- SHA-256 of delivered payload
    attempt_number      INTEGER     NOT NULL DEFAULT 1,
    http_status         INTEGER,                -- response status from client endpoint
    response_body       TEXT,                   -- first 1KB of response
    delivered_at        TIMESTAMPTZ,            -- NULL if not yet delivered
    failed_at           TIMESTAMPTZ,
    failure_reason      TEXT,
    next_retry_at       TIMESTAMPTZ,            -- NULL if succeeded or exhausted
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_subscription
    ON security_webhook_deliveries(subscription_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_retry
    ON security_webhook_deliveries(next_retry_at)
    WHERE next_retry_at IS NOT NULL AND delivered_at IS NULL;
