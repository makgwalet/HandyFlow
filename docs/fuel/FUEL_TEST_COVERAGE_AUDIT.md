# Fuel: test coverage audit and baseline

## Baseline (what is and is not known)
- **Backend:** 22 test classes under `fuel`, 181 `@Test` methods, all Mockito unit tests (services and entity rules). The most recent Maven attempt by the owner (2026-11-11 19:49) stopped at test compilation on two errors in `FuelPortalDataServiceTest` (ambiguous `resolveTenantAdmins` mock), fixed in patch 0765. **No full backend test run has been reported since**, so pass/fail counts, skipped tests and the state of `ArchitectureVerificationTest` are **unknown**.
- **Frontend:** `npx vitest run src/pages/fuel src/pages/fuel-portal` → 28 files, 154 tests passed (run 2026-11-11 19:41); `tsc -p tsconfig.app.json --noEmit` clean at that time.
- **Not present in the Fuel test tree:** controller (MockMvc) tests, security/permission tests, cross-tenant tests, repository/integration tests against PostgreSQL, Flyway migration tests, concurrency tests for stock allocation, end-to-end tests.

## Coverage by area (unit level only)
Stock ledger, stock service, orders, order entity, customers, pricing, purchasing, supplier invoices, invoicing, transport, forecourt, compliance (service and scheduler), dashboard, cost-to-serve, portal data, settings, facade, request validation, integrity rules.

## Gaps
Tenant isolation, permission enforcement, concurrent allocation, idempotent retries, migration correctness, offline queue behaviour in the browser, and the architecture rule for fuel/fleet. These are the first tests to add (GAP-H01).
