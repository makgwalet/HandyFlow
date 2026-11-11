-- Fuel Stage 3: tankers with compartments, driver/tanker allocation on deliveries,
-- duplicate-safe completion (client request id) and the driver permission.

CREATE TABLE IF NOT EXISTS fuel_tankers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    registration VARCHAR(20)  NOT NULL,
    name         VARCHAR(80),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_tanker_reg ON fuel_tankers (tenant_id, upper(registration)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS fuel_tanker_compartments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    tanker_id       UUID          NOT NULL REFERENCES fuel_tankers(id),
    number          INT           NOT NULL,
    capacity_litres NUMERIC(10,2) NOT NULL,
    CONSTRAINT chk_fuel_comp_capacity CHECK (capacity_litres > 0),
    CONSTRAINT chk_fuel_comp_number CHECK (number BETWEEN 1 AND 12),
    CONSTRAINT uq_fuel_comp_number UNIQUE (tanker_id, number)
);

CREATE TABLE IF NOT EXISTS fuel_delivery_compartments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    delivery_id    UUID          NOT NULL REFERENCES fuel_deliveries(id),
    compartment_id UUID          NOT NULL REFERENCES fuel_tanker_compartments(id),
    litres         NUMERIC(10,2) NOT NULL,
    CONSTRAINT chk_fuel_dcomp_litres CHECK (litres > 0),
    CONSTRAINT uq_fuel_dcomp UNIQUE (delivery_id, compartment_id)
);

ALTER TABLE fuel_deliveries
    ADD COLUMN IF NOT EXISTS tanker_id             UUID REFERENCES fuel_tankers(id),
    ADD COLUMN IF NOT EXISTS driver_employee_id    UUID,
    ADD COLUMN IF NOT EXISTS completion_request_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_delivery_completion_req ON fuel_deliveries (tenant_id, completion_request_id) WHERE completion_request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_fuel_delivery_tanker ON fuel_deliveries (tenant_id, tanker_id, status);
CREATE INDEX IF NOT EXISTS ix_fuel_delivery_driver ON fuel_deliveries (tenant_id, driver_employee_id, status);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_DRIVER', 'See and complete the fuel loads allocated to you (driver app)')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'fuel', resource = 'driver', verb = 'EXECUTE', sensitivity = 'NORMAL',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_DRIVER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'FUEL_DRIVER'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
