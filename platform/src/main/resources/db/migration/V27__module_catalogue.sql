-- V27__module_catalogue.sql
-- WHY? Replace the flat features jsonb on plans with a proper module catalogue.
-- Tenants select modules at onboarding and can add/remove them later.
-- This enables per-module billing, trial periods, and self-service upgrades.

-- ── Module catalogue ───────────────────────────────────────────────────────
CREATE TABLE module_catalogue (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key           VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    description   TEXT,
    monthly_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency      VARCHAR(3)   NOT NULL DEFAULT 'ZAR',
    icon          VARCHAR(50),           -- lucide icon name for frontend
    category      VARCHAR(50),           -- OPERATIONS, FINANCE, PEOPLE, INDUSTRY
    active        BOOLEAN NOT NULL DEFAULT true,
    sort_order    INT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Tenant module subscriptions ────────────────────────────────────────────
CREATE TABLE tenant_modules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    module_key    VARCHAR(50) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'TRIAL'
        CHECK (status IN ('TRIAL','ACTIVE','SUSPENDED','CANCELLED')),
    trial_ends_at TIMESTAMP,
    activated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    cancelled_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, module_key)
);

CREATE INDEX idx_tenant_modules_tenant ON tenant_modules(tenant_id, status);
CREATE INDEX idx_tenant_modules_key    ON tenant_modules(module_key);

-- ── Seed module catalogue ──────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order) VALUES
-- Industry modules
('security',     'Security & Guarding',        'Guard management, sites, shifts, QR patrol, incident reporting',      349.00, 'shield',        'INDUSTRY',    10),
('fuel',         'Fuel & Deliveries',           'Fuel tank management, delivery orders, driver dispatch, supplier tracking', 349.00, 'droplet',  'INDUSTRY',    20),
('earthmoving',  'Earthmoving & Plant Hire',    'Asset register, maintenance schedules, operator logs, utilisation',   499.00, 'truck',         'INDUSTRY',    30),
('fleet',        'Fleet Management',            'Vehicle register, trip log, service history, odometer tracking',      299.00, 'car',           'INDUSTRY',    40),
('property',     'Property & Leasing',          'Properties, units, lease agreements, rent collection, inspections',   399.00, 'building-2',    'INDUSTRY',    50),
('clinic',       'Clinic & Medical',            'Patient records, appointments, consultations, prescriptions',         749.00, 'stethoscope',   'INDUSTRY',    60),
('bookings',     'Bookings & Appointments',     'Service booking, staff availability, slot engine, QR check-in',       299.00, 'calendar',      'INDUSTRY',    70),
('events',       'Events & Ticketing',          'Event management, ticket tiers, guest registration, QR check-in',     299.00, 'ticket',        'INDUSTRY',    80),
-- Finance & Operations
('invoicing',    'Invoicing & Quotes',          'Quotes, invoices, PDF generation, VAT-compliant, email delivery',     199.00, 'file-text',     'FINANCE',     110),
('accounting',   'Accounting & Finance',        'Chart of accounts, journal entries, bank reconciliation, VAT returns', 599.00, 'calculator',   'FINANCE',     120),
('expenses',     'Expense Management',          'Staff expense claims, approval workflow, accounting integration',      199.00, 'receipt',       'FINANCE',     130),
('contracting',  'Contracts & Signing',         'Contract templates, OTP e-signing, ECTA compliant, PDF generation',   349.00, 'pen-tool',      'FINANCE',     140),
-- People
('hr',           'HR & Payroll',                'Employee records, leave management, PAYE payroll, payslips, EMP201',  499.00, 'users',         'PEOPLE',      210);

-- ── Migrate existing tenant to have all modules (zeta-earthmoving pilot) ──
-- WHY? The existing test tenant has all features enabled via plans.features jsonb.
-- We migrate them to tenant_modules so they keep working during the transition.
INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT
    t.id,
    m.key,
    'TRIAL',
    NOW() + INTERVAL '60 days'
FROM tenants t
CROSS JOIN module_catalogue m
WHERE t.slug = 'zeta-earthmoving';