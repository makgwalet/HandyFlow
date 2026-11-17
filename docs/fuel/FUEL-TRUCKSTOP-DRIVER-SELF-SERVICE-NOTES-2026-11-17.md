# Fuel truck stop: driver self-service (overnight stay requests) — notes, 2026-11-17

Written, not compiled or run (Java). Parse-checked only; Maven is not reachable from my sandbox, so please run the Java tests. V718 is new. No web screen yet.

## Reading of the request
"Driver self-service" is built on the **customer portal you already have**. A driver or fleet contact with an ACTIVE portal grant on a Fuel customer account can see free bays, see the stays charged to that account, and **ask** for an overnight stay. It is a request, not a booking: staff approve or decline. No new login type and no new identity.

## Why a request and not an instant booking
A stay is charged to the customer's account, uses a limited bay, and the account may be on credit hold. The portal's fuel orders already work as requests that staff confirm; this follows the same rule. Approval books through the **ordinary stay booking**, so the bay lock, the capacity rule, the credit-hold rule and the charge to the account are exactly as when staff book. If that refuses, the request stays pending and the refusal is shown to the staff member, who can decline with a reason.

## Customer side (portal token; `/api/v1/fuel/portal/accounts/{accountId}/truck-stop`)
- `GET availability?from&days` — free bays per night (counts only, never trucks) and the nightly rate before VAT. Up to 14 days. `open=false` when no rate is set.
- `GET stays` — stays charged to this account (last 50): dates, status, nights, amount, invoice number.
- `GET stay-requests`, `POST stay-requests`, `POST stay-requests/{id}/cancel`.
- Request rules: not in the past; departure after arrival; at most 30 nights (longer: call us); registration tidied to upper case; no identical pending request (same truck and dates); at most 10 open requests per account; a night with no free bay is refused early, naming the night (approval checks again and is the authority). A customer on credit hold can still ask; staff decide.
- Access: every call goes through the grant first. No grant, another customer's account, a lapsed Fuel plan, or no Truck Stop division all answer "not found", so ids and the division cannot be probed. Only the person who made a request can cancel it, and only while pending.
- Staff are told by the existing "customer request" notice, linking to Truck stop.

## Staff side (`/api/v1/fuel/truck-stop/stay-requests`, Truck Stop division required)
- `GET ?status=&limit=` (FUEL_READ, FUEL_MANAGE, FUEL_TRUCKSTOP_MANAGE): who asked, for which customer, truck, dates.
- `POST {id}/approve` (FUEL_MANAGE or FUEL_TRUCKSTOP_MANAGE): books and charges the account. The request row is locked, so two staff cannot approve it twice.
- `POST {id}/decline` with a reason of 5+ characters, which the customer sees.

## Changes
V718 (new table only); `FuelTruckStopStayRequest`, repository, `FuelStayRequestService`, `StayRequestDtos`, `FuelPortalTruckStopController`, `FuelStayRequestController`; `FuelTruckStopStayRepository.findForAccount`; `FuelPortalDataService.requireGrant` is now package-private so the new service reuses the same grant check. Security test: the portal controller is listed as a portal controller, and in the no-module-guard list with the other portal controller.
Tests: `FuelStayRequestServiceTest` (17).

## Not built, and why
- **Web screens**, both sides (a "Stay requests" list for staff; a Truck stop page in the customer portal).
- **Tabs and statements in the portal.** Stays are shown; driver tabs and the account statement are not exposed to customers yet.
- **Reserving timed services (showers, parking slots)** through the portal. Those are Bookings reservations; only overnight stays are requestable.
- **Telling the customer by email** when staff decide. They see the status in the portal; no message is sent.
- Edit of a pending request: cancel and ask again.

## Decisions for you
1. Should an approved request for a customer **not** on credit hold be auto-approved when a bay is free? Today staff always decide.
2. Should portal users who are only drivers (not account contacts) be a separate, narrower grant? Today anyone with an account grant sees that account's stays and can ask for stays.
3. Is the 30-night and 10-open-requests limit right?
