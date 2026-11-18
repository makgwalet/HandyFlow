# Fuel Truck stop: offline operations design

Status: implemented in Batch F (server V728, `FuelOfflineService`, Fuel app outbox). **Not tested against a running server or a device.**

## What was audited first

`FuelTruckStopStayService` book / checkIn / checkOut, `FuelStayInvoicingService` (draft invoice for an account stay after check-out; a failed invoice never undoes a check-out), stay requests from customers (`FuelStayRequestService`, staff decide them online). Bay occupancy and capacity are computed on the server from stays and bays.

## What can be captured offline

| Capture | Queue kind | Server call |
|---|---|---|
| Book a stay at the desk (a stay request taken by staff) | `STAY_BOOK` | `POST /truck-stop/stays` |
| Authorised check-in | `STAY_CHECK_IN` | `POST /truck-stop/stays/{id}/check-in` |
| Authorised check-out (with the date the truck left) | `STAY_CHECK_OUT` | `POST /truck-stop/stays/{id}/check-out` |

Not offline: cancel, no-show, bay assignment, account change, tabs and tab settlement, and deciding customer stay requests. They need the server's current state or money.

## Availability is never claimed offline

The Book screen says the phone cannot see which bays are free and that a booking is confirmed only when the server has checked bays and rate; a full yard refuses it at sync and the refusal is kept as a conflict. The app shows no "free bays" figure from a saved copy as a promise.

## Idempotency

V728 adds `book_request_id`, `check_in_request_id`, `check_out_request_id` (+ captured times) on `fuel_truck_stop_stays` with partial unique indexes per tenant. The outbox item id is the client request id.

- Duplicate check-in id: the stay is returned as it is; the truck is not checked in twice.
- Duplicate check-out id: returned as it is; **no second check-out and no second invoice** (the invoice is only raised by the controller after a first real check-out).
- Duplicate booking id: the first stay is returned; no second stay, no second bay.
- An id already used for a different stay is refused.
- Check-in after another device already did it: the existing state rule refuses it; kept as a conflict.

## Server validation at sync

| Check | How |
|---|---|
| Authorised | `FUEL_MANAGE` / `FUEL_TRUCKSTOP_MANAGE` checked on the request that arrives; revoked permission means the queued write is refused (403) and parked. |
| Tenant isolation | Stay lookups and request-id lookups are tenant-scoped. |
| Reservation / stay state | Existing: only BOOKED can check in, only CHECKED_IN can check out; check-in not before the arrival date; account must still be chargeable. |
| Vehicle identity | The stay holds the registration (and Fleet link) from booking; the phone sends only the stay id, so it cannot change the vehicle. There is no plate or driver re-verification at the gate in this batch. |
| Pricing | Server only: nights and amount come from the stay's rate and the departure date. The phone sends the date the truck left (fixed when captured, so a late sync adds no nights); a date in the future is refused. The phone never sends a price. |
| Capture age | Same rule as the forecourt: not more than 5 minutes ahead, not older than 72 hours. |
| Duplicate transactions | The id rules above. |

## Payment

Unchanged and out of scope for offline: a stay on an account gets a draft invoice from the existing invoicing flow after the server has accepted the check-out; a pay-at-the-gate stay is paid at the till and recorded by the existing tab/till flow. The offline path never takes, records or settles payment.

## Refusals

Same conflict log as the forecourt (`fuel_offline_conflicts`, kinds `STAY_BOOK`, `STAY_CHECK_IN`, `STAY_CHECK_OUT`), audit events `FUEL_OFFLINE_CONFLICT` and `FUEL_STAY_*_OFFLINE_SYNC`.

## Known gaps

1. Offline booking is for dates the phone's stale list may disagree with; the server decides. A desk that books offline can disappoint a driver at sync. The screen warns, it does not prevent.
2. The phone's list of stays is the last saved copy; a check-in queued from stale data is judged against the server's current state at sync.
3. Customer-submitted stay requests are not offline (staff decide them online).
4. No app screen for the server conflict list (API only).
