# Fuel: leave administrators off an alert notification (D3) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests pass.

## Rule (decision D3, closed)
- Per alert type, a company can leave administrators off the routine notification. Default off, so every company behaves as before.
- Only allowed while at least one other person is chosen. Removing every other person puts administrators back on.
- If the chosen people cannot be reached (none resolve, or the lookup fails), administrators are told anyway: an alert is never left unseen.
- Affects only this notification. Administrators still see the alert in the app, and mandatory security/compliance escalations do not use this list.

## Changes
- V710 `fuel_alert_topic_settings` (tenant, topic, exclude_admins).
- `FuelAlertTopicSetting` + repository; `FuelAlertRecipientService.replace(..., excludeAdmins, by)` and `recipientsFor` honour it.
- `PUT /api/v1/fuel/alert-recipients/{topic}` accepts optional `excludeAdmins`; `GET` returns it.
- Web: "Leave administrators off this notification" box, disabled until someone else is chosen.

## Not done yet (in the register)
- Only one alert topic exists (delivery variances). Shift, stock and compliance topics, and role-based (not just named-user) targeting, come with D1 and the role work.
