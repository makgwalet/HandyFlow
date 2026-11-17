-- Truck stop driver tabs: a bill for one vehicle with lines taken from the truck stop's Bookings services at their Bookings price.
-- A tab is settled at the till (cash, card or other) or, when it names a Fuel customer account, charged to it as a DRAFT invoice.
-- invoice_state follows the same states as overnight stays.
CREATE TABLE fuel_truck_stop_tabs (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    vehicle_registration VARCHAR(20) NOT NULL,
    driver_name          VARCHAR(120),
    account_id           UUID,
    status               VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    payment_method       VARCHAR(10),
    opened_by            UUID,
    opened_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_by           UUID,
    settled_at           TIMESTAMPTZ,
    void_reason          VARCHAR(300),
    invoice_state        VARCHAR(12),
    invoice_id           UUID,
    invoice_number       VARCHAR(40),
    invoice_error        VARCHAR(500),
    invoice_claimed_at   TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_ts_tab_status CHECK (status IN ('OPEN','SETTLED','VOID')),
    CONSTRAINT ck_fuel_ts_tab_method CHECK (payment_method IS NULL OR payment_method IN ('CASH','CARD','OTHER','ACCOUNT')),
    CONSTRAINT ck_fuel_ts_tab_invoice_state CHECK (invoice_state IS NULL OR invoice_state IN ('PENDING','CLAIMED','INVOICED','FAILED','LINK_FAILED')),
    CONSTRAINT ck_fuel_ts_tab_invoice_acct CHECK (invoice_state IS NULL OR account_id IS NOT NULL)
);
CREATE INDEX ix_fuel_ts_tabs_tenant ON fuel_truck_stop_tabs (tenant_id, status, opened_at DESC);

CREATE TABLE fuel_truck_stop_tab_lines (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    tab_id      UUID NOT NULL REFERENCES fuel_truck_stop_tabs (id),
    offering_id UUID NOT NULL REFERENCES fuel_truck_stop_offerings (id),
    description VARCHAR(200) NOT NULL,
    quantity    NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(14,2) NOT NULL CHECK (unit_price > 0),
    added_by    UUID,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_fuel_ts_tab_lines_tab ON fuel_truck_stop_tab_lines (tab_id);
