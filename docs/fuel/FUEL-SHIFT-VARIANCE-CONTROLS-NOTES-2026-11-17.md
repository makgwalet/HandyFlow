# Fuel: shift variance controls (D1) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests (637) and tsc pass.

## Decision D1 (closed): auto-accept within tolerance, with controls
| Requirement | How it is met |
|---|---|
| Within tolerance: accept and close automatically | Already true: the import is accepted through the normal close, with the tolerance in force |
| Outside tolerance: flag for supervisor review | The normal close refuses a variance outside tolerance without a reason, so the import stays pending; the exception recipients are now told why |
| Missing readings or incomplete cash-up: never auto-accept | The ingest requires cash, card, account, other and at least one reading; an import with problems is never auto-accepted; a missing or unmapped pump or closing meter makes the close refuse |
| Repeated variances: extra investigation alert | New rule: N shifts outside tolerance by one attendant within D days (the company's numbers). Alerts at N, then at each further multiple of N |
| Record variance, tolerance, actor, time | New `fuel_shift_auto_accept_log`: shift, attendant, variance, tolerance applied, actor `SYSTEM`, time. Shown on the Imported shifts screen |
| Tolerance per tenant, site, product | Company-wide stays. A depot (site) can have its own cash tolerance (a shift drawing on several depots uses the smallest). A fuel type can have its own gauge difference limit |
| Notify shift supervisor on auto-accept; site manager on exceptions; admins only escalations or summaries | Three alert topics (below), using the named-user alert recipients and the D3 "leave administrators off" switch |

## Alert topics (Who is told about alerts)
- Shifts accepted automatically: only the people chosen (routine; not sent to administrators, so a company that chooses nobody gets no routine notices).
- Shifts that need review: administrators plus the people chosen (the site manager, for example). Covers an import left for a person, and any shift that closes outside tolerance.
- Repeated shift variances: administrators plus the people chosen.
- Choosing people is by name, per topic. There is no site on a shift in the data, so notices are not routed per site yet.

## Changes
- V712: `fuel_tolerance_overrides`, `fuel_shift_variance_policy`, `fuel_shift_auto_accept_log`.
- `FuelToleranceService` (overrides, rule, lookups that never fail a close), `FuelShiftAlertService` (notices, repeat check; swallows its own errors), `FuelForecourtService.closeShift` uses the depot tolerance and calls the alerts, `FuelImportAutoAcceptService` logs and notifies and uses the per-product gauge limit.
- Endpoints: `GET /api/v1/fuel/shift-controls`, `PUT/DELETE .../overrides`, `PUT .../repeat-policy` (configuration permission); `GET /api/v1/fuel/import-auto-accept/log` (configuration or shift supervise).
- Three new notification types (FUEL_SHIFT_AUTO_ACCEPTED, FUEL_SHIFT_NEEDS_REVIEW, FUEL_SHIFT_VARIANCE_REPEATED).
- Web: "Tolerances by site and product" and the repeat rule, and a "Shifts accepted automatically" list, on Imported shifts for people with the configuration permission.

## Limits and choices to confirm
- The repeat rule counts by attendant name (the only identity on a shift), ignoring case.
- A tolerance override applies to a shift when the tank is placed in a depot; a tank with no depot uses the company-wide number.
- The alerts point at a shift; they say nothing about cause or fault. Nothing is written off.
- The new notice is sent inside the close transaction, after the shift is saved; if the close later fails and rolls back, a notice could already have gone. The close does not fail because of a notice.
