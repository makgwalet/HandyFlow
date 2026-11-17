# Fuel: live pump transactions with refunds and voids — notes, 2026-11-17

Written, not compiled or run (Java). Parse-checked only; Maven is not reachable from my sandbox, so please run the Java tests.

## Reading of the request
The pump/POS import took only whole finished shifts. A sender can now also send **live transactions**: sales, and the **refunds and voids** that reverse them. They are kept with the shift they belong to and **take effect when a supervisor accepts that shift**. Nothing posts stock or money on receipt.

## How it works
- `POST /api/v1/fuel/import/pos-transactions`, header `X-Fuel-Import-Key` (same key, licence and Retail-division rules as `pos-shifts`; unknown, missing or revoked key = 401). Rate limited at 600 a minute.
- Body: `source`, `shiftExternalId` (the externalId the finished shift is, or will be, sent under, same source) and up to 200 `transactions`.
  - `SALE`: `externalId`, `pumpLabel`, `litres`, `amount`, optional `tender` (CASH, CARD, ACCOUNT, FLEET, OTHER), optional `occurredAt`.
  - `REFUND` / `VOID`: `externalId`, `originalExternalId` (the sale), `amount`, `reason` (5+ characters). A void must equal the whole sale; refunds and voids on one sale together may not exceed it.
- **Idempotent per transaction.** Same externalId and same content = `DUPLICATE`; different content = `CONFLICT`, not stored (send a correction under a new externalId). Answer is HTTP 200 with one outcome per transaction, in the sender's order.
- **Order does not matter.** Transactions may arrive before or after the shift, and a refund may arrive before its sale. Problems are listed on receipt and checked again on accept.
- **On accept** of the shift (after it is opened, before it is closed), each refund and void becomes a shift adjustment and is approved in the same step by the supervisor accepting. The close then compares takings with expected less approved adjustments, exactly as for a typed-in refund. If any refund does not add up, the accept is refused and rolls back; the shift stays pending.
- **Automatic accept never carries refunds or voids.** With no person to approve, such a shift is left pending for a supervisor (and the usual "not accepted" notice goes out). Shifts with sales only still auto-accept.
- **Rejecting** the shift marks its waiting transactions `NOT_APPLIED`. Transactions that arrive for a shift already accepted or rejected are kept as `NOT_APPLIED` with the reason: use the shift reversal or adjustment flow for those.
- **Review:** `GET /api/v1/fuel/forecourt/imports/{id}/transactions` (reviewers) lists them with problems and a summary: sales, litres, sales, refunds, voids, net, net per tender and the litres difference against the staged shift's meters. It is a comparison only; the shift's own takings stay the authority.

## Changes
- `V717__fuel_pos_transactions.sql` (new table only; no applied migration edited). Unique per company, source and externalId.
- `FuelPosTransaction`, `FuelPosTransactionRepository`, `FuelPosTransactionService`, `FuelTransactionIngestController`; `FuelPosImportService.accept` and `reject` call the service; `FuelImportReviewController` gets the review endpoint; `SecurityConfig` and `RateLimitFilter` carry the new path.
- Tests: `FuelPosTransactionServiceTest` (18), three added to `FuelPosImportServiceTest`, one added to `FuelEndpointSecurityTest` (single key-authenticated POST, path permitted and rate limited).

## Not changed
Existing senders and the shift endpoint behave as before. Shifts with no live transactions accept exactly as before.

## Not built, and why
- **Web screen.** The review endpoint is there; the Imported shifts detail does not show the transactions yet.
- **Sales do not become stock or money.** Meters stay the authority for litres; takings stay the authority for money.
- **Pump labels on sales are not matched to Fuel pumps.** They are kept as the sender's text and shown beside the shift.
- **Refund after the shift is closed** uses the existing reversal flow, by design.
- Tank-gauge feeds and vendor adapters: unchanged.

## Test it (needs the import key from Fuel > Imported shifts > Keys)
1. Send `pos-shifts` for shift `S-1` (as in the internal test script §7), then `pos-transactions` with `T1` SALE R100 5 L, `R1` REFUND R40 of `T1`. Both STAGED, no problems.
2. Send `R1` again: DUPLICATE. Send `R1` with R50: CONFLICT.
3. Send a REFUND of R70 on `T1`: problem "more than the sale".
4. Imported shifts > shift > `GET .../transactions`: summary shows net R60.
5. Accept as a supervisor with the refund R40 only: the shift closes with an approved REFUND adjustment of R40 and expected takings reduced by R40.
6. Send a refund against an already accepted shift: kept as NOT_APPLIED with the reason.
7. With auto-accept on, send a shift with a refund: it stays pending.
