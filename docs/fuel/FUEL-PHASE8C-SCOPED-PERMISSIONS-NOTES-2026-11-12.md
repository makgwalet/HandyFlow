# Fuel Phase 8c: division and depot access limits (G01 scope, patch 0808)

## Finding that changed the plan
The `FuelDivisionRules` list named `returned-fuel`, which is a screen id. The real API is `/api/v1/fuel/load-returns/...`, so the returned-fuel endpoints were not gated by the Distribution division. Fixed here (with a test).

## 1. Implemented
- Per-user limits on divisions and depots. A user with no rows is unrestricted, so existing users keep their access.
- Enforced backend-side in the existing `FuelDivisionInterceptor`: division-gated routes need at least one of the route's divisions in the user's set; routes about one tank or one depot (including `/forecourt/tanks/{id}`) need that tank's depot in the user's depots.
- `GET /tanks` and `GET /depots` are filtered to the user's depots.
- Admin screen: Setup > Access limits (list, edit, save). Saving replaces the user's whole set; empty removes every limit.
- This is an extra control beside permissions and tenant divisions, never a replacement.

## 2. Preserved
No new permission names (uses `FUEL_CONFIGURATION_MANAGE` / `FUEL_READ`). Impersonation sessions and calls with no identified user are not limited here. Tenant division activation still applies first. Shared foundation routes are never blocked by a division limit, so a limited user can still reach `/fuel/scopes/me`.

## 3. Files
`FuelUserScope`, `FuelUserScopeRepository`, `ScopeDtos`, `FuelScopeService`, `FuelScopeController`; edits to `FuelDivisionInterceptor` (new autowired 2-arg ctor, old 1-arg kept), `FuelDivisionRules`, `FuelTankController`, `FuelStockController`. Web: `AccessLimitsTab`, `scopeOps`, `FuelPage`, `moduleSections`.

## 4. DB
V684 `fuel_user_scopes` (division XOR depot per row, unique per user, tenant-scoped). New table only.

## 5. API
`GET /api/v1/fuel/scopes` and `PUT /scopes/users/{userId}` (`FUEL_CONFIGURATION_MANAGE`); `GET /scopes/me` (`FUEL_READ`). All call `requireModule("fuel")`. Error codes: `FUEL_SCOPE_DIVISION`, `FUEL_SCOPE_DEPOT` (403).

## 6. Security
Server-side, tenant-scoped queries only. A depot-limited user is refused a tank with no depot. A tank that does not exist is left to the controller (404), not turned into 403.

## 7. Tests
`FuelScopeServiceTest` (12), `FuelDivisionRulesTest` (+1), web `AccessLimitsTab.test` (9 incl. ops). Web tests and tsc pass. Java is parse-checked only; I could not run Maven here.

## 8. Gaps
- Lists and reports not tied to one tank or depot (orders, group report, stock ledger summaries, shifts by site) are not depot-filtered. Divisions are enforced everywhere; depot filtering covers tanks, depots and any path naming one.
- Forecourt sites have no depot link yet (8d adds it), so site-based forecourt screens are not depot-limited until then.
- The customer portal is outside this interceptor (already tracked).
- The Access limits screen reads users from `/identity/users`; without that permission it falls back to ids that already have limits.

## 9. Risks
The interceptor now does one small query per request for users that have limits, and one for everyone (to find no rows). Cache if profiling shows cost. Apply after V684.

## 10. Next
8d: link forecourt sites to their supplying depot, then use it for depot reporting and limits.
