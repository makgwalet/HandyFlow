# Fuel B06a: enquiries, quotation history and acceptance (2026-11-12)

## 1. Implemented
- **Enquiries:** record what a customer asked for (account or just a name, fuel, litres, date, how it came in). Raising a quotation from an enquiry creates an ordinary quotation (the existing sales order in QUOTE status) with the usual price resolution, and links the two. Or close it as Lost or Cancelled with a reason (5+ characters). Closed enquiries cannot be edited or re-opened. A quote needs a customer account, so an enquiry from someone with no account must be linked first.
- **Quotation history:** every create and revise of a quotation adds a version (append-only: litres, price, site, dates, who, when). The order screen shows the versions with price change. Existing quotations and orders get a version 1 from their current terms.
- **Acceptance record:** when confirming, optionally record who accepted for the customer, how (email, phone, signed quotation, customer purchase order, portal, other), a reference and the date. It is tied to the latest version and cannot be edited or recorded twice.

## 2. Preserved
Quote, revise, confirm, credit check, reservation, cancel, delivery and invoicing behave as before. `ConfirmOrderRequest` and `FuelOrderService.revise` keep their old shapes (new fields and an overload). The acceptance is optional, so nothing existing needs it.

## 3. Files
Fuel: `FuelEnquiry`, `FuelOrderRevision`, `FuelOrderAcceptance`, three repositories, `FuelOrderHistory`, `FuelEnquiryService`, `EnquiryNumberGenerator`, `FuelEnquiryController`, `EnquiryDtos`, `FuelOrderService`, `FuelOrderController`, `ConfirmOrderRequest`, `FuelDivisionRules`. Web: `EnquiriesTab`, `enquiryOps`, `OrdersTab`, `orderOps`, `FuelPage`, `moduleSections`.

## 4. Database
`V670` (additive): `fuel_enquiry_sequences`, `fuel_enquiries` (status and source checks, quoted-status must have a quotation, one enquiry per quotation), `fuel_order_revisions` (unique per order and version), `fuel_order_acceptances` (one per order). Backfills version 1 for existing orders. Checked on scratch PostgreSQL 16: applied, backfill created one version per order, constraints reject a QUOTED enquiry without a quotation and zero litres. Not run through Flyway.

## 5. API
`GET/POST /api/v1/fuel/enquiries`, `PUT /{id}`, `POST /{id}/quote|lost|cancel`; `GET /api/v1/fuel/orders/{id}/history`; confirm accepts an optional `acceptance`. Reads FUEL_READ; writes FUEL_MANAGE or FUEL_ORDERS_MANAGE. Enquiries need Wholesale or Distribution, like orders.

## 6. Security
Tenant from TenantContext on every query; an enquiry or order id from another company is "not found" (the history endpoint checks the order first). No client-supplied tenant. Rows are append-only in code (updatable = false) and the history is never deleted.

## 7. Tests
`FuelEnquiryServiceTest` (6), `FuelOrderHistoryTest` (5), `FuelOrderServiceTest` +4, `FuelDivisionRulesTest` +1; web `EnquiriesTab` (6), `enquiryOps` (4), `OrdersTab` +2. Java parse-checked only; Maven not run.

## 8. Gaps and risks
- **Contracts and volume pricing (B06b) are not built.** Contract prices today are account-specific entries in the price list (under B07 approval). A contract entity with its own tiers would be a second price master, which the brief forbids; it needs your decision on whether a contract should only group and report on those price entries.
- No enquiry to supplier (RFQ) flow; "RFQ" here means a customer asking for a price.
- A revision made before this release has no record of earlier versions; only the current terms are version 1.
- Acceptance is not mandatory; a setting to require it before confirmation is not built.
- Quotation expiry is shown but not enforced on confirmation (unchanged).
- Rejecting an acceptance recorded in error is not possible (append-only); cancel and re-quote.
- An enquiry's customer cannot be changed to another account once quoted; the quotation is the record.

## 9. Next
Decide the contract design (B06b); optional "require acceptance" setting; expiry enforcement on confirm.
