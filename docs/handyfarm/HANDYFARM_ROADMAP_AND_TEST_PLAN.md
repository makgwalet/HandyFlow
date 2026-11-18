# HandyFarm: roadmap, release gates and test plan

Status tags as in `HANDYFARM_PRD.md`. The phase order is the approved one; where the deep plan differed, the approved plan won (see `README.md`).

## 1. Phases `[Approved]`

| Phase | Scope | Exit criteria |
|---|---|---|
| 0 Audit and architecture | Audit Agriculture backend, web, and the Fuel app's reusable packages; inventory, gap register, ownership map | Approved technical design, inventory, gap register, backlog. **Started**: see `HANDYFARM_CURRENT_STATE_AND_GAPS.md` |
| 1 Mobile foundation and offline engine | App identity, navigation, authentication; tenant, legal entity, farm and unit context; permissions and devices; SQLite and transactional outbox; sync, retry, idempotency, conflicts; camera, GPS, attachments, notifications; Android, iOS and tablet layout foundations | Users capture and synchronise test records offline with no duplicates and no data leakage |
| 2 Farm management and mixed-farm setup | Farms, fields, blocks, paddocks, camps, facilities; crop and livestock enterprises; seasons and cycles; reference data; dashboard and maps | One tenant configures several farms and mixed enterprises |
| 3 Crop and livestock engines | Crop lifecycle and harvest; individual and group animals; breeding, births, purchases, deaths, movement; health, vaccination, treatment and withdrawal controls; poultry and specialised foundations | Complete, auditable lifecycles |
| 4 Field Worker Mode | Assignments and task execution; activity, evidence, GPS and observation capture; offline completion; supervisor review and exceptions | A worker completes an assigned workflow without management-only data |
| 5 Farm resources and purchasing | Inputs and consumption; basic purchasing and goods receipt; equipment usage; irrigation and water; storage and harvest; optional Supply Chain integration | Inputs and resources trace to the consuming enterprise |
| 6 Costing and commercial | Allocation by cycle; labour, machinery, fuel, input costs; yield and quality; sales and customer integration; gross margin; actual, estimated, forecast comparison | A manager can explain cost and gross margin per enterprise |
| 7 Compliance, reporting, intelligence | Compliance and evidence register; biosecurity and incidents; manual weather and sensor observations; dashboards; exports | Management monitors performance, compliance and profitability |
| 8 Advanced integrations | Weather APIs, sensors, satellite, RFID and barcode hardware, advanced poultry and aquaculture, AI-assisted observations, multi-farm analytics | Integrations optional, independently deployable, never compromise core operations |
| 9 Rollout and release | Android pilot; iOS and tablet testing; low-end Android performance; offline and recovery; security and penetration testing; backup, recovery, monitoring; store preparation | Production sign-off based on test evidence |

Suggested release milestones `[Proposed]`: Internal Alpha (login, farm context, navigation), Field Alpha (crop and livestock capture, offline queue), Field Worker Beta, Costing Beta, Production 1.0 after phase 9 gates.

## 2. Release gates

| Area | Required verification |
|---|---|
| Authentication | Secure login, session renewal, logout |
| Entitlements | No access without HandyFarm subscription |
| Tenant isolation | No cross-tenant farm, animal or attachment access |
| Farm scoping | Users see only authorised farms and production units |
| Crop cycles | Full lifecycle and history |
| Animal records | Unique identity and movement history |
| Health records | Treatment integrity and withdrawal tracking |
| Poultry, aquaculture | Batch calculations, mortality reconciliation, water readings (when built) |
| Offline | No duplicate or silently overwritten activities |
| Evidence | Secure upload and attachment association |
| Workers | Assignment and supervisor verification |
| Equipment | Usage allocation and Fleet integration |
| Inventory | Consumption reconciles with stock services |
| Costing | Actual and estimated values clearly distinguished |
| Sales | Correct Invoicing integration |
| Reports | Same calculations on web and mobile |
| Security | Permissions, audit, device revocation |
| Performance | Acceptable on low-end Android |

## 3. Automated scenarios

Product-level end to end (all must pass before production):

| Scenario | Required outcome |
|---|---|
| Mixed farm setup | Crops and livestock coexist under one tenant |
| Multiple farms | Data and permissions respect farm boundaries |
| Individual animal | Identity and lifecycle history maintained |
| Batch management | Batch records managed without losing individual history |
| Offline crop activity | Captured offline, synchronised once, audited |
| Offline livestock treatment | Conflicts and withdrawal periods validated server-side |
| Field Worker Mode | Only assigned and authorised operations available |
| Procurement | Basic purchasing works without Supply Chain |
| Optional integration | Supply Chain adds capability without duplicate records |
| Production costing | Costs allocated to the correct cycle |
| Historical reporting | Closed-cycle gross margin stays stable |
| Device support | Core workflows on Android, iOS, tablet |
| Tenant isolation | No cross-tenant or unauthorised farm access |

Failure and conflict cases to automate:
1. Two workers record activities on the same field offline.
2. Two users record treatments for the same animal.
3. An animal is moved while another user holds an outdated record.
4. A worker's farm access is revoked while the device is offline.
5. A production cycle is closed while offline activities are pending.
6. A photo uploads but the activity submission fails.
7. Farm configuration changes while a device is offline.
8. A user attempts to access another legal entity's farm.
9. A harvest is recorded against a closed crop cycle.
10. An animal movement is submitted twice.
11. A treatment has an incomplete or invalid withdrawal period.
12. A completed cycle's historical costing changes because an employee's current pay rate is updated.

## 4. Test inventory today
Backend: 54 Java test files under `agriculture`. Web: pages exist; test coverage `not checked`. Mobile HandyFarm app: none found. None of the 12 failure cases above has been confirmed as automated; each needs a check against the existing 54 files before work starts.

## 5. Documents still to produce (from the plan's next milestone)
1. Requirements-to-code traceability matrix (first cut in the current-state doc).
2. Screen-by-screen mobile UX specification.
3. Offline and synchronisation design (HandyFarm-specific, built on the Fuel outbox findings).
4. Database and API change register.
5. Permission matrix.
6. Integration ownership matrix (first cut in the architecture doc).
7. Prioritised implementation tickets.
8. Test plan and release-gate evidence template.

## 6. Out of first release `[Deferred]`
Advanced poultry and aquaculture, automated weather and sensors, satellite and NDVI, AI-assisted observations, advanced enterprise forecasting, automated machinery and irrigation integrations. The architecture must keep room for them.
