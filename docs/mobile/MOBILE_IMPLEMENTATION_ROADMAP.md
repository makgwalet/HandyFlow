# Mobile implementation roadmap

Relative complexity only (S, M, L, XL). No dates: not enough information to commit to them.

## Phase 0: Discovery and foundation `[Proposed]`
| Item | Complexity | Notes |
|---|---|---|
| Decide the open items in `MOBILE_ECOSYSTEM_OVERVIEW.md` section 4 | S | Approval needed first |
| Reorganise the folders; untrack `node_modules` and `.env`; fix `AGENTS.md` | S | |
| Monorepo skeleton, catalogue check, CI | M | |
| SDK 58 compatibility spike (React 19, navigation choice, nfc library) on a throwaway branch | M | Decides re-scaffold vs upgrade |
| Backend: B1, B2, B3, B7, B9 (auth, device, config, access summary) | L | Blocks every app |
| Backend: B5 idempotency for Fuel writes | M | Blocks Fuel offline writes |
| `packages/`: tokens, ui, api-client, auth, tenant, permissions, offline | L | |
| Re-baseline Fuel `BACKEND_GAPS.md` | S | Done in outline in the module assessment |
**Readiness to leave Phase 0:** a signed-in "hello tenant" app on the target SDK that shows permissions, handles 401/402/403, queues and replays one idempotent write, and builds a preview binary in CI.

## Phase 1: High-value operational apps
| App | Why | Dependencies | Risk |
|---|---|---|---|
| **Fuel** (driver first, then forecourt operator) | Backend driver API, idempotency on delivery paths and a web offline queue already exist; prototype has about 60 screens to port | Phase 0; B5, B6 for forecourt | Port from SDK 52 and React 18; prototype was designed on a spec that is not in the repo |
| **Work** (employee: leave, payslips, attendance, expenses capture, approvals, tasks) | Used by every tenant, small API surface exists (`hr`, `expenses`, `approvals`, `tasks`) | Phase 0; push (B4) for approvals | Payslip security, geofenced clock-in policy |
Complexity: Fuel L to XL, Work L.

## Phase 2: Additional operational apps
Fleet driver (M to L, shares driver identity with Fuel), Earthmoving operator (L), Warehousing scanner (L), Security guard (M, backend ready; locate or rebuild the referenced "Shield" app), Agriculture (M, existing app migrated to the standards).

## Phase 3: Professional services and business management
Clinic practitioner (XL, privacy review first), Projects site app (L), Facilities/FM technician (L), Events check-in (S), POS stocktake (M), Training assessor (M).

## Phase 4: Customer, supplier and external
Fuel portal app, FM and property portal apps, client portal apps (each M, reuse portal JWT), property inspector `[Deferred]`, collections and internal-audit field tools `[Deferred]`.

## Cross-phase risks
- Backend auth changes touch the most sensitive code; they need a security review.
- Two driver concepts (Fuel and Fleet) must be unified before both apps ship.
- SDK cadence: a new SDK will appear while Phase 1 is in progress; the policy (every second release) applies.
- A single large foundation phase can stall visible progress; ship the Phase 0 hello-tenant build early.

## Recommended sequence
1. Approvals. 2. Folder clean-up. 3. SDK spike. 4. Backend auth (B1, B2, B3, B7, B9). 5. Shared packages. 6. Fuel driver app on real endpoints. 7. B5 and Fuel forecourt. 8. Work app. 9. Phase 2 apps in order of tenant demand.
