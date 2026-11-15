# Fuel: delivery variance open too long alert (2026-11-15)

Status: written, not compiled or run (Java). Web tests and `tsc` pass. Flyway V695 is new.

## What it does
Each company chooses a number of days (1 to 365) under Fuel > Automation. Every morning, after the existing variance sweep, any delivery variance still OPEN or INVESTIGATING that was raised more than that many days ago produces one in-app and email notification to the company's administrators (new type FUEL_VARIANCE_OVERDUE), pointing to Fuel > Variances.

## Decisions
- **No default.** The number is the company's own. Empty means no alert, so existing companies see no change.
- **Once per variance.** `fuel_delivery_variances.overdue_alerted_at` is set after the notification is sent. If sending fails nothing is marked, so the next morning's sweep tries again.
- **Counted from when the variance was raised** until it is closed; investigating does not stop the clock, because the point is to get it to a decision.
- Administrators are told (the D3 default). The alert says nothing about cause or fault, and nothing is written off or changed.
- A company whose plan no longer includes Fuel is skipped; one company failing does not stop the next.

## Files
V695, FuelSettings.varianceReviewDays, FuelDeliveryVariance.overdueAlertedAt (read only, set by a guarded update), FuelDeliveryVarianceRepository (findOverdueNotAlerted, markAlerted), FuelVarianceService (overdueNotAlerted, markAlerted), FuelVarianceNotificationScheduler.sweepOverdue, AutomationSettings now carries varianceReviewDays (the Automation settings PUT sends both values), NotificationType.FUEL_VARIANCE_OVERDUE, AutomationTab days box.

## Limits
- One message per company per morning, listing the count, not each variance.
- Only administrators are told; routing to a named reviewer is not built.
