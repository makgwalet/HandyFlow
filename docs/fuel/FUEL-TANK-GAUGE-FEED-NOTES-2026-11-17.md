# Fuel: tank-gauge feed (2026-11-17)

Written, not compiled or run (Java). Web: all fuel, navigation and shell tests (572) and tsc pass.

## What it is
A gauge or tank-monitoring system sends a tank's level to Fuel. The reading is **staged**; a person accepts it, which records an ordinary dip reading through `FuelService.recordDipReading` (so variance, the negative-variance alert and the audit trail are exactly those of a typed dip), or rejects it with a reason of at least 5 characters. Nothing changes stock or dip records until a person accepts. No auto-accept (that is decision D1, still yours).

## Sender API
`POST /api/v1/fuel/import/tank-gauges`, public path, key in `X-Fuel-Import-Key` (the same per-company keys as the pump/POS import; one key per sending system works for both). One level per call: `source`, `externalId`, `tank` (the sender's label), `litres` (>= 0), `readAt`, optional `senderName`.
- Idempotent per source + externalId. Same content again returns the original (`duplicate: true`); different content under the same id is refused with 409 and never overwritten.
- A `readAt` more than 10 minutes in the future is refused (clock drift allowance); no other time limit is applied.
- Unknown, missing or revoked keys all get the same 401. Company comes from the key. Fuel licence is checked. Rate limit 300 a minute per path.
- No division is required: a depot with no forecourt has gauges too.

## Tank labels
`fuel_import_tank_aliases` maps what a source calls a tank to a Fuel tank (per source, case-insensitive). Without a label the tank is matched by its Fuel name. An unknown tank is staged with a problem and cannot be accepted until it is added or mapped.

## Staff API (`/api/v1/fuel/gauge-imports`, not under /forecourt)
`GET ?status=`, `POST /{id}/accept`, `POST /{id}/reject` (FUEL_MANAGE or FUEL_OPERATIONS_MANAGE, the same as typing a dip); `GET|POST|DELETE /aliases` (list: reviewers; change: FUEL_MANAGE); `GET|POST /keys`, `POST /keys/{id}/revoke` (FUEL_MANAGE). Keys can be made here, so a company without a forecourt can use the feed.

## Web
Stock > Gauge readings: waiting / accepted / rejected, accept and reject, Tank labels, Keys for gauge systems.

## DB
V703: `fuel_gauge_readings`, `fuel_import_tank_aliases`. New tables only.

## Tests
`FuelGaugeImportServiceTest` (9), `FuelEndpointSecurityTest` (+1; the new controller is listed as public and pinned to one key-authenticated POST), web `GaugeImportsTab.test` (7).

## Gaps
- One level per call (no batch).
- A gauge that sends often will fill the review queue; auto-accept within a tolerance needs your decision (D1).
- Accepting uses the gauge's reading time, not the accept time. The dip's "calculated" level is the tank's ledger level when accepted, so accepting a reading long after it was taken can show a variance that is just the movement since.
- Pump/POS and gauge keys are shared; a revoked key stops both.
