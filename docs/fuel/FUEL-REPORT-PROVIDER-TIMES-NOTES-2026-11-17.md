# Fuel: provider times in shifts-by-date reports (decision 6) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests and tsc pass. Migration V715.

## Decision 6 (closed)
Actual provider times drive shifts-by-date reports in the site timezone; scheduled/system times stay separate; fallback is flagged.

## How it works
- **A company setting** (Settings, "Shift dates in reports"; configuration permission): "Date imported shifts by the times their pump or POS provider reported" plus the timezone that decides the day.
- **Off by default**, so every existing company's reports are unchanged. This is deliberate: the setting changes which day a shift counts in, so it is a choice, not a silent switch.
- **When on:** an imported shift counts on the day its provider closed it, even if it was accepted days later. The system's own opened/closed times are never changed (still used for ordering and audit).
- **Fallback flag:** an imported shift with no provider closing time uses the system time and is counted. The report says "N imported shifts had no provider time, so the system time was used". Manual shifts (never imported) are not counted as fallbacks, because they have no provider time to fall back from.
- **Timezone:** one company-wide timezone (default Africa/Johannesburg, as before). It decides the day boundaries of the period and of each shift. An unknown timezone is refused on save, and falls back to Africa/Johannesburg if one is ever stored badly.

## Reports that use it
Forecourt sales by supplying depot, forecourt supply reconciliation (litres sold), and the group report's forecourt sales. Each returns `shiftBasis` (SYSTEM or PROVIDER, timezone, fallback count) and shows the note only when the dating is not the usual one. The group report's delivery dates use the same timezone for the period.

## Not done
- **Per-site timezone.** Shifts have no site and depots have no timezone, so the timezone is company-wide.
- The rolling "last N days" forecourt summary (dashboard) is a rolling window from now, not a by-date report, and is unchanged.
- Shift detail screens already show the provider times (0861); nothing changed there.

## Changes
V715 (`fuel_settings.shift_report_use_provider_times`, `report_timezone`); `FuelShiftReportClock` (new) used by `FuelForecourtSupplyReportService`, `FuelForecourtSupplyReconciliationService`, `FuelGroupReportService`; `GET/PUT /api/v1/fuel/report-basis` (read FUEL_READ, write FUEL_CONFIGURATION_MANAGE); DTOs gain `shiftBasis` with compatible constructors. Web: Settings card, one-line note on the three reports. Tests: supply report (provider dating, default, fallback, timezone), Settings, ops.
