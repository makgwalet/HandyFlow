-- Fuel Stage 2: sales orders (quote -> confirmed -> delivered), loading record,
-- invoice hand-off state on deliveries, and the credit override permission.

CREATE TABLE IF NOT EXISTS fuel_order_sequences (
    tenant_id UUID NOT NULL,
    year      INT  NOT NULL,
    last_seq  INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE IF NOT EXISTS fuel_sales_orders (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID          NOT NULL,
    order_number          VARCHAR(20)   NOT NULL,
    account_id            UUID          NOT NULL REFERENCES fuel_customer_accounts(id),
    crm_customer_id       UUID          NOT NULL,
    site_id               UUID          REFERENCES fuel_customer_sites(id),
    fuel_type             VARCHAR(30)   NOT NULL,
    litres_ordered        NUMERIC(12,2) NOT NULL,
    price_per_litre       NUMERIC(10,4) NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'QUOTE',
    requested_date        DATE,
    quote_valid_until     DATE,
    tank_id               UUID          REFERENCES fuel_tanks(id),
    credit_override_reason VARCHAR(300),
    cancel_reason         VARCHAR(300),
    customer_reference    VARCHAR(60),
    notes                 VARCHAR(500),
    created_by            UUID,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_order_litres CHECK (litres_ordered > 0),
    CONSTRAINT chk_fuel_order_price  CHECK (price_per_litre >= 0),
    CONSTRAINT chk_fuel_order_status CHECK (status IN ('QUOTE','CONFIRMED','PART_DELIVERED','DELIVERED','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_order_number ON fuel_sales_orders (tenant_id, order_number);
CREATE INDEX IF NOT EXISTS ix_fuel_order_tenant_status ON fuel_sales_orders (tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_fuel_order_account ON fuel_sales_orders (tenant_id, account_id);

ALTER TABLE fuel_deliveries
    ADD COLUMN IF NOT EXISTS order_id           UUID REFERENCES fuel_sales_orders(id),
    ADD COLUMN IF NOT EXISTS loaded_litres      NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS loaded_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS seal_numbers       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS invoice_state      VARCHAR(12) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS invoice_id         UUID,
    ADD COLUMN IF NOT EXISTS invoice_number     VARCHAR(40),
    ADD COLUMN IF NOT EXISTS invoiced_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS invoice_error      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS invoice_claimed_at TIMESTAMPTZ;

ALTER TABLE fuel_deliveries
    ADD CONSTRAINT chk_fuel_delivery_loaded CHECK (loaded_litres IS NULL OR loaded_litres > 0),
    ADD CONSTRAINT chk_fuel_delivery_invstate CHECK (invoice_state IN ('NONE','INVOICING','INVOICED','FAILED'));
CREATE INDEX IF NOT EXISTS ix_fuel_delivery_order ON fuel_deliveries (tenant_id, order_id);
CREATE INDEX IF NOT EXISTS ix_fuel_delivery_uninvoiced ON fuel_deliveries (tenant_id, status, invoice_state);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_CREDIT_OVERRIDE', 'Confirm a fuel order that takes the customer over their credit limit')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'fuel', resource = 'credit', verb = 'OVERRIDE', sensitivity = 'FINANCIAL',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_CREDIT_OVERRIDE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'FUEL_CREDIT_OVERRIDE'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
