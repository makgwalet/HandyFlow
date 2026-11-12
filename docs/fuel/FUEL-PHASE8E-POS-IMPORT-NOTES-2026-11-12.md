# Fuel Phase 8e: inbound pump/POS shift import (D03, patch 0810)

## Scope decided with the user
Inbound import API only: an idempotent endpoint, a per-company API key and a review queue. No device, vendor, vendor-SDK or outbound code.

## 1. Implemented
- `POST /api/v1/fuel/import/pos-shifts` (public path, key in `X-Fuel-Import-Key`). One finished shift per call: source, externalId, attendant, optional provider times, tenders (cash, card, account, other, fleet card), and per-pump opening and closing meters and test litres. Pumps are named as in Fuel (case-insensitive).
- Receipt only stages the shift (PENDING). It changes no shift, stock or takings.
- Idempotent: source + externalId is unique per company. Same content again returns the original (`duplicate: true`). Different content for the same reference is refused with 409 and never overwritten; corrections go under a new externalId. The content check ignores reading order and decimal padding.
- Review queue (Forecourt > Imported shifts): list by status, details, accept, reject (reason of at least 5 characters). Accept runs the existing `openShift` then `closeShift`, so meter, tender, variance, cash-control and stock-ledger rules are exactly those of typing the shift in. Any refusal rolls everything back and leaves the import pending, with the message shown to the reviewer.
- Keys: create (plaintext shown once, only the SHA-256 hash stored), list, revoke.

## 2. Preserved
No change to shifts, stock or cash-control code. The imported shift has no opener, so the "opener cannot close" rule is met by the supervisor who accepts. No prices, tax or regulatory values. No migration edits.

## 3. Files
`FuelImportKey`, `FuelPosImport`, two repositories, `PosImportDtos`, `FuelPosImportService`, `FuelImportIngestController` (public), `FuelImportReviewController`; `SecurityConfig` (exact path permitted), `RateLimitFilter` (120/minute). Web: `ImportsTab`, `importOps`, `FuelPage`, `moduleSections`. Test change: `FuelEndpointSecurityTest` now lists the ingest controller as public and pins it to one key-authenticated POST.

## 4. DB
V686: `fuel_import_keys` and `fuel_pos_imports` (unique per tenant, source and externalId). New tables only.

## 5. API
Public: `POST /api/v1/fuel/import/pos-shifts`. Staff, under `/api/v1/fuel/forecourt/imports`: `GET`, `GET /{id}`, `POST /{id}/accept`, `POST /{id}/reject`, `GET|POST /keys`, `POST /keys/{id}/revoke`. All staff routes call `requireModule("fuel")`; the Retail division rule applies. Accept and reject need `FUEL_SHIFT_SUPERVISE` or `FUEL_FORECOURT_MANAGE`; keys need `FUEL_MANAGE` or `FUEL_FORECOURT_MANAGE`.

## 6. Security
Unknown, missing or revoked keys all get the same 401. The company comes from the key, never from the request. The ingest checks the Fuel licence and the Retail division for the key's company before staging. Staff routes are tenant-scoped; no tenant id is accepted from a client. Keys are rate limited by path.

## 7. Tests
`FuelPosImportServiceTest` (16), `FuelEndpointSecurityTest` (+1, and the allowlist), web `ImportsTab.test` (10). Full Fuel web run: 72 files, 489 tests pass; tsc clean. Java is parse-checked only; I could not run Maven here, so please run the Java tests.

## 8. Gaps
- The shift's openedAt/closedAt are server time when accepted. The provider's times are kept in the shift notes and the staged record.
- One pump name must match one pump; there is no alias table for provider pump ids.
- Only whole finished shifts are accepted: no live transactions, no tank-gauge feeds, no refunds or voids from the sender (use the existing adjustment flow).
- No automatic accept. Auto-accept would need a decision on trust and variance limits.
- A rejected shift cannot be re-sent unchanged; the sender uses a new externalId.
- No outbound calls, webhooks or device/vendor adapters (out of scope by decision).

## 9. Risks
A public, key-authenticated endpoint: keep keys secret, revoke on suspicion, and consider gateway rate limits as well. The staged payload is stored as text; it holds attendant names and takings only.

## 10. Next
Apply 0798-0810 in order, run Maven, and decide whether auto-accept within a variance limit is wanted.
