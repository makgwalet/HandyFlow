# Fuel: switching the arrival check and POD policy on

Both controls default OFF so existing web screens and older apps keep working. Nothing is enforced until a company moves them up. From patch 0938 this is done in stages (see "Staged rollout" at the end), not with an on/off switch. This is how to do it without surprising drivers.

## 1. Look back first (read only)

`GET /api/v1/fuel/settings/enforcement-readiness?days=30` (needs `FUEL_CONFIGURATION_MANAGE`; `days` is 1 to 180).

| Field | Meaning |
|---|---|
| `delivered` | Loads completed in the window |
| `wouldRefuseNoReceiver` | Completed with no receiver name and no exception used. The POD policy would have refused these |
| `wouldRefuseNoArrivalCheck` | Completed with no arrival check on record. The arrival check policy would have refused these. A stale or blocked check is **not** counted (history does not say what the check looked like at completion), so the real number is at least this |
| `arrivalCheckEnforced`, `podPolicyEnforced` | Current switch positions |

A high number is not a fault in the data: it is how many loads drivers complete today without the step. Expect the arrival check number to be close to `delivered` until drivers have the app that makes the check.

## 2. Order

1. Get the new Fuel app to drivers first (it makes the arrival check and asks for the receiver name). Enforcing before that stops every load.
2. Turn on the **POD policy** first. It only asks for a receiver name or an authorised exception.
3. Mark customer sites that really are unattended or automated (`PUT /customer-sites/{id}/pod-exception`, API only for now) and pre-approve after-hours deliveries, so legitimate deliveries have a way through.
4. Run the look-back again after a week. Turn on the **arrival check** when `wouldRefuseNoArrivalCheck` is close to zero.
5. Grant `FUEL_ARRIVAL_CHECK_OVERRIDE` and `FUEL_POD_EXCEPTION_APPROVE` to the people who will be called at 2 a.m. They are granted to no role by default.

## 3. Going back

Move the control back down a stage (to MONITOR or OFF) with a reason. That stops new refusals at once; exceptions and releases already recorded stay in the audit trail.

## Open

The look-back is counts only. It does not name loads or drivers, and it does not predict stale checks.

## Staged rollout (OFF → MONITOR → PILOT → ENFORCED)

The arrival check and the POD policy are no longer on/off switches. Each is in one of four states per company, changed from `PUT /api/v1/fuel/settings/enforcement/{control}` (controls `ARRIVAL_CHECK`, `POD_POLICY`; needs FUEL_CONFIGURATION_MANAGE).

| State | What happens |
|---|---|
| OFF | Nothing is asked, as before. |
| MONITOR | Nothing is refused. What would have been refused is recorded (`GET .../enforcement/{control}/observations`). |
| PILOT | Enforced for deliveries from the chosen depots; watched (recorded) everywhere else. |
| ENFORCED | Enforced for everything. |

Rules, checked by the server (`GET .../enforcement/{control}/blockers?target=...` lists them without changing anything):
- one stage at a time going up (OFF→MONITOR→PILOT→ENFORCED); going back down is always allowed (that is the rollback);
- the stage before must have run at least 7 days;
- a pilot needs at least one real depot;
- enforcing is blocked while more than 5% of the last 7 days' completed deliveries would have been refused;
- every change needs a reason and is audited (`FUEL_ENFORCEMENT_CHANGED`: control, before/after state, depots, reason, who, when, version).

Never waived by a watch-only stage: the live safety-critical arrival check (blocking eligibility findings, expired statutory certificates). Only OFF skips it, as before.

Completed deliveries are never re-judged when a state changes. Companies that already had a switch on were set to ENFORCED by migration V740, so nothing changes for them. The old `enforced` flag on `PUT /arrival-check` and `PUT /pod-policy` can no longer change state (it answers with a message pointing here); the flags are kept in step and are true only when the state is ENFORCED.

Not built yet: a screen for this in the web app, scope below depot level (sites, customers, divisions), and a prerequisite check for "approvers configured".
