# Truck stop prices and VAT (2026-11-17)

Decision (closed): Bookings prices are VAT-exclusive. Tab and stay invoicing must not add VAT twice. Screens show ex-VAT, VAT and total. VAT comes from the shared Invoicing engine.

## What was checked
- `FuelTabInvoicingService` and `FuelStayInvoicingService` pass the ex-VAT price (`unitPrice`, `ratePerNight`) with the company's rate from `invoicing.vatTreatment(tenant, today)` (zero when not registered). Invoicing adds the VAT once.
- New test `noDoubleVat`: invoice lines multiply out to the ex-VAT tab total (R170), never a VAT-inclusive figure.

## What was added
- `GET /api/v1/fuel/truck-stop/tabs/vat` (FUEL_READ and similar): `{registered, ratePercent}` today. Same rule as the invoice.
- Tabs: total reads "ex VAT" with a line "R170 ex VAT + R25.50 VAT (15%) = R195.50", or "no VAT charged" when not registered.
- Stays: amount marked "ex VAT" with the same breakdown.
- Web helpers `vatBreakdown` and `vatText` (rounded to cents).

## Limits
- Bookings has no VAT field. VAT-exclusive is the decision, not something Bookings enforces. Prices entered in Bookings must be ex-VAT.
- The screen breakdown uses today's rate; the invoice uses the rate on its creation date. Across a rate change they can differ. The invoice is the record.
- The breakdown on the screen is an estimate of rounding: Invoicing rounds per line, the screen rounds the total.

Written, not compiled or run (Java).
