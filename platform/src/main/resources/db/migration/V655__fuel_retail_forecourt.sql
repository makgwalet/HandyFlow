-- Fuel Stage 5: retail forecourt. Pumps draw from a tank; an attendant shift records opening and closing meters
-- per pump, the takings by tender, and the variance. Stock leaves the tank (ledger) when the shift closes.

CREATE TABLE fuel_pumps (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    name             VARCHAR(40)   NOT NULL,
    tank_id          UUID          NOT NULL REFERENCES fuel_tanks (id),
    price_per_litre  NUMERIC(10,4) NOT NULL,
    last_meter       NUMERIC(14,2),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_pump_price CHECK (price_per_litre > 0),
    CONSTRAINT chk_fuel_pump_meter CHECK (last_meter IS NULL OR last_meter >= 0)
);
CREATE UNIQUE INDEX uq_fuel_pumps_name ON fuel_pumps (tenant_id, upper(name)) WHERE deleted_at IS NULL;
CREATE INDEX ix_fuel_pumps_tank ON fuel_pumps (tenant_id, tank_id);

CREATE TABLE fuel_shifts (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    shift_number      VARCHAR(30)   NOT NULL,
    attendant_name    VARCHAR(120)  NOT NULL,
    status            VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    opened_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    opened_by         UUID,
    closed_at         TIMESTAMPTZ,
    closed_by         UUID,
    cash_amount       NUMERIC(14,2),
    card_amount       NUMERIC(14,2),
    account_amount    NUMERIC(14,2),
    other_amount      NUMERIC(14,2),
    expected_amount   NUMERIC(14,2),
    litres_sold       NUMERIC(14,2),
    variance_amount   NUMERIC(14,2),
    variance_reason   VARCHAR(500),
    notes             VARCHAR(500),
    reversed_at       TIMESTAMPTZ,
    reversed_by       UUID,
    reverse_reason    VARCHAR(500),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_shift_status CHECK (status IN ('OPEN','CLOSED','REVERSED')),
    CONSTRAINT chk_fuel_shift_tenders CHECK (
        coalesce(cash_amount,0) >= 0 AND coalesce(card_amount,0) >= 0 AND coalesce(account_amount,0) >= 0 AND coalesce(other_amount,0) >= 0),
    CONSTRAINT uq_fuel_shift_number UNIQUE (tenant_id, shift_number)
);
CREATE INDEX ix_fuel_shifts_tenant ON fuel_shifts (tenant_id, opened_at DESC);

CREATE TABLE fuel_shift_readings (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    shift_id         UUID          NOT NULL REFERENCES fuel_shifts (id),
    pump_id          UUID          NOT NULL REFERENCES fuel_pumps (id),
    tank_id          UUID          NOT NULL REFERENCES fuel_tanks (id),
    fuel_type        VARCHAR(30)   NOT NULL,
    opening_meter    NUMERIC(14,2) NOT NULL,
    opening_gap      NUMERIC(14,2) NOT NULL DEFAULT 0,
    closing_meter    NUMERIC(14,2),
    test_litres      NUMERIC(14,2) NOT NULL DEFAULT 0,
    litres_sold      NUMERIC(14,2),
    price_per_litre  NUMERIC(10,4) NOT NULL,
    sales_value      NUMERIC(14,2),
    cost_value       NUMERIC(14,2),
    is_open          BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_fuel_reading_open CHECK (opening_meter >= 0),
    CONSTRAINT chk_fuel_reading_close CHECK (closing_meter IS NULL OR closing_meter >= opening_meter),
    CONSTRAINT chk_fuel_reading_test CHECK (test_litres >= 0),
    CONSTRAINT uq_fuel_reading_pump UNIQUE (shift_id, pump_id)
);
-- A pump can be on one open shift at a time, enforced by the database.
CREATE UNIQUE INDEX uq_fuel_reading_open_pump ON fuel_shift_readings (pump_id) WHERE is_open;
CREATE INDEX ix_fuel_readings_shift ON fuel_shift_readings (tenant_id, shift_id);

CREATE TABLE IF NOT EXISTS fuel_shift_sequences (
    tenant_id UUID NOT NULL,
    year      INT  NOT NULL,
    last_seq  INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_FORECOURT', 'Open and close forecourt shifts and record pump readings and takings')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'fuel', resource = 'forecourt', verb = 'EXECUTE', sensitivity = 'NORMAL',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_FORECOURT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'FUEL_FORECOURT'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
