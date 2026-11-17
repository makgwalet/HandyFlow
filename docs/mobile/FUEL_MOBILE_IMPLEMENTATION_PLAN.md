# HandyFlow Fuel Mobile: analysis and implementation plan

Date: 2026-11-17. Branch `fuel-stage0`. Documentation only: nothing was built or changed in `HandyFlow-Apps/` or the backend.
Inputs: the "Mobile App Design & Product Plan" and the "Drivers and receivers" recommendation you attached, the 2026-11-12 mobile documents in this folder, and a fresh read of the code.
Legend: `[Verified]` seen in the repository today. `[Proposed]` my recommendation. `[Requires approval]` needs your decision. `[Deferred]` valid, later. `[Not checked]` I did not open it.

## 1. Verdict
The plan is sound and matches the November 12 mobile documents on every major point (one app, role-based, offline-first, backend authoritative, Distribution first). It adds four things the earlier documents did not cover, and each needs backend work before the app can deliver it:

1. **Third-party and contracted drivers** (own, contracted, transport company, subcontracted). The backend only knows drivers as HR employees. A transport company's driver has no way in today.
2. **Receiver confirmation without an app** (secure POD link). Nothing like it exists.
3. **Truck-stop QR booking validation** and **driver registration at the truck stop** in the app. No QR/validation exists.
4. **One app now, a separate Driver app later** from the same codebase. This is an architecture rule, cheap to follow now, expensive to retrofit.

Everything else in the plan is either built on the server already or is app work that sits on a built endpoint. Section 3 shows which is which.

## 2. What exists today
| Area | State | Evidence |
|---|---|---|
| Driver API | Built. `GET /api/v1/fuel/driver/loads`, `POST .../{id}/dispatch`, `.../complete`, `.../failed-attempt`, evidence (data and multipart file) | `FuelTransportController`, `FuelDistributionController` `[Verified]` |
| Driver permission | `FUEL_DRIVER` (also `FUEL_MANAGE`, `FUEL_TRANSPORT_MANAGE`) | `FuelTransportController` `[Verified]` |
| Driver identity | A driver is an HR employee (`driverEmployeeId`, `driverName` on the delivery; site approvals keyed on `employeeId`) | `FuelDelivery`, `FuelSiteDriverApproval` `[Verified]`. How `myLoads` maps the signed-in user to the employee `[Not checked]` |
| Compartments | Tanker, compartments, per-delivery compartment records, compartment cleaning | `FuelTanker`, `FuelTankerCompartment`, `FuelDeliveryCompartment`, `FuelCompartmentCleaning` `[Verified]` |
| Eligibility (site, tanker, driver) | Built as server checks when a load is allocated, with an override permission | `FuelEligibilityService` `[Verified]`. A live "arrival check" call for the app `[Not checked]` |
| Idempotency | `clientRequestId` on complete, exceptions, evidence, returns. **Not** on dispense, dip, receive, shift close, stays | DTO search `[Verified]`; same finding as November 12 (B5) |
| Customer portal | Exists: register, login, accounts, sites, orders, deliveries, order requests. No PODs, invoices or statements in it | `FuelPortalAuthController`, `FuelPortalDataController` `[Verified]` |
| Secure POD link / receiver | None | no token model in Fuel `[Verified]` |
| Truck stop | Stays, bays, tabs, statements, Fleet links (0862 to 0864). No QR validation, no parking reservation check-in by QR | `[Verified]` |
| Push | Guard has a push-token API and service. Nothing for Fuel users | `GuardPushTokenController` `[Verified]` |
| Auth for native | Refresh token is cookie-only; Guard has its own device and PIN login | `AuthController.refresh` `[Verified]` |
| Mobile code | `handyflow-guard` is on Expo 58 / RN 0.88 / React 19.3 with outbox, SQLite store, secure device store, push, camera, location, i18n, tests. `handyflow-fuel` is an untouched SDK 52 template. `handyfarm-sync-alerts` holds a mocked Fuel UI prototype (about 60 screens) | folder reads `[Verified]` |

Reading: the **foundation already exists in the Guard app**. The Fuel app should start from that foundation, not from the SDK 52 template or the prototype's plumbing. The prototype is a source of screen layouts only. (The Guard app is parked, but its code is the reusable base.)

## 3. Plan versus reality, by division
Status: **Ready** = endpoint exists, app work only. **Gap** = backend work first.

### 3.1 Distribution and Driver Mode (first release)
| Plan item | Status | Note |
|---|---|---|
| Assigned deliveries, tanker, trailer, route | Ready | `driver/loads`; trailer as a field `[Not checked]` |
| Pre-trip inspection | Gap, by design | Belongs to Fleet (`InspectionController`). Driver Mode should open the Fleet inspection and show its status on the load, not copy it `[Proposed]` |
| Loading instructions, compartments, authorisation | Ready for display | Compartment records exist. Loading authorisation status shown from eligibility result |
| Loading confirmation (meter, seals, evidence) | Partly | Compartment readings and evidence exist. Seal number capture `[Not checked]` |
| Route navigation, milestones | Ready for points | Only evidence points carry GPS. No milestone event stream |
| Continuous GPS tracking | Gap `[Deferred]` | Needs a location-ping endpoint and a retention rule. First release: foreground points only |
| Site eligibility at arrival | Gap | Needs a read-only "may this driver and tanker offload here now" call that returns the same rules as allocation |
| Offloading readings, POD, signature, photos | Ready | `complete`, evidence file upload |
| Shortage, rejected delivery, failed attempt, incident | Ready | `failed-attempt`, delivery exceptions. Incident to Fleet accident report for vehicle incidents |
| Offline capture and replay | Ready on the server for delivery paths | Complete, exceptions, evidence, returns are idempotent |
| Emergency and control-room contact | Gap | Needs a tenant setting for the number |

### 3.2 Wholesale and Supply
Mostly Ready: requisitions, purchase orders and approvals, receiving, suppliers, stock lookup, contract and price lookup exist on the web API. Gaps: scanning of delivery references (no reference-scan endpoint) and idempotency on receive (B5). `[Proposed]` ship approvals, receiving and lookup only, as the plan says.

### 3.3 Retail and Forecourt
Shifts, pump readings, dips, cash control, variances and the POS import path exist. Gaps: idempotency on dispense, dip, receive, shift close (B5) and an operator PIN for shared devices (B6). The plan's rule that mobile must not become a second sales ledger is already how Fuel works: sales come from the pump/POS import. `[Verified]` (0842 onward)

### 3.4 Truck Stop
Stays, bays, tabs, statements, Fleet links exist. Gaps: **QR booking validation**, driver registration capture at check-in (an existing stay is created by staff, not by the driver), and idempotency on check-in/out.

### 3.5 Shared capabilities
| Capability | Status |
|---|---|
| Sign-in, session | Gap: B1 to B3 (body refresh token for native, device registry, remote revoke) |
| Push | Gap: B4 |
| Access summary for menu | Gap: B9 (`GET /me/access`: modules, divisions, permissions, sites) |
| Min app version | Gap: B7 |
| Audit (actor, device, time) | Partly: server audit exists; device id arrives with B3 |
| Sync status for the user | Gap: a "my pending/rejected submissions" view needs either client-only state or a server list |

## 4. People and modes
One app, with the experience chosen by the signed-in person's permissions and divisions.
| Person | Mode | How they sign in | Status |
|---|---|---|---|
| Own driver | Driver Mode | Staff account with `FUEL_DRIVER` | Server ready |
| Contracted driver (individual) | Restricted Driver Mode | Staff-type account, restricted permissions | Gap, section 5 |
| Third-party transport company driver | Restricted Driver Mode | Account linked to the transport company | Gap, section 5 |
| Dispatch, Depot, Attendant, Site, Truck stop staff, Fuel Manager | Workspace by permission | Staff account | Ready after B1 to B3, B9 |
| Customer receiver | Secure POD link (no install) | Single-use link | Gap, section 6 |
| Commercial customer | Customer portal (web) | Portal login | Exists; add PODs, invoices, statements |
| Large receiver | Receiver app | Portal login | `[Deferred]` |

Architecture rule so a Driver app can be split out later `[Proposed]`: put the driver feature in its own package (`packages/fuel-driver`), with navigation, screens, queue payloads and API calls inside it. The app shell only chooses which packages to mount. A separate store binary is then a new thin shell that mounts one package. No driver code may import from the manager, depot or forecourt packages.

## 5. Third-party drivers (design)
Problem: the server treats a driver as an HR employee. An outside company's driver is not on the tenant's payroll and should not be.
`[Proposed]`
- New **transport company** record (name, registration, contact, status, contract dates) and **external driver** record (name, ID number or licence number, phone, company, status) in Fuel, not in HR. No duplicate master data: an own employee who drives keeps using the HR employee; the load stores either `driverEmployeeId` or `externalDriverId`.
- Each external driver gets an individual sign-in linked to one transport company, with the single permission `FUEL_DRIVER_EXTERNAL` (new) which allows only: my assigned loads, dispatch, complete, failed attempt, evidence upload, and the delivery documents on those loads.
- Visibility is enforced on the server by query, not by hiding screens: an external driver's load list is `assigned to me` only. No prices, no other customers, no supplier or financial data, no other drivers. This must be tested with an explicit negative test per endpoint.
- Eligibility applies equally: the licence, induction and site approval records used for employees must also work for external drivers (`FuelSiteDriverApproval` is keyed on `employeeId` today and needs an external-driver key).
- The tenant decides what an external driver may see through a setting (documents shown, whether the customer contact phone is visible).
- Own tankers versus a carrier's tankers: the carrier's tanker needs the same tanker, compartment and cleaning records, owned by the transport company. `[Requires approval]` whether external carriers' tankers are registered in the tenant's tanker list.

## 6. Receiver confirmation (design)
`[Proposed]`, default method (Option A): **secure POD link**.
1. When the driver reaches the delivery and offloading readings are captured, the driver taps "Get receiver confirmation". The server creates a **confirmation request** for that delivery with a random single-use token (stored hashed, short expiry, tenant and delivery bound).
2. The driver shows a QR code or sends the link by SMS or WhatsApp. The receiver opens a small web page (no install, no login) showing the delivery: product, compartments, litres, readings, site. They enter their name, confirm or dispute the quantity, and sign.
3. The server records the confirmation as evidence on the delivery (name, signature, time, IP, user agent, the token id), and marks the request used. Disputed quantities become a delivery exception for review; they do not change stock.
4. If the receiver cannot use a phone, the driver captures the same name and signature on the driver's device (this already works through evidence upload). The driver's device path stays the fallback and the only path that works offline.
5. The backend remains the authority: the POD is valid only when the server accepts it. The app never shows "approved" from local state.
Safeguards: tokens are single use and expire; the link shows nothing outside that one delivery; rate limits; no customer prices or account data; the link page uses no tenant staff session.
Option B adds PODs, invoices and statements to the existing portal data controller. Option C is `[Deferred]` until a large customer asks.

## 7. Offline rules (carried over and tightened)
- Capture offline (queued, with client id, device id, capture time): inspection answers, readings, photos, signatures, incident reports, milestones, tank dips, delivery completion.
- Online only: new authorisations, eligibility decisions, overrides, credit, price, financial approval, final stock ownership.
- A driver must not be able to **start offloading** when the last known eligibility check is stale or negative. Rule `[Proposed]`: the arrival check must have succeeded online within a tenant-configured window (default 12 hours); otherwise the app requires a fresh online check, or a supervisor's online release. This is how "the device being offline must not bypass safety rules" is honoured without blocking all offline work.
- Each queued item has: client id, tenant, user, device id, capture time, server receipt time, status (pending, sending, accepted, rejected, conflict), retry count, evidence references.
- The queue is a durable SQLite store with secrets in SecureStore. Zustand holds UI state only. (The Guard app's `outbox.ts` and `sqliteStore.ts` are the starting point.)
- Replays use `clientRequestId`. Server rejects a replay of an already accepted id with the original result (this is already how complete and evidence behave).
- Rejected items stay visible with the server's reason and can be corrected and resubmitted; they are never dropped silently.

## 8. Backend change register for Fuel Mobile
Carried from `MOBILE_ARCHITECTURE.md` (B1 to B9, none built) plus new items. All `[Requires approval]`.
| # | Change | Why | Size |
|---|---|---|---|
| B1 | Native refresh token in the response body when the client says it is mobile | Cookie flow does not work in native apps | M |
| B2 | Re-check tenant status on refresh | Suspended tenant keeps access | S |
| B3 | Device registry: register, list, revoke; refresh tokens bound to a device | Lost phone | M |
| B4 | Push tokens and a push channel in notifications | Alerts, approvals, delayed deliveries | M |
| B5 | `clientRequestId` on dispense, dip, receive, shift close, stay check-in/out | Safe offline replay | M |
| B6 | Operator PIN for shared forecourt devices | Accountability | M |
| B7 | Minimum app version endpoint | Force upgrades | S |
| B9 | `GET /me/access` (modules, divisions, permissions, sites) | Menu and mode choice | S |
| N1 | Transport company and external driver records; `externalDriverId` on loads; `FUEL_DRIVER_EXTERNAL`; external key on site driver approvals | Section 5 | L |
| N2 | Driver-scoped read rules and negative tests for external drivers | Data leakage | M |
| N3 | Arrival eligibility check (read-only, same rules as allocation) with a recorded result and time | Section 7 | M |
| N4 | Confirmation request and public confirmation page endpoints, hashed single-use token, evidence record | Section 6 | L |
| N5 | Portal data: PODs, invoices, statements | Option B | M |
| N6 | Truck-stop QR: booking QR payload, validate and check in on scan | Truck-stop plan | M |
| N7 | Driver location ping endpoint with retention setting | Continuous GPS `[Deferred]` | M |
| N8 | Tenant setting for control-room/emergency contact | Driver home screen | S |
| N9 | "My submissions" server list (accepted, rejected, with reasons) | Sync centre | S |

## 9. Roadmap
Each phase ends with something a driver or attendant can really use, and a stated acceptance test.
**Phase 0, platform (backend B1 to B3, B7, B9; app shell).** Create `HandyFlow-Apps/handyflow-fuel` fresh from the Guard foundation (Expo 58). Sign in, choose tenant, show access summary, handle 401/402/403, queue and replay one idempotent write, build a preview binary. Acceptance: a test driver signs in on a phone, sees only Driver Mode, and a revoked device is refused on the next refresh.
**Phase 1, Driver Mode for own drivers (no backend beyond Phase 0 plus N3, N8, N9).** My loads, load detail, start trip, arrival and eligibility check, offloading readings, photos, signature, POD, failed attempt, returned litres view, sync centre, emergency contact. Acceptance: a full delivery done in airplane mode replays once, with no duplicates, when the phone is back online; an ineligible tanker cannot start offloading.
**Phase 2, receiver link and portal (N4, N5).** Secure POD link, driver fallback signature, disputes, portal PODs and invoices. Acceptance: a receiver without the app confirms from their phone; a reused or expired link is refused.
**Phase 3, third-party drivers (N1, N2).** Transport companies, external drivers, restricted mode. Acceptance: an external driver cannot read any load, price or customer that is not theirs (negative tests pass for every driver endpoint).
**Phase 4, Depot and Wholesale (B4, B5).** Goods receiving, stock lookup, PO approvals, push.
**Phase 5, Retail and Forecourt (B5, B6).** Shift, readings, dips, cash-up, supervisor approvals.
**Phase 6, Truck Stop (N6).** QR validation, check-in/out, bookings, tabs and till.
**Phase 7, enterprise.** Multi-site dashboards, telematics, device management, continuous GPS (N7).
Phases 4 to 6 can swap order by customer demand.

## 10. Risks
- Safety rules versus offline: mitigated by the arrival-check window (section 7). It needs a business decision on the window.
- External driver data leakage: the biggest risk. Mitigate by server-side scoping and mandatory negative tests, not UI hiding.
- Unverified Java: the backend since patch 0819 has never been compiled in this environment. Mobile depends on it, so compile and run it before any mobile test against it.
- Scope: do not port all 60 prototype screens. Build the Driver Mode set first.
- Two identity modes (staff and HR employee portal) plus the portal and receiver links make four ways in. Keep each narrow and separately tested.

## 11. Decisions I need
1. Approve the backend change register (section 8), in particular N1 (transport company and external driver as new Fuel records) and N4 (public confirmation link).
2. Are carriers' tankers registered in the tenant's tanker list, or only the carrier's driver and a vehicle registration?
3. Arrival eligibility freshness window (default 12 hours) and who can release a stale one.
4. Confirmation link delivery: QR on the driver's phone only, or also SMS/WhatsApp? (SMS has a provider and cost.)
5. Does a disputed quantity block the driver from finishing, or does the delivery complete with an exception?
6. Start the Fuel app from the Guard foundation (my recommendation), with the SDK 52 template and the prototype retired.
7. Confirm the Guard app stays parked while this is built.
