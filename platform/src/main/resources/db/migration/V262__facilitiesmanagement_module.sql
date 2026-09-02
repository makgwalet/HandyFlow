-- ============================================================================
-- Module 5b: Facilities Management Company (outsourced FM provider)
-- VERSION NUMBER NOT CONFIRMED — this assumes V262 follows V261 (facilities)
-- sequentially. READ THE REAL FLYWAY MIGRATION HISTORY BEFORE APPLYING.
-- ============================================================================

CREATE TABLE fm_profiles (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    company_name          VARCHAR(255) NOT NULL,
    registration_number   VARCHAR(100),
    contact_email         VARCHAR(255),
    contact_phone         VARCHAR(50),
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_fm_profiles_tenant ON fm_profiles(tenant_id);

CREATE TABLE fm_clients (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    client_code           VARCHAR(50) NOT NULL,
    trading_name          VARCHAR(255) NOT NULL,
    registration_number   VARCHAR(100),
    contact_name          VARCHAR(255),
    contact_email         VARCHAR(255),
    contact_phone         VARCHAR(50),
    address               TEXT,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_fm_clients_tenant ON fm_clients(tenant_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_fm_clients_tenant_code ON fm_clients(tenant_id, client_code) WHERE deleted_at IS NULL;

CREATE TABLE fm_sites (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES fm_clients(id),
    name            VARCHAR(255) NOT NULL,
    site_type       VARCHAR(30) NOT NULL DEFAULT 'OFFICE',
    address         JSONB,
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_fm_sites_tenant ON fm_sites(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_sites_client ON fm_sites(client_id) WHERE deleted_at IS NULL;

CREATE TABLE fm_assets (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    site_id               UUID NOT NULL REFERENCES fm_sites(id),
    asset_tag             VARCHAR(100),
    name                  VARCHAR(255) NOT NULL,
    asset_type            VARCHAR(30) NOT NULL,
    location              VARCHAR(255),
    manufacturer          VARCHAR(255),
    model                 VARCHAR(255),
    serial_number         VARCHAR(255),
    install_date          DATE,
    warranty_expiry_date  DATE,
    criticality           VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status                VARCHAR(20) NOT NULL DEFAULT 'OPERATIONAL',
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_fm_assets_tenant ON fm_assets(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_assets_site ON fm_assets(site_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_fm_assets_tenant_tag ON fm_assets(tenant_id, asset_tag) WHERE deleted_at IS NULL AND asset_tag IS NOT NULL;

CREATE TABLE fm_technicians (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    contact_phone   VARCHAR(50),
    contact_email   VARCHAR(255),
    specialization  VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_fm_technicians_tenant ON fm_technicians(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE fm_vendors (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    company_name    VARCHAR(255) NOT NULL,
    service_type    VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    contact_name    VARCHAR(255),
    contact_phone   VARCHAR(50),
    contact_email   VARCHAR(255),
    notes           TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_fm_vendors_tenant ON fm_vendors(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE fm_ppm_schedules (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    asset_id              UUID NOT NULL REFERENCES fm_assets(id),
    task_name             VARCHAR(255) NOT NULL,
    description           TEXT,
    frequency_days        INTEGER NOT NULL,
    next_due_date         DATE NOT NULL,
    last_completed_date   DATE,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fm_ppm_frequency_positive CHECK (frequency_days > 0)
);
CREATE INDEX idx_fm_ppm_tenant ON fm_ppm_schedules(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_ppm_asset ON fm_ppm_schedules(asset_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_ppm_due_sweep ON fm_ppm_schedules(next_due_date) WHERE active = TRUE AND deleted_at IS NULL;

CREATE TABLE fm_work_orders (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    work_order_number     VARCHAR(50) NOT NULL,
    client_id             UUID NOT NULL REFERENCES fm_clients(id),
    site_id               UUID NOT NULL REFERENCES fm_sites(id),
    asset_id              UUID REFERENCES fm_assets(id),
    ppm_schedule_id       UUID REFERENCES fm_ppm_schedules(id),
    category              VARCHAR(20) NOT NULL,
    priority              VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    description           TEXT NOT NULL,
    reported_by           VARCHAR(255),
    technician_id         UUID REFERENCES fm_technicians(id),
    technician_name       VARCHAR(255),
    vendor_id             UUID REFERENCES fm_vendors(id),
    vendor_name           VARCHAR(255),
    scheduled_date        DATE,
    completed_at          TIMESTAMPTZ,
    completion_notes      TEXT,
    cost                  NUMERIC(15,2),
    invoiced              BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_reason   TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_fm_wo_tenant ON fm_work_orders(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_wo_client ON fm_work_orders(client_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_wo_site ON fm_work_orders(site_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_wo_asset ON fm_work_orders(asset_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fm_wo_overdue_sweep ON fm_work_orders(scheduled_date) WHERE status NOT IN ('COMPLETED','CANCELLED') AND deleted_at IS NULL;
CREATE INDEX idx_fm_wo_billable_sweep ON fm_work_orders(client_id, completed_at) WHERE status = 'COMPLETED' AND invoiced = FALSE AND deleted_at IS NULL;
CREATE UNIQUE INDEX uq_fm_wo_number ON fm_work_orders(work_order_number);

CREATE TABLE fm_service_agreements (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES fm_clients(id),
    billing_type    VARCHAR(30) NOT NULL,
    monthly_fee     NUMERIC(15,2),
    hourly_rate     NUMERIC(15,2),
    start_date      DATE NOT NULL,
    end_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fm_agreement_end_after_start CHECK (end_date IS NULL OR end_date > start_date)
);
CREATE INDEX idx_fm_agreements_tenant ON fm_service_agreements(tenant_id);
CREATE INDEX idx_fm_agreements_client ON fm_service_agreements(client_id);
CREATE INDEX idx_fm_agreements_expiry_sweep ON fm_service_agreements(end_date) WHERE status = 'ACTIVE' AND end_date IS NOT NULL;

CREATE TABLE fm_invoices (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES fm_clients(id),
    invoice_number  VARCHAR(50) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    issue_date      DATE NOT NULL,
    due_date        DATE NOT NULL,
    subtotal        NUMERIC(15,2) NOT NULL,
    vat_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total           NUMERIC(15,2) NOT NULL,
    amount_paid     NUMERIC(15,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at         TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fm_invoice_period_order CHECK (period_end >= period_start)
);
CREATE INDEX idx_fm_invoices_tenant ON fm_invoices(tenant_id);
CREATE INDEX idx_fm_invoices_client ON fm_invoices(client_id);
CREATE INDEX idx_fm_invoices_overdue_sweep ON fm_invoices(due_date) WHERE status IN ('SENT','PARTIAL');
CREATE UNIQUE INDEX uq_fm_invoices_number ON fm_invoices(invoice_number);

CREATE TABLE fm_portal_access_grants (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    client_id                 UUID NOT NULL REFERENCES fm_clients(id),
    portal_user_id            UUID,
    invite_email              VARCHAR(255) NOT NULL,
    status                    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_token              VARCHAR(255),
    invite_token_expires_at   TIMESTAMPTZ,
    invited_by                UUID,
    invited_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at               TIMESTAMPTZ,
    revoked_by                UUID,
    revoked_at                TIMESTAMPTZ
);
CREATE INDEX idx_fm_portal_grants_tenant ON fm_portal_access_grants(tenant_id);
CREATE INDEX idx_fm_portal_grants_client ON fm_portal_access_grants(client_id);
CREATE INDEX idx_fm_portal_grants_portal_user ON fm_portal_access_grants(portal_user_id);
CREATE UNIQUE INDEX uq_fm_portal_grants_invite_token ON fm_portal_access_grants(invite_token) WHERE invite_token IS NOT NULL;

-- ── Module catalogue + permissions ──────────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, category, monthly_price, is_active)
VALUES ('facilitiesmanagement', 'Facilities Management Company', 'Client sites and assets, planned maintenance, technician/vendor work orders, service agreements and billed invoicing for an outsourced facilities management provider.', 'Operations', 349.00, TRUE)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_READ',   'View clients, sites, assets, work orders, agreements and invoices'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_MANAGE', 'Manage clients, sites, assets, PPM schedules, work orders and service agreements'),
    (gen_random_uuid(), 'FACILITIESMANAGEMENT_ADMIN',  'Delete facilitiesmanagement records, generate invoices, record payments and revoke portal access')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('FACILITIESMANAGEMENT_READ', 'FACILITIESMANAGEMENT_MANAGE', 'FACILITIESMANAGEMENT_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
