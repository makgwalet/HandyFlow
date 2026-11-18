# HandyFarm: architecture

Status tags as in `HANDYFARM_PRD.md`. Everything marked `[Proposed]` is a target, not a claim about the repository. Verified facts are in `HANDYFARM_CURRENT_STATE_AND_GAPS.md`.

## 1. Shape
One independent mobile app (HandyFarm) on the shared HandyFlow mobile foundation and the existing Spring Boot backend. The backend is the source of truth; SQLite on the device holds a tenant-scoped working copy plus an outbox.

```
HandyFarm app (Expo / React Native)
  Crop Production · Livestock · Farm Workforce · Farm Resources · Production Costing · Weather & Observations
  Shared mobile foundation: auth · tenant context · permissions · API client · offline DB · sync · evidence capture · notifications · maps/GPS · audit
Backend: Java 21 · Spring Boot · Spring Modulith · PostgreSQL · Agriculture module + shared services
```

HandyFarm must not load Fuel, Supply Chain or Fleet features just because they share the foundation. Each app is independently installable, buildable, testable and releasable, with its own application identifier, icon, splash, navigation, notification categories and offline workflows.

## 2. Engines (priority)
P0 means foundational to the initial product, P1 essential to a commercially useful first full release, P2 later.

| Engine | Priority | Responsibility |
|---|---|---|
| Farm hierarchy and configuration | P0 | Farms, production units, enterprises |
| Crop production | P0 | Crop lifecycle and activities |
| Livestock management | P0 | Individual and group/batch records |
| Offline and synchronisation | P0 | Durable capture, retries, conflicts |
| Identity and permissions | P0 | Tenant, farm and worker access |
| Field Worker workflow | P0 | Assignments and activity completion |
| Evidence and documents | P0 | Photos, documents, signatures |
| Production costing | P1 | Enterprise cost allocation |
| Farm inventory and consumption | P1 | Inputs and stock use |
| Basic purchasing | P1 | Farm procurement |
| Harvest and traceability | P1 | Output and history |
| Irrigation and water | P1 | Water assets and consumption |
| Equipment usage | P1 | Machinery and production allocation |
| Reporting and analytics | P1 | Operational and financial insight |
| Weather observations | P1 | Manual weather and environment data |
| Compliance and biosecurity | P1 | Evidence, risks, controls |
| Integration framework | P1 | Optional HandyFlow services |
| AI and predictive intelligence | P2 | Assisted insights |
| Sensor and satellite integrations | P2 | External data |

## 3. Hard rules `[Approved]`
1. **No duplicated ownership** (section 5). HandyFarm keeps agricultural context and cost allocation; it calls shared capabilities through stable service interfaces.
2. **Stable history.** Closing a production cycle snapshots rates and allocation inputs. Later HR or Fleet rate changes never restate closed margins. (Existing ADR-001 already requires a snapshot at costing time.)
3. **Offline sync is a business-critical subsystem.** Every offline write has a client-generated operation ID, a server acknowledgement, a retry policy, a conflict policy, an audit trail and a visible sync state. A locally saved record is never presented as server-approved.
4. **Smallholder to enterprise.** No long wizard for a small user; legal entities, farms, scopes and reporting for large ones.

## 4. Mixed-farm model `[Approved direction]`
`Tenant → Legal Entity → Farm → Production Unit → Enterprise → Production Cycle`. Access is scoped by legal entity, farm and production unit. Examples: Farm A maize, cattle and sheep; Farm B citrus, poultry and vegetables; Farm C dairy, grazing and fodder. Add production types without redesign; do not lock a tenant to its first choice.

Farm mapping `[Proposed]`: provider-independent map abstraction; GPS capture works without a particular map provider; user-drawn operational boundaries are kept distinct from legal cadastral boundaries, for which survey documents stay authoritative.

## 5. Integration ownership matrix

| Information | System of record | HandyFarm holds |
|---|---|---|
| Farm and agricultural production, crop cycles, animals and groups, agricultural activities, animal health and treatment | HandyFarm / Agriculture | Everything |
| Agricultural input consumption and allocation | HandyFarm | Everything |
| Customers | CRM | Reference |
| Sales invoices, receivables | Invoicing | Allocation of sale lines to output |
| General ledger | Accounting | Nothing (no GL posting) |
| Employees, pay rates, payroll | HR | Hours, assignment, rate snapshot |
| Equipment master, maintenance | Fleet | Usage and hours allocated to a cycle, cost snapshot |
| Fuel transactions | Fuel | Allocation of fuel use to field, cycle or group |
| Advanced procurement, supplier master | Supply Chain / shared supplier service | Reference; basic purchasing when not subscribed |
| Advanced warehouse stock | Warehousing / Inventory | Agricultural purpose and consumption context |
| Tasks | Shared task engine | Agricultural context, requirements |
| Documents, notifications, reporting | Shared services | Use |

Equipment example: a tractor works 6 hours on Field 12. HandyFarm records activity, field, cycle and the 6-hour allocation; Fleet keeps maintenance history; Fuel keeps fuel transactions.

Integration must prevent duplicate purchase orders, suppliers, goods receipts and stock movements when Supply Chain is also subscribed. Packaging consequence: a HandyFarm-only tenant still needs an adequate stock and input capability, so entitlements must not force an unrelated subscription for basic input use. `[Requires approval]`

## 6. Offline architecture

### 6.1 What works offline

| Function | Offline |
|---|---|
| View previously synced farm records, assigned tasks | Yes |
| Capture crop activities, animal observations, weights, inspections, harvest quantities | Yes |
| Record treatments | Yes, with controlled server validation |
| Scan animal tags, capture photos, GPS, signatures | Yes |
| Record births, deaths, movements, input consumption | Captured; server validates |
| Draft purchase requests | Yes |
| Final financial approval, final sales invoice | No |
| Authoritative stock posting | Server confirmation |
| Change compliance policy | No |
| Finalise high-risk animal movement approval | Online server validation |

Offline capture is not authorisation. Animal movement approvals, high-risk treatment conflicts, financial approvals and authoritative stock posting must not silently become final offline.

### 6.2 Lifecycle
Select assigned activity → capture locally → SQLite stores transaction and evidence references → outbox assigns idempotency key → connection detected → data and attachments upload → backend validates tenant, user, assignment and rules → server acknowledges or rejects → device updates authoritative status.

### 6.3 Requirements
Durable SQLite; transactional outbox; unique transaction IDs; idempotency; dependency ordering; resumable attachment upload; retry with backoff and limits; version-based conflict detection; device time versus server time; user and device revocation; tenant-scoped local storage; secure cached documents; logout data handling; schema migration with pending records; visible sync health. Visible states: saved locally, waiting, uploading, submitted, accepted, rejected, needs attention, conflict.

### 6.4 Livestock conflict rules
Two offline treatments for one animal: keep both, flag conflict or possible duplicate, never blindly merge medication records. Two offline movements: validate the latest location and movement history before accepting the second. A worker's newer animal, crop, stock or production record is never silently overwritten.

## 7. Security and permissions
Required: tenant isolation; legal entity, farm and production-unit scope; role-based permissions; device registration, revocation and remote logout; secure token handling; local data encryption strategy; attachment access control; audit logging; data minimisation; POPIA-aware retention; restricted veterinary information; GPS permissions; controlled sync. Tenant and user identity are never trusted from client input.

Proposed permission families (the existing module has four coarse permissions today; see current-state doc):

| Family | Example capabilities |
|---|---|
| Farm | read, create, update, manage |
| Land and fields | view, create, edit, map |
| Crop production | plan, record, manage, close |
| Livestock | view, register, edit, move, dispose |
| Animal health | record, administer, review, approve |
| Breeding | record, manage, approve |
| Poultry / aquaculture | manage flocks, batches, ponds, readings, harvest |
| Irrigation | view, record, operate where integrated |
| Workers | assign tasks, verify activities |
| Equipment, inputs | view, allocate, inspect, issue, consume, adjust |
| Harvest, sales | record, grade, approve; view, allocate, initiate |
| Compliance, reports, administration | view, capture, review, approve; view, export; configure |

A worker who may record a treatment must not automatically be allowed to administer every medication or approve a controlled movement.

## 8. API strategy
Reuse existing controllers and DTOs before adding endpoints. The planning papers propose mobile groupings `[Proposed]`, to be created only where reuse is not enough: `/api/v1/mobile/agri/{me, dashboard, farms, fields, crops, activities, livestock, health, poultry, aquaculture, irrigation, workers, equipment, harvests, inventory, sales, documents, sync}`. The existing surface is `/api/v1/agriculture/**`.

## 9. Technology `[Proposed]`, to be reconciled with the Fuel app
React Native, Expo, TypeScript, Expo Router, Zustand, SQLite, Expo SecureStore, React Hook Form with Zod, TanStack Query, provider-independent map abstraction, Expo Camera, barcode and QR scanning, Expo Location, shared push infrastructure, Jest, React Native Testing Library, Maestro, EAS Build. Inspect actual library versions before adopting or upgrading anything.

Target repository layout from the plans (`handyflow-mobile/{apps, packages, docs}`) is a target, not the current structure. The real layout is `HandyFlow-Apps/`; reconcile before moving files.

## 10. Admin and setup
Setup wizard steps (save and resume): business and farm; legal entities; properties and locations; divisions; enterprises; fields, paddocks and units; livestock and crop reference; workers and roles; equipment and facilities; inputs and suppliers; reporting and costing; compliance; devices and offline. A smallholder records the first activity before completing enterprise configuration.

## 11. Reports
Owner, crop, livestock, poultry and financial dashboards, filterable by farm, legal entity, season, crop, livestock type, production unit, field and date range, with PDF and Excel export where permissions allow. Mobile and web must use identical calculations.
