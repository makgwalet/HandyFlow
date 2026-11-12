-- B04: supplier banking details. A bank account is never edited. A change is proposed, then verified or rejected by a
-- DIFFERENT person (the database enforces that), and only a verified account counts. Verifying a new account retires the old one.
-- B05: purchase requisitions: a request to buy fuel, approved through the Approvals engine, then turned into a draft purchase order.

-- Permissions. Only ADMIN is given them here; nobody else gains access to banking details by default.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_SUPPLIER_BANK_EDIT',   'Propose supplier bank details for fuel suppliers (a second person must verify them)'),
    (gen_random_uuid(), 'FUEL_SUPPLIER_BANK_VERIFY', 'Verify or reject proposed supplier bank details, not your own'),
    (gen_random_uuid(), 'FUEL_SUPPLIER_BANK_READ',   'See full supplier bank account numbers (everyone else sees the last four digits)')
ON CONFLICT (name) DO NOTHING;
UPDATE permissions SET module_key = 'fuel', resource = 'supplier_bank', verb = 'EDIT', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_SUPPLIER_BANK_EDIT';
UPDATE permissions SET module_key = 'fuel', resource = 'supplier_bank', verb = 'VERIFY', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_SUPPLIER_BANK_VERIFY';
UPDATE permissions SET module_key = 'fuel', resource = 'supplier_bank', verb = 'READ', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_SUPPLIER_BANK_READ';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('FUEL_SUPPLIER_BANK_EDIT', 'FUEL_SUPPLIER_BANK_VERIFY', 'FUEL_SUPPLIER_BANK_READ')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

CREATE TABLE fuel_supplier_bank_accounts (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    supplier_id      UUID NOT NULL REFERENCES fuel_suppliers(id),
    account_holder   VARCHAR(150) NOT NULL,
    bank_name        VARCHAR(100) NOT NULL,
    branch_code      VARCHAR(20),
    account_number   VARCHAR(30) NOT NULL,
    account_type     VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
    status           VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    reason           VARCHAR(300),
    proposed_by      UUID NOT NULL,
    proposed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by       UUID,
    decided_at       TIMESTAMPTZ,
    decision_note    VARCHAR(300),
    withdrawn_at     TIMESTAMPTZ,
    superseded_at    TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_bank_status CHECK (status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','WITHDRAWN','SUPERSEDED')),
    CONSTRAINT chk_fuel_bank_type CHECK (account_type IN ('CURRENT','SAVINGS','TRANSMISSION','OTHER')),
    CONSTRAINT chk_fuel_bank_account_digits CHECK (account_number ~ '^[0-9]{5,20}$'),
    CONSTRAINT chk_fuel_bank_decided CHECK (status NOT IN ('VERIFIED','REJECTED','SUPERSEDED') OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT chk_fuel_bank_second_person CHECK (decided_by IS NULL OR decided_by <> proposed_by),
    CONSTRAINT chk_fuel_bank_withdrawn CHECK (status <> 'WITHDRAWN' OR withdrawn_at IS NOT NULL)
);
CREATE INDEX idx_fuel_bank_supplier ON fuel_supplier_bank_accounts (tenant_id, supplier_id, proposed_at DESC);
-- At most one verified account and one waiting proposal per supplier.
CREATE UNIQUE INDEX uq_fuel_bank_verified ON fuel_supplier_bank_accounts (tenant_id, supplier_id) WHERE status = 'VERIFIED';
CREATE UNIQUE INDEX uq_fuel_bank_pending  ON fuel_supplier_bank_accounts (tenant_id, supplier_id) WHERE status = 'PENDING_VERIFICATION';

CREATE TABLE fuel_requisition_sequences (
    tenant_id UUID NOT NULL,
    year      INT  NOT NULL,
    last_seq  INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE fuel_purchase_requisitions (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    req_number           VARCHAR(20) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    fuel_type            VARCHAR(30) NOT NULL,
    litres               NUMERIC(14,2) NOT NULL,
    tank_id              UUID,
    supplier_id          UUID REFERENCES fuel_suppliers(id),
    estimated_price      NUMERIC(12,4),
    needed_by            DATE,
    reason               VARCHAR(300) NOT NULL,
    requested_by         UUID,
    approval_request_id  UUID,
    approval_note        VARCHAR(300),
    purchase_order_id    UUID REFERENCES fuel_purchase_orders(id),
    cancel_reason        VARCHAR(300),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_req_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','CONVERTED','CANCELLED')),
    CONSTRAINT chk_fuel_req_litres CHECK (litres > 0),
    CONSTRAINT chk_fuel_req_price CHECK (estimated_price IS NULL OR estimated_price >= 0),
    CONSTRAINT chk_fuel_req_converted CHECK (status <> 'CONVERTED' OR purchase_order_id IS NOT NULL),
    CONSTRAINT chk_fuel_req_cancelled CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)
);
CREATE UNIQUE INDEX uq_fuel_req_number ON fuel_purchase_requisitions (tenant_id, req_number);
CREATE INDEX idx_fuel_req_status ON fuel_purchase_requisitions (tenant_id, status, created_at DESC);
CREATE UNIQUE INDEX uq_fuel_req_po ON fuel_purchase_requisitions (purchase_order_id) WHERE purchase_order_id IS NOT NULL;
