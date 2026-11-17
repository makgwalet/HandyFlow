# Fuel: provider times on accepted shifts

Date: 2026-11-17. Written, not compiled or run (Java).

## What it does
An imported shift carries the opened and closed times its pump/POS provider reported. Accepting it used to drop them. They are now stored on the shift (V706 `provider_opened_at`, `provider_closed_at`) and shown in the shift detail as "Provider times: opened …, closed …".

## Rules
- The system's own `opened_at` / `closed_at` are **not changed**. Reports, ordering and audit keep using them. The provider times are for reference only.
- Recorded on accept, only when the provider sent at least one time. Shifts that were not imported have none.
- A provider closing time before its opening time makes the accept refuse (the import stays pending, the reviewer sees why, and may reject it).
- `ShiftResponse` gained two components; the old constructor is kept.

## Decision for you
Should reports and the "shifts by date" views use the provider's times for imported shifts instead of the system's? Not done, because it changes which day a shift counts in.
