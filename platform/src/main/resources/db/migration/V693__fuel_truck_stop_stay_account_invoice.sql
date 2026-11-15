-- A stay can be charged to an existing Fuel customer account. At check-out a DRAFT invoice is created in Invoicing (never issued here).
-- invoice_state: PENDING (to invoice), CLAIMED (being invoiced), INVOICED, FAILED (retry), LINK_FAILED (invoice exists, link not saved).
-- Stays with no account are paid at the gate and carry no invoice state.
ALTER TABLE fuel_truck_stop_stays
    ADD COLUMN account_id          UUID,
    ADD COLUMN invoice_state       VARCHAR(12),
    ADD COLUMN invoice_id          UUID,
    ADD COLUMN invoice_number      VARCHAR(40),
    ADD COLUMN invoice_error       VARCHAR(500),
    ADD COLUMN invoice_claimed_at  TIMESTAMPTZ;
ALTER TABLE fuel_truck_stop_stays
    ADD CONSTRAINT ck_fuel_ts_stay_invoice_state CHECK (invoice_state IS NULL OR invoice_state IN ('PENDING','CLAIMED','INVOICED','FAILED','LINK_FAILED')),
    ADD CONSTRAINT ck_fuel_ts_stay_invoice_acct CHECK (invoice_state IS NULL OR account_id IS NOT NULL);
CREATE INDEX ix_fuel_ts_stays_invoice ON fuel_truck_stop_stays (tenant_id, invoice_state) WHERE invoice_state IS NOT NULL;
