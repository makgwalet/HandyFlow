# Fuel truck stop: safety fixes, yard capacity view, charging stays to an account (2026-11-15)

Status: written, not compiled or run (Java). Web tests and `tsc` pass. Flyway V693 is new.

## Safety fixes (patch 0842)
- A division cannot be deactivated while overnight stays are booked or in the yard, or timed truck stop reservations are open (`FuelOperatingProfileService.blockers`).
- Bookings and departure changes take a pessimistic row lock on the company's parking settings, so two people cannot book the last bay at once.

## Yard capacity view (patch 0842)
`GET /api/v1/fuel/truck-stop/stays/capacity?from=&days=` (1 to 31 nights, default 14): used, arrivals, departures and free bays per night, plus trucks past their planned departure. Counts nights with the same rule as the double-booking check.

## Charging a stay to a customer account
- A stay may carry `account_id` (an existing Fuel customer account, which is a CRM customer). No account means the driver pays at the gate and no invoice is made.
- Booking, changing the account and check-in refuse an account that is inactive or on credit hold. Check-out never waits on credit: the nights were used.
- At check-out the stay is queued (`invoice_state = PENDING`) and `FuelStayInvoicingService` creates a DRAFT invoice through `InvoicingFacade`: one line, nights x the rate the stay was booked at, VAT from the company's own settings (zero when not registered), due date from the account's payment terms. Nothing is issued here.
- Same claim / link / fail pattern as delivery invoicing, each step in its own transaction: PENDING -> CLAIMED -> INVOICED; FAILED can be retried; LINK_FAILED (invoice exists but could not be saved on the stay) is never retried automatically, so it cannot be billed twice. A claim older than 15 minutes counts as a crashed attempt.
- A failed invoice never undoes the check-out. The response says the invoice was not raised and the stay shows "Raise invoice" to try again (`POST /stays/{id}/invoice`).
- `PUT /stays/{id}/account` sets or clears the account while the stay has not ended.

## Known limits
- If saving the link and recording LINK_FAILED both fail, the claim goes stale after 15 minutes and the stay could be invoiced again. This is logged at error level with the invoice number.
- The invoice state columns are changed by guarded bulk updates, so the entity maps them read only.
- Drivers are not linked to Fleet trucks (not requested this round).
