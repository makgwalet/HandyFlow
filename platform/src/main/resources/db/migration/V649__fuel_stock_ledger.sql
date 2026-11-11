-- Fuel Stage 1A: append-only stock ledger, depots, transfers, reservations.
-- The tank's current_litres stays as the fast balance; every change to it is
-- now ALSO a ledger line, and SUM(ledger) must equal the balance.

CREATE TABLE IF NOT EXISTS fuel_depots (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    name             VARCHAR(120) NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    address          VARCHAR(300),
    legal_entity_id  UUID,                 -- reserved for multi-entity (Stage 4); unused today
    active           BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_fuel_depots PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_depots_code ON fuel_depots (tenant_id, upper(code)) WHERE deleted_at IS NULL;

ALTER TABLE fuel_tanks ADD COLUMN IF NOT EXISTS depot_id UUID REFERENCES fuel_depots(id);
ALTER TABLE fuel_tanks ADD COLUMN IF NOT EXISTS safety_stock_litres NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE fuel_tanks DROP CONSTRAINT IF EXISTS chk_tank_safety_stock;
ALTER TABLE fuel_tanks ADD CONSTRAINT chk_tank_safety_stock CHECK (safety_stock_litres >= 0);

CREATE TABLE IF NOT EXISTS fuel_transfers (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID          NOT NULL,
    from_tank_id  UUID          NOT NULL REFERENCES fuel_tanks(id),
    to_tank_id    UUID          NOT NULL REFERENCES fuel_tanks(id),
    litres        NUMERIC(12,2) NOT NULL,
    reason        VARCHAR(500)  NOT NULL,
    created_by    UUID,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_fuel_transfers PRIMARY KEY (id),
    CONSTRAINT chk_fuel_transfer_litres CHECK (litres > 0),
    CONSTRAINT chk_fuel_transfer_tanks CHECK (from_tank_id <> to_tank_id)
);
CREATE INDEX IF NOT EXISTS idx_fuel_transfers_tenant ON fuel_transfers (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS fuel_stock_movements (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    tank_id        UUID          NOT NULL REFERENCES fuel_tanks(id),
    movement_type  VARCHAR(20)   NOT NULL,
    litres_delta   NUMERIC(12,2) NOT NULL,
    balance_after  NUMERIC(12,2) NOT NULL,
    source_type    VARCHAR(20),
    source_id      UUID,
    reason         VARCHAR(500),
    created_by     UUID,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_fuel_stock_movements PRIMARY KEY (id),
    CONSTRAINT chk_fuel_movement_type CHECK (movement_type IN
        ('OPENING','RECEIPT','DISPATCH','DELIVERY','ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','REVERSAL')),
    CONSTRAINT chk_fuel_movement_delta CHECK (litres_delta <> 0),
    CONSTRAINT chk_fuel_movement_balance CHECK (balance_after >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fuel_movements_tank ON fuel_stock_movements (tank_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fuel_movements_tenant ON fuel_stock_movements (tenant_id, created_at DESC);
-- The same source can post the same kind of movement only once: a retry can never double-post.
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_movement_source
    ON fuel_stock_movements (tenant_id, source_type, source_id, movement_type, tank_id) WHERE source_id IS NOT NULL;

-- Append-only: no edits, no deletes. Corrections are new lines.
CREATE OR REPLACE FUNCTION fuel_stock_movements_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'fuel_stock_movements is append-only';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_fuel_stock_movements_immutable ON fuel_stock_movements;
CREATE TRIGGER trg_fuel_stock_movements_immutable
    BEFORE UPDATE OR DELETE ON fuel_stock_movements
    FOR EACH ROW EXECUTE FUNCTION fuel_stock_movements_immutable();

-- One opening balance per existing tank so that SUM(ledger) = current_litres from day one.
INSERT INTO fuel_stock_movements (tenant_id, tank_id, movement_type, litres_delta, balance_after, source_type, reason, created_at)
SELECT t.tenant_id, t.id, 'OPENING', t.current_litres, t.current_litres, 'TANK',
       'Opening balance when the stock ledger was introduced', now()
FROM fuel_tanks t
WHERE t.current_litres > 0
  AND NOT EXISTS (SELECT 1 FROM fuel_stock_movements m WHERE m.tank_id = t.id);

CREATE TABLE IF NOT EXISTS fuel_reservations (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    tank_id         UUID          NOT NULL REFERENCES fuel_tanks(id),
    litres          NUMERIC(12,2) NOT NULL,
    customer_id     UUID,
    reference_type  VARCHAR(20),          -- DELIVERY today; SALES_ORDER from Stage 2
    reference_id    UUID,
    status          VARCHAR(12)   NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMPTZ,
    note            VARCHAR(300),
    created_by      UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_fuel_reservations PRIMARY KEY (id),
    CONSTRAINT chk_fuel_reservation_litres CHECK (litres > 0),
    CONSTRAINT chk_fuel_reservation_status CHECK (status IN ('ACTIVE','RELEASED','CONSUMED'))
);
CREATE INDEX IF NOT EXISTS idx_fuel_reservations_tank ON fuel_reservations (tank_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_fuel_reservations_tenant ON fuel_reservations (tenant_id, status);

-- Stock adjustments write off or add fuel without a physical movement, so they get their own permission
-- (same seeding pattern as V271): every tenant's ADMIN role receives it; other roles must be given it.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_STOCK_ADJUST', 'Correct fuel stock (stocktake write-offs and write-ons) with a reason')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'fuel', resource = 'stock', verb = 'ADJUST', sensitivity = 'FINANCIAL',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_STOCK_ADJUST';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'FUEL_STOCK_ADJUST'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
