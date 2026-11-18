# Fuel push notification matrix (Batch C)

Status: implemented; backend not compiled or run in the build sandbox. Push stays off until `handyflow.fuel.push.enabled=true` and the app has an EAS project id.

| Event | Who is told | Recipient source | Event key (dedup) | Opens | Switch (per person) |
|---|---|---|---|---|---|
| New load (existing) | the assigned driver | the allocation | none (sent once at allocation) | Load | none |
| New stay request (existing) | staff chosen for stay requests | `FuelAlertRecipientService` | none | Stay requests | none |
| Overdue delivery, level 1 | the assigned driver | the load | `OVERDUE:{load}:L1` | Load | OVERDUE_DELIVERY |
| Overdue delivery, level 2 (escalation) | dispatch recipients, not the driver again | topic `DELIVERY_OVERDUE` (new topic in the same recipient service; administrators by default) | `OVERDUE:{load}:L2` | Overview | OVERDUE_DELIVERY |
| Dip variance | the people chosen for dip alerts (own topic, separate from delivery variances) | topic `DIP_VARIANCE` | `DIP:{reading}` | Tank | DIP_VARIANCE |
| Shift closed outside tolerance | the people chosen for shift review | topic `SHIFT_EXCEPTION` (existing) | `SHIFT:{shift}` | Shift | SHIFT_OUTSIDE_TOLERANCE |
| Driver arrival check blocked | the driver, plus operations recipients | driver from the check; topic `ARRIVAL_BLOCKED` (new topic; administrators by default) | `ARRIVAL:{load}:{block codes}` | Load (driver), Overview (operations) | ARRIVAL_CHECK_BLOCKED |

No recipient list is duplicated: the two new topics live in the existing alert-recipient service and appear in the same settings screen.

## Behaviour
- **Deduplication:** one row per person per event key (`fuel_push_events`, unique on company, key and person). Publishing the same event again queues nothing.
- **Preferences:** `GET/PUT /api/v1/fuel/push/preferences`; every type is on by default; an off type is recorded as SKIPPED.
- **Tracking:** each row ends as SENT, NO_DEVICE (no phone signed in; not retried), SKIPPED, or GAVE_UP. `GET /api/v1/fuel/push/recent` (FUEL_CONFIGURATION_MANAGE) shows what became of recent alerts.
- **Retry:** a failure is retried after 1, 5 and 15 minutes (3 attempts), then GAVE_UP with audit event `FUEL_PUSH_GAVE_UP`. A queue worker runs every 30 seconds.
- **Escalation:** driver first; dispatch after `overdue_escalation_minutes` (default 60) if the load is still open. A load with no driver on the app goes straight to dispatch. Grace before "overdue" is `overdue_grace_minutes` (default 120); both set at `PUT /api/v1/fuel/push/overdue-policy` (15 to 1440 minutes).
- **Audit:** `FUEL_PUSH_QUEUED` per publish, `FUEL_PUSH_GAVE_UP`, recorded as the system.
- **Deep links:** the alert names a screen and an id; the app ignores anything not on its list and checks ids. The screen then applies the person's own permissions.
- **A notification authorises nothing:** the alert carries no token or action. Every protected step is checked again by the server (see the arrival check and received-by policies).
- Alerts carry no figures: a title and one short line.

## Fixed along the way
`HrFacade.findEmployeeById` returned a summary with no login link, so the existing "New load" push (and anything alerting a driver) had no user to send to and never fired. It now carries the link (`HrService.linkedUserId`, with a test).

## Open points
- "Overdue" here is a push policy (open load past its time plus a grace). The dashboard still counts a load as overdue after 24 hours; the two should be aligned.
- Dip variance reuses the delivery-variance recipient topic. Say if you want its own topic.
- The existing email/in-app notice for dip variance still goes to administrators only; only the push uses the configured recipients.
- The app has no screen for a person's alert switches yet (API only), and the web has no screen for the overdue policy.
- Delivery receipts: "SENT" means Expo accepted the message, not that the phone showed it.

## Tests
Backend: `FuelPushEventServiceTest` (queue per person, duplicate key, preference off, nothing queued, never throws, sent, no device, retry, give up, disabled, preferences), `FuelOverdueDeliveryAlertsTest` (grace, driver first, escalation, no driver, company grace and module, limits, failure isolation), `FuelArrivalCheckServiceTest` (blocked alert recipients, dedup key, allowed sends nothing), `HrFacadeTest`. Mobile `push.test.ts` (222 mobile tests pass). Not covered: shift and dip hooks (no test of `FuelShiftAlertService`/`FuelService` publishing).
