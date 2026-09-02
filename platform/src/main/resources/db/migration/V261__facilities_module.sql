-- ============================================================================
-- Module 5a: Facilities & Maintenance (Internal)
-- VERSION NUMBER NOT CONFIRMED — this assumes V261 follows V260 (trainingprovider)
-- sequentially. READ THE REAL FLYWAY MIGRATION HISTORY BEFORE APPLYING.
-- ============================================================================

CREATE TABLE facility_sites (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    site_type       VARCHAR(30) NOT NULL DEFAULT 'OFFICE',
    address         JSONB,
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_facility_sites_tenant ON facility_sites(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE facility_assets (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    site_id               UUID NOT NULL REFERENCES facility_sites(id),
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
    deleted_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_facility_assets_tenant ON facility_assets(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_facility_assets_site ON facility_assets(site_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_facility_assets_tenant_tag ON facility_assets(tenant_id, asset_tag) WHERE deleted_at IS NULL AND asset_tag IS NOT NULL;

CREATE TABLE facility_technicians (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    contact_phone   VARCHAR(50),
    contact_email   VARCHAR(255),
    specialization  VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    linked_user_id  UUID,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_facility_technicians_tenant ON facility_technicians(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE facility_vendors (
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
CREATE INDEX idx_facility_vendors_tenant ON facility_vendors(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE facility_ppm_schedules (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    asset_id              UUID NOT NULL REFERENCES facility_assets(id),
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
    CONSTRAINT ck_facility_ppm_frequency_positive CHECK (frequency_days > 0)
);
CREATE INDEX idx_facility_ppm_tenant ON facility_ppm_schedules(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_facility_ppm_asset ON facility_ppm_schedules(asset_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_facility_ppm_due_sweep ON facility_ppm_schedules(next_due_date) WHERE active = TRUE AND deleted_at IS NULL;

CREATE TABLE facility_work_orders (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    work_order_number     VARCHAR(50) NOT NULL UNIQUE,
    site_id               UUID NOT NULL REFERENCES facility_sites(id),
    asset_id              UUID REFERENCES facility_assets(id),
    ppm_schedule_id       UUID REFERENCES facility_ppm_schedules(id),
    category              VARCHAR(20) NOT NULL,
    priority              VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    description           TEXT NOT NULL,
    reported_by           VARCHAR(255),
    technician_id         UUID REFERENCES facility_technicians(id),
    technician_name       VARCHAR(255),
    vendor_id             UUID REFERENCES facility_vendors(id),
    vendor_name           VARCHAR(255),
    scheduled_date        DATE,
    completed_at          TIMESTAMPTZ,
    completion_notes      TEXT,
    cost                  NUMERIC(15,2),
    cancellation_reason   TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_facility_wo_tenant ON facility_work_orders(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_facility_wo_site ON facility_work_orders(site_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_facility_wo_asset ON facility_work_orders(asset_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_facility_wo_overdue_sweep ON facility_work_orders(scheduled_date) WHERE status NOT IN ('COMPLETED','CANCELLED') AND deleted_at IS NULL;

CREATE TABLE facility_compliance_certificates (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    site_id             UUID NOT NULL REFERENCES facility_sites(id),
    asset_id            UUID REFERENCES facility_assets(id),
    certificate_type    VARCHAR(30) NOT NULL,
    certificate_number  VARCHAR(100),
    issued_by           VARCHAR(255),
    issue_date          DATE NOT NULL,
    expiry_date         DATE NOT NULL,
    document_ref        VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'VALID',
    revoked_reason      TEXT,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_facility_cert_expiry_after_issue CHECK (expiry_date >= issue_date)
);
CREATE INDEX idx_facility_cert_tenant ON facility_compliance_certificates(tenant_id);
CREATE INDEX idx_facility_cert_expiry_sweep ON facility_compliance_certificates(expiry_date) WHERE status = 'VALID';

-- ── Module catalogue + permissions ──────────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, category, monthly_price, is_active)
VALUES ('facilities', 'Facilities & Maintenance', 'Site and asset register, planned preventive maintenance, work orders, and compliance-certificate tracking.', 'Operations', 249.00, TRUE)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'FACILITIES_READ',   'View facilities, assets, work orders and certificates'),
    (gen_random_uuid(), 'FACILITIES_MANAGE', 'Manage sites, assets, PPM schedules and work orders'),
    (gen_random_uuid(), 'FACILITIES_ADMIN',  'Delete facilities records and revoke compliance certificates')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('FACILITIES_READ', 'FACILITIES_MANAGE', 'FACILITIES_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
