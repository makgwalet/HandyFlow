# Fuel Phase 1: operating profile, divisions, legal entities (patches 0767 backend, 0768 web)

Apply with `git am -3`. Migration **V658** (next free is V659). Nothing was pushed.

## 1. What was implemented
- **Operating profile** per tenant: status NOT_STARTED / IN_PROGRESS / ACTIVE, business size, org structure, wizard step and answers (save and resume).
- **Divisions**: WHOLESALE_SUPPLY, DISTRIBUTION, RETAIL_FORECOURT, TRUCK_STOP, each switchable on/off after setup with a reason. Rows are never deleted; the history of who/when/why is kept.
- **Legal entities**: create, edit, remove (refused while depots use one). Depot assignment exists in the API only (`PUT /depots/{id}/legal-entity`); no screen yet.
- **Backend division enforcement**: one interceptor on `/api/v1/fuel/**` maps the first path segment to the divisions allowed to use it and returns 403 `FUEL_DIVISION_NOT_ACTIVE` otherwise. Shared foundation (tanks, depots, stock, ledger, compliance, dashboard, settings, suppliers, operating-profile, legal-entities, portal paths) is always open.
- **Deactivation safeguards**: open shifts block leaving Retail; open deliveries, confirmed/part-delivered orders and live reservations block leaving the last of Wholesale/Distribution; awaiting POs block leaving the last of Wholesale/Retail. The last division cannot be turned off.
- **Web**: Setup & divisions and Legal entities tabs (Setup group), a three-step wizard (divisions, business, review and activate), a banner until setup is finished, and sidebar sections hidden for divisions that are off (via a small store; the dashboard is never hidden).

## 2. Existing functionality preserved
- **Existing tenants**: V658 backfills an ACTIVE, `legacy_mode = true` profile for every tenant that has any tanks, depots, suppliers, customers, orders, deliveries or pumps. Legacy mode means all four divisions are on, so nothing is blocked. Completing the wizard moves them to real divisions.
- No existing table, column, endpoint or migration was changed. Only additions: new tables, two new permissions, small repository methods.
- Existing Fuel permissions and roles are untouched.

## 3. Rules (path to division)
| Path | Divisions allowed |
|---|---|
| forecourt | Retail |
| tankers, drivers, planning, driver | Distribution |
| customer-accounts, customer-sites, orders, invoicing, prices, deliveries | Wholesale or Distribution |
| purchase-orders, supplier-invoices | Wholesale or Retail |
| everything else | always open |

## 4. Database (V658, additive only)
`fuel_operating_profiles` (unique per tenant), `fuel_division_activations` (unique per tenant + division), `fuel_legal_entities` (unique name per tenant, soft delete). Permissions `FUEL_CONFIGURATION_READ` (NORMAL) and `FUEL_CONFIGURATION_MANAGE` (SENSITIVE), granted to ADMIN.

## 5. API
`GET /operating-profile` (FUEL_READ); `PUT /operating-profile/wizard`, `POST /operating-profile/activate`, `POST /operating-profile/divisions/{d}/activate|deactivate` (CONFIGURATION_MANAGE); `GET /legal-entities` (CONFIGURATION_READ or MANAGE); `POST/PUT/DELETE /legal-entities`, `PUT /depots/{id}/legal-entity` (MANAGE). All under `/api/v1/fuel`.

## 6. Defaults I chose (your four questions were not answered)
1. **Fuel to Accounts Payable**: still not allowed. Not touched in Phase 1.
2. **`fuel_tankers`**: left as a Fuel table.
3. **Who may switch divisions**: `FUEL_CONFIGURATION_MANAGE`, held by ADMIN only. Other roles can be granted it.
4. **Truck Stop reservations**: not started. TRUCK_STOP can be switched on but has no screens or routes yet.

## 7. Verification: what was and was not run
- **Java is parse-only** (all new and changed files parse). **Maven has not been run by me**, including the new tests: `FuelDivisionRulesTest`, `FuelDivisionGuardTest`, `FuelOperatingProfileServiceTest` (13), `FuelLegalEntityServiceTest` (5). Expect possible compile or Mockito fixes on first run.
- **V658 was run twice in a scratch Postgres 16** (idempotent) with stub parent tables: backfill created legacy profiles for tenants with data, permissions and ADMIN grants were created, and the CHECK and unique constraints reject a bad status, a bad division and a duplicate division. It has not been run through Flyway.
- **Web**: vitest passes for fuel, fuel-portal, navigation and shell tests; `tsc` result is stated in the delivery message.
- Not tested end to end against a running backend or browser. `ArchitectureVerificationTest` not run (GAP-A01 still open).

## 8. Risks and gaps
- **A tenant with no profile gets 403 on every division route** until an admin completes setup. That includes a tenant that starts using Fuel after V658, and any data seeded after the migration (tests, demo data). Seeders should create a profile.
- **Non-ADMIN roles cannot run setup** until granted FUEL_CONFIGURATION_MANAGE. A user with only FUEL_READ sees the banner but cannot act.
- **Customer portal is not division-gated** (its tenant comes from the access grant, not the session).
- Pending approvals are not checked when deactivating a division.
- The wizard asks only about divisions, size and structure. No per-division questions, operating rules, user access, licences or depot assignment screen yet.
- No controller-level, security or cross-tenant tests exist for the new endpoints (the interceptor is unit-tested only).

## 9. Next
Run Maven and the architecture test, then fix any failures. Then Phase 2 (Wholesale), after the P0 items in the gap register (H01 tests, A01) are addressed.
