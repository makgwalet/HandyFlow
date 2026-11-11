# Fuel: existing database audit

Fuel migrations: V15, V17, V18, V51, V186, V271, V282, V648–V657 (plus Fleet/Earthmoving migrations that mention fuel). Latest is **V657**.

32 `fuel_*` tables: compliance_items, compliance_reminders, compliance_renewals, customer_accounts, customer_sites, deliveries, delivery_compartments, depots, dip_readings, dispatches, order_sequences, po_sequences, portal_access_grants, prices, pumps, purchase_orders, receipt_sequences, receipts, reservations, sales_orders, settings, shift_readings, shift_sequences, shifts, stock_movements, supplier_invoice_receipts, supplier_invoices, suppliers, tanker_compartments, tankers, tanks, transfers.

Observations:
- Tenant column on all business tables sampled; unique indexes are tenant-scoped (PO number, settings per tenant, ledger source uniqueness).
- Money and litres use NUMERIC with explicit scale (no floating point) in the entities read.
- Optimistic locking (`@Version`) on entities read; pessimistic lock on tanks and POs for stock paths.
- `fuel_depots.legal_entity_id` exists, unused by any DTO or service.
- No tables exist for: operating profile, divisions, legal entities, licences, enquiries, contracts, nozzles, truck stop, routes, delivery variance, outbox, idempotency keys.
- No audit columns beyond created/updated and a few `*_by` columns; the stock ledger carries a reason and source reference.
- Not audited here: row counts, index coverage under load, foreign keys to Fleet.
