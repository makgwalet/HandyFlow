-- V36__desk_support_module.sql
-- Desk Support — dual-purpose ticket system.
-- (1) HandyFlow internal: tenants log issues with HandyFlow support team.
-- (2) White-labelled: tenants use it as their own customer helpdesk.
--
-- WHY dual-purpose?
-- The same tables serve both use cases. A ticket belongs to a tenant.
-- The "channel" column distinguishes:
--   INTERNAL  = tenant logging issue with HandyFlow
--   HELPDESK  = tenant's customer logging issue with the tenant
--
-- The HandyFlow Admin Portal will be a special tenant that receives
-- all INTERNAL tickets across all tenants.

-- ── Ticket categories (tenant-defined) ───────────────────────────────────────
CREATE TABLE desk_categories (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    color       VARCHAR(20),    -- hex color for UI badge
    sort_order  INT         NOT NULL DEFAULT 0,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_desk_categories PRIMARY KEY (id),
    CONSTRAINT uq_desk_category UNIQUE (tenant_id, name)
);

CREATE INDEX idx_desk_categories_tenant ON desk_categories(tenant_id);

-- ── Tickets ───────────────────────────────────────────────────────────────────
CREATE TABLE desk_tickets (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    ticket_number   VARCHAR(20) NOT NULL,

    -- Channel: who is raising to whom
    channel         VARCHAR(20) NOT NULL DEFAULT 'HELPDESK'
        CHECK (channel IN ('INTERNAL', 'HELPDESK')),

    -- Requester (the person who raised the ticket)
    requester_name  VARCHAR(255) NOT NULL,
    requester_email VARCHAR(255),
    requester_phone VARCHAR(30),
    customer_id     UUID REFERENCES customers(id) ON DELETE SET NULL,  -- if linked to CRM

    -- Ticket content
    subject         VARCHAR(500) NOT NULL,
    description     TEXT        NOT NULL,
    category_id     UUID REFERENCES desk_categories(id) ON DELETE SET NULL,

    -- Priority and SLA
    priority        VARCHAR(10)  NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),

    -- Status lifecycle
    status          VARCHAR(30)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN (
            'OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER',
            'WAITING_ON_THIRD_PARTY', 'RESOLVED', 'CLOSED'
        )),

    -- Assignment
    assigned_to     UUID REFERENCES users(id) ON DELETE SET NULL,

    -- SLA tracking
    -- WHY store these timestamps? SLA clock pauses on WAITING_ON_CUSTOMER.
    -- We need to track actual time spent, not wall time.
    first_response_at   TIMESTAMP,
    resolved_at         TIMESTAMP,
    closed_at           TIMESTAMP,
    sla_breached        BOOLEAN NOT NULL DEFAULT false,
    due_at              TIMESTAMP,   -- SLA deadline based on priority

    -- Public portal (for HELPDESK channel)
    -- Customers can view their ticket status via a public URL
    public_token    VARCHAR(100) UNIQUE,   -- for customer to track ticket without login

    notes           TEXT,   -- internal staff notes (not visible to requester)
    tags            TEXT[],

    created_by      UUID REFERENCES users(id),   -- null if submitted via public portal
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_desk_tickets PRIMARY KEY (id),
    CONSTRAINT uq_desk_ticket_number UNIQUE (tenant_id, ticket_number)
);

CREATE INDEX idx_desk_tickets_tenant   ON desk_tickets(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_desk_tickets_status   ON desk_tickets(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_desk_tickets_priority ON desk_tickets(tenant_id, priority) WHERE deleted_at IS NULL;
CREATE INDEX idx_desk_tickets_assigned ON desk_tickets(assigned_to) WHERE deleted_at IS NULL;
CREATE INDEX idx_desk_tickets_token    ON desk_tickets(public_token) WHERE public_token IS NOT NULL;
CREATE INDEX idx_desk_tickets_channel  ON desk_tickets(tenant_id, channel);

-- ── Comments / replies ────────────────────────────────────────────────────────
-- WHY author_type? Distinguishes team replies (visible to customer)
-- from internal notes (staff-only). Customers can also reply.
CREATE TABLE desk_comments (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    ticket_id   UUID        NOT NULL REFERENCES desk_tickets(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    author_type VARCHAR(20) NOT NULL
        CHECK (author_type IN ('TEAM', 'CUSTOMER', 'SYSTEM')),
    is_internal BOOLEAN     NOT NULL DEFAULT false,  -- true = staff-only note
    body        TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_desk_comments PRIMARY KEY (id)
);

CREATE INDEX idx_desk_comments_ticket ON desk_comments(ticket_id);

-- ── Attachments ───────────────────────────────────────────────────────────────
CREATE TABLE desk_attachments (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    ticket_id   UUID        NOT NULL REFERENCES desk_tickets(id) ON DELETE CASCADE,
    comment_id  UUID REFERENCES desk_comments(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_type   VARCHAR(50),
    file_url    TEXT        NOT NULL,   -- base64
    file_size   BIGINT,
    uploaded_by VARCHAR(255),          -- name (not FK — could be customer)
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_desk_attachments PRIMARY KEY (id)
);

CREATE INDEX idx_desk_attachments_ticket ON desk_attachments(ticket_id);

-- ── SLA policies (per tenant, per priority) ───────────────────────────────────
-- WHY a table? Different tenants may want different SLA targets.
-- HandyFlow seeds defaults; tenants can override.
CREATE TABLE desk_sla_policies (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    priority            VARCHAR(10) NOT NULL CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    first_response_hours INT        NOT NULL,   -- SLA: time to first reply
    resolution_hours     INT        NOT NULL,   -- SLA: time to resolve
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_desk_sla PRIMARY KEY (id),
    CONSTRAINT uq_desk_sla UNIQUE (tenant_id, priority)
);

CREATE INDEX idx_desk_sla_tenant ON desk_sla_policies(tenant_id);

-- ── Module catalogue entry ────────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order)
VALUES (
    'desk',
    'Desk Support',
    'Customer support ticketing, SLA tracking, internal notes, public portal. Use as your own helpdesk or to log issues with HandyFlow.',
    249.00, 'headphones', 'OPERATIONS', 85
) ON CONFLICT (key) DO NOTHING;

-- Add to zeta-earthmoving pilot
INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'desk', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

-- ── Default SLA policies for zeta-earthmoving ────────────────────────────────
INSERT INTO desk_sla_policies (tenant_id, priority, first_response_hours, resolution_hours)
SELECT t.id, p.priority, p.first_response, p.resolution
FROM tenants t
CROSS JOIN (VALUES
    ('LOW',    48, 120),
    ('NORMAL', 24,  72),
    ('HIGH',    8,  24),
    ('URGENT',  2,   8)
) AS p(priority, first_response, resolution)
WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, priority) DO NOTHING;

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'DESK_READ',   'View support tickets'),
    (gen_random_uuid(), 'DESK_MANAGE', 'Create, assign and resolve support tickets'),
    (gen_random_uuid(), 'DESK_ADMIN',  'Manage SLA policies, categories and settings')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('DESK_READ', 'DESK_MANAGE', 'DESK_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
