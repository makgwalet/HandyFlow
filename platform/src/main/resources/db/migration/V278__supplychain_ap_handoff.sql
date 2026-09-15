-- V278__supplychain_ap_handoff.sql
-- Supply Chain -> AP hand-off, per the product owner's own explicit
-- decision: keep both ScSupplierInvoice and ApBill (they represent
-- genuinely different business stages -- procurement validation vs
-- financial liability), but build the missing bridge between them so
-- a matched, approved supplier invoice never sits as two independent,
-- unreconciled records.
--
-- Deliberately a link field on each side rather than widening
-- InvoiceStatus with a new terminal state -- ap_bill_id being non-null
-- on the Supply Chain side IS the "handed off" fact, and doubles as
-- the idempotency guard (a second approval attempt on an
-- already-handed-off invoice is a no-op, not a duplicate ApBill).

ALTER TABLE sc_supplier_invoices
    ADD COLUMN ap_bill_id UUID REFERENCES ap_bills (id);

ALTER TABLE ap_bills
    ADD COLUMN source_type      VARCHAR(30),  -- e.g. 'SUPPLY_CHAIN' -- null for a bill entered directly into AP, matching the vast majority of bills that never touch Supply Chain at all
    ADD COLUMN source_reference VARCHAR(100); -- the source ScSupplierInvoice's own invoiceNumber, for a human-readable audit trail even before anyone opens the linked record

CREATE INDEX idx_ap_bills_source ON ap_bills (source_type, source_reference) WHERE source_type IS NOT NULL;

COMMENT ON COLUMN sc_supplier_invoices.ap_bill_id IS
    'Set the moment this invoice is approved (three-way matched, cleared for payment) -- the AP bill this invoice was handed off to. AP owns the payment lifecycle from here; markPaid() on this entity becomes a no-op once this is set, per the product owner''s own "don''t duplicate the money" instruction.';
