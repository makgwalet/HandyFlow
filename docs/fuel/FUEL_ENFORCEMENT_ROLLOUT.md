# Fuel: switching the arrival check and POD policy on

Both switches default OFF so existing web screens and older apps keep working. Nothing is enforced until a company turns them on. This is how to do it without surprising drivers.

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

Both switches can be turned off again in Operations policy. Turning off stops new refusals; exceptions and releases already recorded stay in the audit trail.

## Open

The look-back is counts only. It does not name loads or drivers, and it does not predict stale checks.
