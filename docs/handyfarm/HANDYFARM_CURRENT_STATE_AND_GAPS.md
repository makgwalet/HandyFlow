# HandyFarm: current state, traceability and gaps

Basis: read-only inspection of `fmwork`, branch `fuel-stage0`, on 2026-11-18. Nothing was compiled, run or installed for this document. Counts come from grepping source. Where I did not look, the row says `not checked`.

Tags: `[Verified]` seen in code, `[Partial]` something exists but short of the requirement, `[Gap]` not found, `[Proposed]`, `[Requires approval]`.

The approved plan asks for a repository-backed specification before building. This is the first part of it: inventory, requirement-to-code traceability and gap register. Screen specs, API change register, permission matrix and tickets are later documents.

## 1. What exists

### 1.1 Backend: Agriculture module `[Verified]`
- Package `platform/src/main/java/za/co/handyflow/platform/agriculture/`; 19 controllers under `/api/v1/agriculture/**`; about 172 mapped endpoints.
- Controllers: Animal, CostEntry, CostReporting, CropCycle, CropType, Dashboard, Enterprise, EquipmentFuel, Farm, Group, InventoryItem, Labour, ProductionArea, Profitability, Sales, Season, Species, Supplier, Trends.
- 24 entities: Animal, BreedingRecord, CostEntry, CropCycle, CropType, Enterprise, Farm, FeedRecord, FinanceSettings, Group, HarvestRecord, HealthEvent, InputApplication, InventoryItem, MortalityRecord, MovementRecord, ProductionArea, SalesAllocation, ScoutingRecord, Season, Species, StockMovement, TrackingTarget, WeightRecord.
- Migrations V264 to V311 (agriculture, crops, cost ledger, sales allocations, labour costing, equipment and fuel guard, breeding stock, supplier references).
- Permissions in controllers: `AGRICULTURE_READ`, `AGRICULTURE_MANAGE`, `AGRICULTURE_FINANCE`, `AGRICULTURE_ADMIN`.
- Cross-module facades used: `HrFacade`, `FleetFacade`, `FuelFacade`, `InvoicingFacade`, `SupplierFacade`.
- Mobile-oriented endpoints already present: `GET /farms/{id}/today-summary` and `GET /farms/{id}/attention` (the earlier mobile gap report listed these as missing; they are now built), plus evidence list, download and detach for animals, crop cycles and groups.
- Health events carry `withdrawal_period_days` and `nextDueDate`. 54 Java test files exist under `agriculture`.
- Design records: `ADR-001` (financial integration: snapshot rule, gross margin not net profit, no parallel ledgers) and `ADR-002` (weather and NDVI, status Proposed, nothing built).

### 1.2 Web portal `[Verified]`
`handyflow-web/src/pages/agriculture/` has tabs for farms, animals, groups, enterprises, crop types, crop cycles (inputs, scouting, harvests), health, movement, mortality, breeding, feed, inventory, labour, equipment fuel, cost ledger, cost reports, margin overview, evidence, and a dashboard with a farm map (react-leaflet, farm GPS points only). Pages are farm-scoped by a `?farm=` selector; there is no all-farms view because no tenant-wide endpoint exists.

### 1.3 Existing HandyFarm mobile app `[Verified]`
Location: `HandyFlow-Apps/handyfarm-offline-increment/handyfarm/`.
- Expo 52.0.46, React Native 0.76.7, React 18.3.1. About 3,500 lines of TypeScript in `src/`. No tests found. Four design documents under `docs/agriculture/` (overview, API map, API gaps, offline architecture).
- Libraries present: TanStack Query, Zustand, React Hook Form, Zod, expo-sqlite, expo-secure-store, expo-camera, expo-location. Axios client sends a bearer token.
- 16 screens: Home, crop cycle list and profile, record planting, input application, scouting, harvest, farm list and hub, animal list and profile, group profile, record weight, treatment, mortality.
- Offline: a SQLite `pending_mutations` queue (status PENDING, SYNCING or FAILED, attempt count, last error) replayed by `useOfflineSync`.
- Placeholder screens remain for unbuilt areas.

### 1.4 Sibling mobile apps (the foundation to reuse) `[Verified]`
- `HandyFlow-Apps/handyflow-fuel/`: Expo `~58.0.24`, React Native 0.88.1, React 19.3.0; has `app.config.ts`, `eas.json`, Jest config, five feature packages, and `src/` folders for auth (including a device store), offline (outbox, SQLite store, send), push and access. This supersedes the 12 Nov overview, which called it an untouched scaffold.
- `HandyFlow-Apps/handyflow-guard/`: has `app.config.ts`, `eas.json`, tests.
- `HandyFlow-Apps/handyfarm-sync-alerts/handyfarm/`: the earlier Fuel prototype, still named `handyfarm`.

## 2. Requirements traceability

| Approved requirement | Backend | Web | Mobile app | Status |
|---|---|---|---|---|
| Multiple farms, enterprises, production areas | Farm, Enterprise, ProductionArea, Season | Yes | Farm list and hub | `[Partial]` farms and areas exist; no legal-entity level (no `legalEntity` reference in the module) |
| Hierarchy Legal Entity → Farm → Unit → Enterprise → Cycle | Farm → ProductionArea; Enterprise has `farmId`; CropCycle | Partial | Partial | `[Partial]` |
| Farm-level user access scoping | none found (rows are farm-owned, but no user-to-farm access list) | Farm picker only | n/a | `[Gap]` |
| Crop lifecycle and activities | CropType, CropCycle, InputApplication, ScoutingRecord, HarvestRecord | Yes | Planting, input, scouting, harvest screens | `[Verified]` core; rotation rules and soil records `not checked` |
| Individual animals and groups | Animal, Group, Species, MovementRecord | Yes | Lists, profiles | `[Partial]` both exist; animal-to-group transition keeping history `not checked` |
| Livestock health and withdrawal | HealthEvent with withdrawal days and next due date | Yes | Treatment screen | `[Partial]` periods stored; server-side enforcement and treatment-conflict detection `not checked` |
| Births, deaths, weights, breeding, feed | BreedingRecord, MortalityRecord, WeightRecord, FeedRecord | Yes | Weight and mortality screens | `[Partial]` no mobile screens for breeding, feed, birth |
| Poultry, aquaculture | none | none | placeholders | `[Gap]` (deferred) |
| Field Worker Mode | no task facade or worker-assignment model found in the module (grep for `TaskFacade` and `assignedTo` returned nothing) | none | none | `[Gap]` P0 |
| Offline capture, server validation | no idempotency key or client operation ID in the module | n/a | SQLite queue exists | `[Partial]` queue exists; idempotency, conflict policy, evidence upload recovery, revocation `[Gap]` |
| Photo and evidence capture | Evidence endpoints for animals, cycles, groups | Evidence tab | camera dependency present | `[Partial]` no verified mobile upload flow |
| GPS capture | Farm has one lat/long point | Farm map | location dependency present | `[Partial]` no boundaries, no field polygons |
| Farm map and boundaries | points only | Leaflet map of farms | stub | `[Gap]` for boundaries and unit geometry |
| Manual weather and observations | none (only a mention inside input application) | none | none | `[Gap]` (ADR-002 covers external data, not manual capture) |
| Basic input catalogue and consumption | InventoryItem, StockMovement, InputApplication | Inventory tab | none | `[Partial]` batch and expiry fields not found on stock entities |
| Basic purchasing without Supply Chain | Supplier references via `SupplierFacade` only | none | none | `[Gap]`: no purchase request, quotation, PO or goods-received flow in the module |
| Optional Supply Chain integration | `SupplierFacade` (10 uses) | partly | none | `[Partial]` |
| Production costing, margin | CostEntry, CostReporting, Profitability, SalesAllocation, Labour, EquipmentFuel, FinanceSettings | Cost ledger, reports, margin overview | none | `[Verified]` web and backend strong; no mobile |
| Cost snapshot on closure | snapshot of seed unit cost seen in cycle service; labour and equipment rate snapshots per ADR-001 | n/a | n/a | `[Partial]` a closed-cycle freeze of all rates `not checked` |
| Role-aware dashboard | `AgDashboardController`, today-summary, attention | Dashboard | Home | `[Partial]` |
| Compliance register, biosecurity, incidents | none found | none | none | `[Gap]` |
| Android, iOS, tablet | n/a | n/a | one app, no EAS config, no tablet layouts checked | `[Gap]` |

## 3. Gap register (backlog seeds)

| ID | Gap | Why it matters | Priority |
|---|---|---|---|
| G1 | Mobile app is on Expo SDK 52 / RN 0.76 while Fuel and the standards use SDK 58 / RN 0.88 | Cannot share foundation packages; re-scaffold or upgrade needed `[Requires approval]` | P0 |
| G2 | No idempotency or client operation ID in Agriculture write endpoints | Offline retries can create duplicates | P0 |
| G3 | Offline queue lacks conflict policy, dependency ordering, evidence upload recovery, visible sync states | Approved offline rules not met | P0 |
| G4 | No per-user farm and production-unit access scope | Farm boundaries and worker restriction cannot be enforced | P0 |
| G5 | No Field Worker task, assignment or verification model | Field Worker Mode cannot be built | P0 |
| G6 | Only four coarse permissions; plan needs per-family permissions (animal health administer, movement approve) | Worker and vet separation | P0 |
| G7 | No legal-entity level | Multi-entity tenants | P1 |
| G8 | No basic purchasing flow inside Agriculture | Approved decision 8 | P1 |
| G9 | No manual weather and observation records | Approved decision 7 | P1 |
| G10 | No compliance, biosecurity or incident engine | Plan P1 | P1 |
| G11 | No field or paddock geometry | Mapping, grazing, drawing boundaries | P1 |
| G12 | Stock has no batch and expiry tracking (not found) | Chemical and vaccine traceability | P1 |
| G13 | No mobile app tests, no `eas.json`, no app identifiers | Release gates | P0 |
| G14 | Poultry and aquaculture absent | Deferred by decision, but data model must not block them | P2 |
| G15 | Naming collision: the Fuel prototype folder is called `handyfarm` | Confusion about which app is HandyFarm `[Requires approval]` | P0 |
| G16 | Closed-cycle full-rate freeze to be confirmed | Historical margin stability | P1 |

Mobile auth needs (body-returned refresh token for native clients, device registry, push tokens, tenant re-check on refresh) were listed as blockers on 12 Nov. The Fuel app now has an auth context and a device store, so re-verify what the backend supports before treating this as open.

## 4. Decisions needed

1. Re-scaffold the HandyFarm app on the Fuel app's SDK and shared packages, or upgrade in place (G1). `[Requires approval]`
2. Where Field Worker tasks live: the shared task engine with agricultural context, or an Agriculture-owned assignment model (G5). `[Requires approval]`
3. How farm and production-unit scope is stored and enforced (G4, G6). `[Requires approval]`
4. Whether basic purchasing is built inside Agriculture or as a shared lightweight capability that Supply Chain later replaces (G8). `[Requires approval]`
5. Rename of the Fuel prototype folder (G15). `[Requires approval]`
6. Entitlement key and packaging for a HandyFarm-only tenant, including its stock capability. `[Requires approval]`
