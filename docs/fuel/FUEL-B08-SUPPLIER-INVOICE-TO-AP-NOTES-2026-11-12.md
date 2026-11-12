# Fuel B08: approved supplier invoices go to Accounts Payable (2026-11-12)

## 1. Implemented
- Approving a Fuel supplier invoice, when the tenant has the AP module, creates a DRAFT bill in Accounts Payable in the same transaction. AP's own approval, second approval and payment controls take over; Fuel does not post or pay anything.
- If AP refuses, the approval rolls back, so there is never an approved invoice silently missing its bill.
- Tenants without AP approve exactly as before. Invoices approved earlier (or while AP was off) get a "Send to Accounts Payable" button, which is safe to repeat.
- The invoice list shows "In Accounts Payable" once handed over.

## 2. Preserved
Capture, three-way matching, reject, existing approval rules, and the Supply Chain hand-off (`createBillFromSupplyChainInvoice` unchanged).

## 3. Files
AP: `ApFacade.createBillFromFuelInvoice`, `ApBill.createFromSource`. Fuel: `FuelSupplierInvoice`, `FuelSupplierInvoiceService`, `FuelSupplierInvoiceController`, `SupplierInvoiceDtos`, `fuel/package-info`. Web: `SupplierInvoicesTab`, `supplierInvoiceOps`.

## 4. Database
`V668`: `fuel_supplier_invoices.ap_bill_id`, `ap_handed_off_at`, and a unique index on `ap_bill_id`. No foreign key to `ap_bills` (modules stay separable). Checked on scratch PostgreSQL 16: applies; a second invoice cannot point at the same bill.

## 5. API
`POST /api/v1/fuel/supplier-invoices/{id}/send-to-ap` (FUEL_MANAGE or FUEL_SUPPLIER_INVOICE_MANAGE). Invoice responses add `apBillId`, `apHandedOffAt`.

## 6. Module dependency (needs your attention)
`fuel/package-info` now allows `ap` and `ap::*` (your decision of 2026-11-11). Only Agriculture and Fleet allow `fuel`, and neither is in AP's dependency chain (shared, identity, notifications, accounting, approvals), so there is no cycle. `ArchitectureVerificationTest` was not run here; please run it. Fuel only calls `ApFacade` and receives a UUID, never an AP entity.

## 7. How the bill is built
Idempotent by source ("FUEL", invoice id), so a retry returns the same bill. Bill number `FUEL-<supplier invoice number>` (max 50) so it cannot collide with a hand-typed bill; AP's `UNIQUE(tenant, bill_number)` still applies. Amount is the ex-VAT value; VAT is passed separately. Category OTHER, like the Supply Chain hand-off. The bill has no supplier link because `ap_bills.supplier_id` points at CRM customers, not Fuel suppliers; the supplier name is stored.

## 8. Security
`@PreAuthorize`, `requireModule("fuel")`; sending to AP also needs the AP module on (`requireModule("ap")`). Tenant from TenantContext.

## 9. Tests
`FuelSupplierInvoiceServiceTest` +5 (hand-off on approve, AP off, AP failure, send once, only approved). Web +3. Java is parse-checked only here.

## 10. Gaps and risks
- A missing due date falls back to the invoice date (AP requires one); due dates should be captured.
- Supplier banking details are not passed (separate item).
- Rejecting an approved invoice is not possible, so no reverse path to AP is needed today.
- Two bills for the same real invoice remain possible if someone also keys it into AP by hand; the bill number prefix makes it visible, not impossible.

## 11. Next
Supplier banking details and requisition (separate item); show the AP bill's payment status on the Fuel invoice.
