# Fuel: who is told about delivery variances (D3)

Date: 2026-11-17. Written, not compiled or run (Java).

## What it does
A company can add named people who are told about delivery variances **in addition to** its administrators. Nobody added means administrators only, exactly as before.

## Pieces
- V704 `fuel_alert_recipients` (tenant, topic, user; unique per tenant+topic+user).
- Notifications module: `TenantAdminRecipients.resolveUsers(tenant, userIds)` (default empty), implemented in identity with JDBC (active users of that tenant with an email only).
- `FuelAlertRecipientService`: `list`, `replace` (max 10, every person must resolve as an active user of the company, empty list clears), `recipientsFor` (admins + extras, each person once; falls back to admins on any lookup failure).
- `GET /api/v1/fuel/alert-recipients`, `PUT /api/v1/fuel/alert-recipients/{topic}` (FUEL_CONFIGURATION_MANAGE; no division rule, like access limits).
- `FuelVarianceNotificationScheduler`: both sweeps (raised, overdue) now use `recipientsFor(tenant, VARIANCE)`.
- Web: "Who is told about alerts" card on the Access limits tab.

## Deliberately not done
Only the variance topic exists. Low stock, compliance, automation and delivery alerts still go to administrators; the topic list is a map, so adding one means adding a key and switching that caller.
Existing `FuelVarianceNotificationSchedulerTest` now mocks `FuelAlertRecipientService` (delegating to the admins mock).

## Decision for you (D3)
Is "administrators plus chosen people" enough, or should some topics be able to exclude administrators?
