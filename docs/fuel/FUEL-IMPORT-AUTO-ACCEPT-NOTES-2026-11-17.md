# Fuel: automatic acceptance of clean imports (D1)

Date: 2026-11-17. Written, not compiled or run (Java).

## What it does
A company can switch on automatic acceptance for (a) pump/POS shifts and (b) tank-gauge readings. **Both are off by default**; with no setting, every import waits for a person exactly as before.

## Rules
- Runs only after the import was staged and committed (in the ingest controllers, not inside the ingest transaction), so a refusal can never lose the received data.
- Only an import with **no problems** (known pumps/tank), that is **not a repeat**, and is still pending.
- Accepts through the same `accept` code a person uses. Every normal rule applies: meters, tenders, the shift variance tolerance (needs a reason beyond it, so such shifts stay pending), stock effects. Any refusal leaves it pending, logged.
- Gauge readings are accepted only within a **limit the company enters** (largest absolute difference from the tank's book level, litres). Switching gauges on without a limit is refused. No limit means no automatic gauge acceptance.
- Automatic acceptances have no deciding user and the note "Accepted automatically".
- V705 `fuel_import_auto_accept` (one row per company). `GET/PUT /api/v1/fuel/import-auto-accept` (FUEL_CONFIGURATION_MANAGE). Web: "Automatic acceptance" card at the foot of Imported shifts.

## Decisions for you (D1)
1. Is the company's existing shift variance tolerance enough as the shift check, or do you want an extra per-company limit (for example maximum litres per shift)?
2. Should an automatic acceptance notify anyone? Today it does not.
