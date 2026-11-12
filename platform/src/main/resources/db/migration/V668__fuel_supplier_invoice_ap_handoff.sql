-- B08: an approved Fuel supplier invoice is handed to Accounts Payable as a DRAFT bill.
-- ap_bill_id is the "handed off" fact and the idempotency guard on the Fuel side (AP also dedupes by source).
-- No foreign key to ap_bills on purpose: the modules stay separable, and AP owns that table.
ALTER TABLE fuel_supplier_invoices
    ADD COLUMN ap_bill_id        UUID,
    ADD COLUMN ap_handed_off_at  TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_fuel_sinv_ap_bill ON fuel_supplier_invoices (ap_bill_id) WHERE ap_bill_id IS NOT NULL;
