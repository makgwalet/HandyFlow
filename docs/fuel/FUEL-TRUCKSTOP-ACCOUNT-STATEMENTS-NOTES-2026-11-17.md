# Fuel truck stop: account statements

Date: 2026-11-17. Written, not compiled or run (Java). Web tests (611 in fuel and navigation) and `tsc` pass. No migration.

## What it does
`GET /api/v1/fuel/truck-stop/account-statements/{accountId}?from=&to=`: what the truck stop charged to one customer account in a period.
- **Overnight stays** that checked out in the period, dated by the day the truck left, amount = nights x the rate the stay was booked at.
- **Driver tabs** charged to the account in the period, dated by the day they were charged (South African time), amount = the sum of the lines at the prices they kept.
- Each line carries how far it got in Invoicing (draft invoice number, not invoiced yet, failed). Totals: total, invoiced, not invoiced yet.
- Period: first and last day inclusive, at most 366 days. Read only. Under `/truck-stop` (division enforced on the server); FUEL_READ, FUEL_MANAGE or FUEL_TRUCKSTOP_MANAGE.
- Web: "Account statements" under Truck Stop, with a CSV download (text cells that start with `=`, `+`, `-` or `@` are written as text).

## What it is not
- Not a ledger. Amounts are **before VAT** (VAT is added on the invoice), and payments live in Invoicing, so there is no balance or ageing here.
- Only truck stop charges. Fuel deliveries to the same account are not on it.
- No PDF or emailed statement.

## Decisions for you
1. Should the statement also carry fuel deliveries and the invoices' VAT-inclusive totals, so it matches what the customer sees in Invoicing? That needs a read of Invoicing's invoices, not just Fuel's own records.
2. PDF and a "send to the customer" button?
