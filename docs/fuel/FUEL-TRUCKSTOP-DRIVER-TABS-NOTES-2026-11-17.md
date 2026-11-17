# Fuel truck stop: driver tabs (POS items)

Date: 2026-11-17. Written, not compiled or run (Java). Web tests (601 in fuel and navigation) and `tsc` pass. V707 is new.

## What it does
A driver's bill at the truck stop: one tab per vehicle, with lines taken from the truck stop's existing services (showers, meals, washes... the offerings already set up from Bookings). Settled at the till or charged to a Fuel customer account.

## Prices
Never typed. A line reads the service's **current Bookings price** when added and keeps that price and name. A service with no price, an inactive one, or a switched-off offering cannot be added. No new price list exists.

## Rules
- `POST /tabs` opens (registration required; optional account, which must be active and not on credit hold). `PUT /tabs/{id}/account` changes it while open.
- Lines: `POST /tabs/{id}/lines` (offering + quantity 0 to 999, two decimals), `DELETE /tabs/{id}/lines/{lineId}` while open.
- `POST /tabs/{id}/settle` (cash, card or other) only for a tab **without** an account and at least one line.
- `POST /tabs/{id}/charge` only for a tab **with** an account and at least one line: settles it as ACCOUNT, queues a draft invoice and tries to raise it. A failed invoice never undoes the charge; the response says so, and `POST /tabs/{id}/invoice` retries.
- `POST /tabs/{id}/void` (reason 5+ characters) only while open. A settled tab is final.
- Invoicing uses the stay safeguards: PENDING, CLAIMED, INVOICED, FAILED, LINK_FAILED; one caller wins the claim; an invoice that exists but could not be linked is never made again. One invoice line per tab line, VAT from the company's own settings (zero when not registered), due date from the account's terms. Nothing is issued.
- A truck stop division cannot be switched off while a tab is open.
- Under `/truck-stop`, so the Truck Stop division is required on the server. Read: FUEL_READ, FUEL_MANAGE, FUEL_TRUCKSTOP_MANAGE. Write: FUEL_MANAGE, FUEL_TRUCKSTOP_MANAGE.
- Web: "Driver tabs" under Truck Stop.

## Not done, on purpose
- **Fuel on a tab.** Forecourt shifts already count fuel by meter readings; a second place to sell fuel would double count stock and sales.
- Items with no Bookings service (a meal not set up as a service). They would need a price list, which does not exist.
- Linking a tab to an overnight stay, so the nights and the services appear on one invoice.
- Tab-level payments split across methods, refunds on settled tabs.

## Decisions for you
1. Are Bookings service prices VAT-exclusive? Invoicing adds VAT on top (same assumption as stay rates). If your prices include VAT, this over-charges.
2. Should a tab be attachable to an overnight stay so one invoice covers both?
3. Should till-paid tabs reach the day's takings anywhere (there is no truck stop till or shift today)?
