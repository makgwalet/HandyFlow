# Fuel: account statements across the Fuel account (decision 5) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests (641) and tsc pass. No migration.

## Decision 5 (closed)
Statements cover the customer's wider Fuel account, not only truck-stop charges, within the Fuel boundary. Other modules only contribute through a designed shared accounts-receivable integration.

## What the statement now contains
- **Fuel deliveries** (Distribution), dated by the day delivered in South African time, for the account's customer. Amount = litres delivered x price per litre, before VAT.
- **Overnight stays** and **driver tabs** (Truck Stop), as before. Bookings charges are reached through stays and tabs, which read their prices from Bookings.
- **Where the invoices stand.** For every invoice Fuel raised for those lines: status, total including VAT, paid, credited (credit notes) and still owed, read from Invoicing. If Invoicing cannot be read the lines still show and the statement says the settlement is not known (it never shows a false zero).
- **Filter:** by dates and by division (All, Fuel deliveries, Truck stop). Account is the one chosen.
- CSV download includes the invoice standing.

## Boundary and honesty about "ledger"
Fuel has no payment or credit-note ledger of its own; payments, credit notes and refunds live in Invoicing. So this statement shows, per Fuel-raised invoice, the paid and credited totals, not each individual payment or allocation, and it shows only invoices Fuel raised. A true account ledger across modules (individual payments, allocations, refunds, opening balance) needs the shared accounts-receivable integration the decision describes; that is not built and is not faked here.

## Changes
- `InvoicingFacade.findInvoiceSummaries(tenant, ids)` (read only, at most 500, unknown ids absent) and its implementation. This is the one change outside Fuel; it adds a method and changes nothing existing.
- `FuelAccountStatementService` adds deliveries, invoice standing and the division filter.
- `GET /api/v1/fuel/account-statements/{accountId}?from=&to=&division=` (new, not under /truck-stop, so a company without the Truck Stop division can use it). The old `/truck-stop/account-statements/...` path still works and still needs the Truck Stop division.
- Web: "Account statements" moved from the Truck Stop group to the customers group and shown for Wholesale, Distribution and Truck Stop companies; a "Show" filter; delivery lines; an invoice standing table.

## Not done, and why
- **Legal entity filter:** Fuel has no legal-entity concept on accounts or deliveries, so there is nothing to filter by.
- **Retail pump sales on account** (fleet cards, account tender at the pump) are shift totals, not per-customer, so they are not on a customer statement.
- Returned fuel is not shown as its own credit line; a credit note appears as the credited amount on its invoice.
