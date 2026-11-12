# FUEL H02: Portal entitlement check and variance alert (2026-11-12)

## 1. Implemented
- Customer portal checks the supplier tenant's Fuel entitlement on every request (`FeatureGuard.hasModuleFor(tenantId, "fuel")`, same test as the staff guard: active subscription, not suspended or cancelled, module in plan). Failure looks like "not found".
- Daily delivery-variance alert for tenants that set a tolerance (new `FuelVarianceNotificationScheduler`, new notification type `FUEL_VARIANCE_RAISED`, in-app and email).

## 2. Preserved
- Portal grants, ids and responses are unchanged for entitled tenants. Variance detection, the list screen and decisions are unchanged; the list still detects on open.

## 3. Files
- Java: `FeatureGuard` (`hasModuleFor`, `hasModule` now uses it), `FuelPortalDataService`, `FuelVarianceService.raiseNew`, `FuelVarianceNotificationScheduler`, `FuelSettingsRepository.findTenantsWithVarianceTolerance`, `NotificationType`.

## 4. DB
- None. The notification type is an enum with no database constraint.

## 5. API / 6. Web
- None.

## 7. Security
- Closes the known portal gap: a lapsed or suspended tenant's customers can no longer read orders or deliveries or send requests. The all-tenants settings query is on the scoping test's allow-list (scheduler only).

## 8. Tests (Java, not run, parse-checked only)
- New `FeatureGuardTest` (4), `FuelVarianceNotificationSchedulerTest` (6), `FuelPortalDataServiceTest` +1 (and a default stub so existing tests stay entitled).

## 9. Gaps and risks
- If the notification fails after variances are saved, they still show in the list but that run's alert is lost.
- Only administrators are told. No reminder for variances left open.
- The alert runs for every tenant with a tolerance; turn it off with `fuel.variance.alerts.enabled=false`.

## 10. Next
- Route entity for multi-stop (C04 part 2), then F02.
