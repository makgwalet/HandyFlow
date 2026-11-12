# Mobile module assessment

Basis: 63 backend packages under `platform/src/main/java/za/co/handyflow/platform/` and 58 web page folders under `handyflow-web/src/pages/`. All recommendations are `[Proposed]`; dedicated apps need `[Requires approval]`.

Categories: **A** dedicated app, **B** app subject to tenant business configuration, **C** shared employee/customer mobile experience, **D** mobile-responsive web only, **E** future opportunity.

Names in the brief that do not match the repository: "Products" is `catalogue`; "POS & Stock" is `pos`; "Debt Collection Agency" is `collectionsagency`; "Booking Agency" is `bookingagency`. **FuneralDesk has no backend package** `[N/A]`. Two overlapping packages exist, `facilities` and `facilitiesmanagement` (about 150 endpoints each); a consolidation question for the owner, not a mobile one.

## 1. Recommendation matrix
Counts are mapped endpoints in the module (a rough maturity signal).

| Module | Cat | Priority | Main users | Key mobile workflows | Special capabilities | Reason |
|---|---|---|---|---|---|---|
| fuel (244) | **A** | P1 | Drivers, pump attendants, depot clerks, supervisors | See section 3 | Camera, GPS, signature, offline, push | Field work away from desks, offline needed, backend driver API exists |
| fleet (169) | **A** | P2 | Drivers, technicians | Inspections, trips, accident and breakdown reports, documents, fuel logs | Camera, GPS, offline | Controllers for inspection, accident, driver, fuel log, work order already exist |
| earthmoving (118) | **A** | P2 | Plant operators, site supervisors | Equipment checklists and inspections, meter and hour readings, deploy checks, incidents | Camera, GPS, offline | Remote sites; controllers for checklist, inspection, meter, deploy check exist |
| warehousing (220) | **A** | P2 | Pickers, receivers, stock counters | Receiving, put-away, pick/pack, cycle count, scan | Barcode/QR, offline reads | `WhseScan`, `WhseReceiving`, `WhseCycleCount`, `WhseTask` exist |
| security (303) | **A** | P1/P2 | Guards, supervisors | Patrol checkpoints, incidents, duress, shift, location | GPS, NFC/QR, push, device binding | Guard login, device activation and replacement already built; a "Shield" app is referenced in code but **not in the repo** |
| agriculture (172) | **B** | P2 | Farm workers, managers | Record weight, treatment, mortality, planting, scouting, harvest | Camera, GPS, offline | App already started; poultry/aquaculture not built in backend |
| clinic (221) | **B** | P3 | Practitioners | Schedule, patient summary, notes, consent | Secure storage, biometric | Health data (POPIA special personal information); needs privacy review before any build |
| projects (182) | **B** | P3 | Site managers, field staff | Tasks, site photos, issues, RFIs, timesheets | Camera, offline capture | `Field`, `Issues`, `Rfi`, `Task` controllers exist |
| facilitiesmanagement (145) / facilities (152) | **B** | P3 | Technicians | Work orders, PPM, asset scan, photos | QR scan, camera, offline | `FmField`, `FmTechnician`, `FmWorkOrder` exist |
| property (28) | **E** | P4 | Inspectors, tenants | Inspections, tenant requests | Camera | Small module; tenant portal exists on web |
| events (112) | **B** | P3 | Door staff, organisers | Ticket check-in scan | QR scan, offline | Only the check-in workflow is mobile |
| pos (26) | **B** | P3 | Cashiers, stock takers | Stock take, quick sale | Barcode, offline | Full POS is a tablet/web matter; stocktake is the mobile win |
| supplychain (40) | **C** | P3 | Buyers, receivers | Goods receipt, approvals | Barcode | Fold into warehousing scanner and employee app |
| training / trainingprovider (48/61) | **B** | P3 | Assessors, learners | Attendance, assessment capture | Signature, camera | Subject to provider business model |
| hr (273) | **C** | P1 | All employees, managers | Leave, payslips, clock-in/out, announcements, approvals | Geofence, biometric, push | `HrEmployeePortalController` (separate portal login) and `AttendanceController` exist; one employee app, two identity modes (see architecture section 5) |
| expenses (20) | **C** | P1 | Employees | Receipt capture, claims | Camera, OCR later | Classic mobile task |
| approvals (4) / tasks (36) | **C** | P1 | Managers | Approve, reject, my tasks | Push | Cross-module inbox in the employee app |
| desk (17) | **C** | P2 | Support agents, technicians | My tickets, updates | Push | Employee app tab |
| crm (117) | **C** | P2 | Sales reps | Contacts, notes, visits | GPS, camera | Employee app tab; a sales-rep app is `[Deferred]` |
| bookings (23) / bookingagency (36) | **C** | P3 | Staff | Today's bookings | Push | Employee app tab |
| invoicing (52) | **C** | P3 | Owners | View, send, approve | Push | Owner dashboard glance |
| recruiter (93) / recruitmentagency (38) | **D** | P4 | Recruiters | n/a | n/a | Desk-based |
| accounting (121), accountant (98), bookkeeping (70), auditor (48), internalaudit (118), payrollbureau (40), ap (44) | **D** | n/a | Finance professionals | n/a | n/a | Desk-based; approvals surface through the employee app |
| internalaudit (118) | **E** | P4 | Field auditors | Evidence capture | Camera, offline | Only if auditors work on site |
| collectionsagency (56) / debtcollection (140) | **E** | P4 | Field collectors | Visit notes | GPS | Demand unproven |
| legalpractice (77), legalcompliance (43), complianceservices (44), compliancetender (63), tenderpricing, contracting (27), catalogue (76), marketing (17), creative (18), insurance (9), insurancebrokerage (30), businessreadiness | **D** | n/a | Office staff | n/a | n/a | Desk-based |
| admin (89), billing (12), audittrail, controls, usage, capture, evidence, documents, secureaccess, notifications, identity | **D** / platform | n/a | Administrators | n/a | n/a | Platform and back-office; notifications/identity are enablers |
| Customer portals (`fuel-portal`, `fm-portal`, `property-portal`, `warehousing-portal`, `client portal` etc. on web) | **E** | P4 | Customers, suppliers | Status, documents, requests | Push | Portals exist on web; native apps wait for demand |

## 2. Groupings
- **Build first:** Fuel field app, then the employee app (`HandyFlow Work`).
- **Can wait:** Clinic, Projects, FM, Events, POS.
- **Shared infrastructure:** all apps share auth, tenant, API client, offline queue, design tokens (see `MOBILE_TECHNOLOGY_STANDARDS.md`).
- **Web-only:** all finance, legal, compliance and recruitment modules.
- **Customer-facing candidates:** Fuel portal, FM portal, property portal, client portal (Phase 4).
- **Field-worker apps:** Fuel, Fleet, Earthmoving, Warehousing, Security, Agriculture.

## 3. Fuel mobile application (deep assessment)
### 3.1 Users and divisions
The backend defines four divisions in `FuelDivision`: `WHOLESALE_SUPPLY`, `DISTRIBUTION`, `RETAIL_FORECOURT`, `TRUCK_STOP`. The brief's "Trucking" is not a division; trucking sits under `DISTRIBUTION` (tankers, drivers, loads). `GET /api/v1/fuel/operating-profile` returns the tenant's configured divisions, so the app can show only the experiences the tenant has `[Proposed]`. The backend already blocks calls to divisions a tenant lacks (`FuelDivisionRules`), so the app must treat a refusal as normal, never as a crash.

| Persona | Division | Experience |
|---|---|---|
| Delivery driver | Distribution | My loads, start trip, arrive, deliver, proof, failed attempt, returned litres |
| Pump attendant / operator | Retail forecourt | Shift, dispense, dip, receive |
| Depot clerk | Wholesale | Receive from supplier, dip, transfer, load |
| Truck-stop attendant | Truck stop | Reservations, overnight stays check-in/out |
| Fuel manager | All | Approvals, variances, alerts, dashboards |

### 3.2 Workflow to backend map (endpoints read from controllers)
| Flow | Endpoint(s) | Idempotency | Verdict |
|---|---|---|---|
| My loads | `GET /fuel/driver/loads` | n/a | Ready |
| Start trip | `POST /fuel/driver/loads/{id}/dispatch` | "already moved on" check only | Ready; no client id |
| Complete delivery | `POST /fuel/driver/loads/{id}/complete` | `clientRequestId` | Ready |
| Photo/signature | `POST /fuel/driver/loads/{id}/evidence/file` (multipart), `GET /fuel/driver/evidence/{id}/file` | `clientRequestId` | Ready |
| Failed delivery | `POST /fuel/driver/loads/{id}/failed-attempt` | `clientRequestId` | Ready |
| Returned litres | `GET/POST /fuel/deliveries/{id}/returns` | `clientRequestId` | Ready (office permission; driver variant not built) |
| GPS point | Evidence record with `latitude`, `longitude`, `accuracyM` via `POST /fuel/driver/loads/{id}/evidence` (no separate location endpoint) | `clientRequestId` | Ready; continuous tracking is not supported, only points |
| Dispense to vehicle | `POST /fuel/tanks/{id}/dispatch` | **none in DTO** (`DispatchFuelRequest`) | **Gap** |
| Dip reading | `POST /fuel/tanks/{id}/dip-readings` | **none** | **Gap** |
| Receive fuel | `POST /fuel/tanks/{id}/receive` | **none** | **Gap** |
| Shifts | `/fuel/forecourt/shifts` open, close, review, adjustments | none | Gap for close |
| Variances | `/fuel/variances`, investigate, close | n/a | Ready (manager) |
| Approvals | `/fuel/adjustment-requests/{id}/approve|reject` | n/a | Ready (manager) |
| Truck-stop | `/fuel/truck-stop/reservations`, `/stays` check-in/out | none | Gap for check-in/out |
| Alerts | `/api/v1/notifications` | n/a | In-app only; no push |

The generic `Idempotency-Key` header filter (`config/IdempotencyKeyFilter.java`) is opt-in per request and can cover the gaps without DTO changes `[Proposed]`.

### 3.3 Capabilities from the brief
| Candidate | Backend today | Mobile need | Call |
|---|---|---|---|
| Delivery execution, POD, signature, photos | Built | Offline capture, queue | In scope, P1 |
| GPS and route tracking | Location capture, routes, route cost | Background location policy | P1 foreground, `[Deferred]` background |
| Tank readings / dips | Built | Offline, numeric entry | P1 |
| Fuel issue confirmations | Dispatch endpoint, no idempotency | Operator PIN, vehicle search | P1 after backend fix |
| Vehicle inspections | Belongs to **Fleet** (`InspectionController`) | Do not duplicate in Fuel | Link to Fleet app |
| Driver management | Web | Not a mobile job | Web only |
| Incident reporting | Delivery exceptions; Fleet accident reports | Camera | Reuse |
| Fuel stock movements | Ledger; deducts at delivery completion, not loading | Read-only on mobile | Web |
| Notifications | In-app only | Push | Needs backend |

### 3.4 Permissions needed (existing, from earlier Fuel stages)
`FUEL_READ`, `FUEL_TRANSPORT_MANAGE` (driver and returns), `FUEL_TRUCKSTOP_MANAGE`, `FUEL_MANAGE`. Navigation hides what the permission list lacks; the backend stays authoritative.

### 3.5 Screens (carry over from the prototype, re-verified)
Login, Today, My loads, Load detail, Arrival, Proof (photo and signature), Failed attempt, Dispense wizard, Dip wizard, Receive wizard, Shift open/close, Sync centre, Alerts, Truck-stop stays. The prototype already has these (`handyfarm-sync-alerts/.../screens`).

### 3.6 Offline needs
Level 3 (offline workflow execution) for deliveries, dips, dispenses, receipts, shifts. See `MOBILE_OFFLINE_STRATEGY.md`.

### 3.7 Security
Lost device, shared devices at forecourts, operator PIN, screenshot blocking on signature, remote revoke. See `MOBILE_SECURITY_STANDARDS.md`.

## 4. Other deep assessments (scope candidates, not approved features)
- **Fleet driver app:** inspections from templates, trip log, breakdown and accident reports, document expiry, vehicle allocation, fuel log. Shares drivers and vehicles with Fuel; the two should use one driver identity `[Proposed]`.
- **Earthmoving operator app:** pre-start checklist, meter and hours, deploy checks, damage and incident photos, site attendance, offline sites.
- **Clinic:** read-mostly practitioner view first; notes and consent after a privacy impact assessment. No patient data cached unencrypted. Prescription workflows `[Deferred]` pending legal review.
- **HR employee app:** leave, payslips, attendance (`/hr/attendance`), documents, approvals, profile. Payslip access must re-authenticate.
- **Projects:** task execution, site photos, timesheets, issues, RFIs, risk, approvals, client progress via existing portal endpoints.
- **Warehousing scanner:** receive, put-away, pick, count; hardware scanners and Android keyboard-wedge support to be validated.
- **Security guard app:** patrols, checkpoints, duress, shift, location; backend ready.
- **Agriculture:** extend the existing app after the standards migration.

## 5. Re-baseline of the Fuel prototype's `BACKEND_GAPS.md` (verified against controllers)
| Prototype gap | Status now |
|---|---|
| Delivery lifecycle endpoints | **Closed**: driver loads, dispatch, complete, cancel |
| Receiver signature upload | **Closed**: evidence file endpoints |
| Problem report | **Closed** for failed delivery (`failed-attempt`, exceptions) |
| Dip endpoint | **Closed** (`POST /tanks/{id}/dip-readings`); variance policy thresholds not verified |
| Receipt endpoint | **Closed**; idempotency still open |
| Shift lifecycle | **Closed**: forecourt shifts |
| Variance workflow | **Closed**: investigate and close |
| Approvals resource | **Closed**: adjustment-requests |
| Vehicle search | Fleet vehicles exist; search endpoint to verify |
| Supplier master | **Closed**: `/fuel/suppliers` |
| GPS fields | Delivery location exists; dispense GPS not verified |
| OTP login / roles | **Open**: no phone/OTP; roles are tenant permissions |
| Device registration | **Open** (only for guards) |
| Operator PIN | **Open** (guards have PIN; fuel does not) |
| Per-vehicle allowance, policy engine | **Open** |
| Dispatch/dip/receive idempotency | **Open** |
| Sync status API | **Open** |
