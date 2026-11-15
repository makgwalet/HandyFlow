-- Freight that arrives after a stock receipt (the carrier's invoice comes later). Each addition is kept as its own row; the receipt's freight total
-- is the sum, and the tank's weighted-average cost moves only by the share that belongs to fuel still in the tank. The share that belongs to fuel
-- already sold is recorded here as unabsorbed and is never pushed back into deliveries already costed.
CREATE TABLE fuel_receipt_freight_adjustments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    receipt_id          UUID NOT NULL,
    tank_id             UUID NOT NULL,
    amount              NUMERIC(14,2) NOT NULL,
    note                VARCHAR(300) NOT NULL,
    litres_in_tank      NUMERIC(12,2) NOT NULL,
    absorbed_amount     NUMERIC(14,2) NOT NULL,
    unabsorbed_amount   NUMERIC(14,2) NOT NULL,
    cost_before         NUMERIC(10,4),
    cost_after          NUMERIC(10,4),
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fuel_rfa_amount CHECK (amount > 0),
    CONSTRAINT ck_fuel_rfa_split CHECK (absorbed_amount >= 0 AND unabsorbed_amount >= 0 AND absorbed_amount + unabsorbed_amount = amount)
);
CREATE INDEX ix_fuel_rfa_receipt ON fuel_receipt_freight_adjustments (tenant_id, receipt_id, created_at);
