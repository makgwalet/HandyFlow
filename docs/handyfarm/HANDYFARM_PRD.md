# HandyFarm: product requirements

Status: consolidated 2026-11-18 from three planning papers (see `README.md` for sources and how they were reconciled).
Status tags: `[Approved]` product owner decision, `[Proposed]` recommendation not yet approved, `[Verified]` seen in the repository, `[Deferred]` valid but later, `[Requires approval]` needs a decision before work.

## 1. What HandyFarm is

HandyFarm is an independent agricultural business management application for South African farms, from smallholders to large multi-farm businesses, supporting crops and livestock from the first release. `[Approved]`

- Product name: **HandyFarm**. Backend module: **Agriculture** (`platform/.../agriculture`). The earlier working name "HandyFlow Agri" is retired. `[Approved]`
- Product family: HandyFlow / Blue Ndlovu. Customer-facing product is HandyFarm; Agriculture stays the internal module name.
- Independence rule: a tenant must not need Supply Chain, Fleet, HR, Warehousing or any other HandyFlow module to run HandyFarm's core agricultural workflows. Integrations enhance HandyFarm; they are never prerequisites. `[Approved]`
- Tagline used in planning: *Plan. Plant. Produce. Protect. Profit.*

### Value proposition
Operational visibility and better agricultural decisions, not paperwork digitisation. A farmer should be able to answer: what land do I have, what is planted where, what is due today, which inputs went where, which animals need treatment, what was harvested, what did it cost, what is the margin, and what risks are open.

### Target customers
Smallholders and emerging farmers; commercial crop farmers; cattle, sheep and goat farmers; poultry and pig farmers; mixed farms; horticulture, orchards and vineyards; cooperatives; contract growers; estates and agribusiness groups.

## 2. Approved product decisions

| # | Area | Decision | Implication |
|---|---|---|---|
| 1 | Launch market | Mixed farms | Crops and livestock from the first foundation |
| 2 | Animal records | Individual and group/batch | Tagged animals and managed groups; an animal moves between the two without losing history |
| 3 | Offline | Full offline capture, server validation | Field work continues offline; the server stays authoritative |
| 4 | Worker experience | Dedicated Field Worker Mode | Simplified, restricted mode inside HandyFarm (not a separate app) |
| 5 | Devices | Android, iOS and tablets | Responsive, device-aware layouts |
| 6 | Costing | Production costs and gross margin by enterprise | Allocation across crops, livestock and other enterprises |
| 7 | Weather and sensors | Manual capture first | Manual recording plus integration-ready interfaces |
| 8 | Procurement | Basic purchasing plus optional Supply Chain integration | HandyFarm useful alone; advanced procurement when Supply Chain is subscribed |

## 3. Functional scope

HandyFarm is one app with a shared farm foundation and specialised engines. Divisions are configurable per tenant and are not the same thing as user permissions. The first onboarding choice must never lock a tenant into one agricultural model.

### 3.1 Farm hierarchy `[Approved]`
`Tenant → Legal Entity → Farm → Production Unit → Enterprise → Production Cycle`

A production unit is a field, orchard block, paddock, livestock camp, poultry house, fish pond or greenhouse. Crop fields, livestock groups and aquaculture facilities have different needs, so they are not forced into one shape. A smallholder must be able to create one farm, a few fields and a small herd without a long wizard; larger businesses can configure several legal entities, farms, user scopes and reporting structures. Setup is save-and-resume.

### 3.2 Crop production
Seasons, planning (crop, variety, field, planned dates, hectares, expected yield and cost), activities (ploughing, planting, fertiliser, spraying, irrigation, scouting, harvest), soil and crop protection records, rotations with configurable agronomic warnings, harvest grading and traceability. Pesticide registrations and use instructions are controlled reference data. The app must never invent application rates or recommend unsafe chemical combinations. Satellite and NDVI are `[Deferred]` decision support, never a substitute for field inspection.

### 3.3 Livestock (individual and group/batch) `[Approved]`
Preserve: animal identity and tag history; group membership and movement; health, vaccination and treatment; withdrawal periods; births, deaths, purchases, sales; breeding and offspring. Species must be configurable (cattle, sheep, goats, pigs, poultry, rabbits, fish; more later). Individual ID via QR or barcode first; RFID is a later hardware capability. The system supports health records but does not diagnose or prescribe.

### 3.4 Poultry and aquaculture
Own workflows, not generic livestock records (batches, mortality, feed conversion, egg production, house management; ponds, stocking, water quality, harvest). `[Deferred]` to a later release, but the architecture must not exclude them.

### 3.5 Field Worker Mode `[Approved]`
My Tasks, Today's Work, assigned farm and production unit, instructions and checklists, crop and livestock capture, photo and GPS evidence, issue and incident reporting, offline sync status, completed-work history. Workers do not automatically see financials, supplier prices, payroll, management reports or other workers' private information. The backend enforces this; hiding a screen is not security.

### 3.6 Farm resources, inputs and basic purchasing `[Approved]`
Input catalogue, stock by storage location with batch and expiry, issue and consumption against a field, unit or group, equipment usage, irrigation and water records, storage and harvest. Basic purchasing without Supply Chain: farm suppliers, input catalogue, purchase requests, quotations and price comparison, purchase orders, goods received, delivery discrepancies, purchase history, documents, and cost allocation to the enterprise. With Supply Chain subscribed, advanced procurement takes over without duplicate suppliers, purchase orders, goods receipts or stock movements.

### 3.7 Production costing and gross margin `[Approved]`
Distinguish six amounts: actual costs (confirmed), allocated costs, estimated costs, forecast costs, actual revenue (confirmed sales), expected revenue (planned or forecast).
`Gross margin = production revenue − direct production costs.` Never label it net profit; overheads, tax and financing belong to Accounting.
Unit measures: cost per hectare and per tonne (crops), per animal and per kg (livestock), per bird and per dozen eggs (poultry), per kg harvested (fish), per hectare and tonne (orchards).
When a production cycle is closed, snapshot the rates and allocation inputs used so later HR pay or Fleet cost-rate changes do not rewrite historical margin.

### 3.8 Weather and observations `[Approved]`
First release is manual: rainfall (mm), temperature, wind, humidity, soil moisture, water levels, irrigation hours, general observations, weather incidents. Record date, time, location, author, source and unit. APIs, stations, soil and tank sensors, irrigation controllers and satellite imagery come later behind an integration layer, not a hard-coded manufacturer. Any recommendation is advisory and shows source quality and freshness.

### 3.9 Compliance, biosecurity and risk
Configurable compliance register (not one universal checklist) separating legal obligations, voluntary standards, buyer requirements and internal policy; biosecurity (restricted areas, visitors, quarantine, disease reporting, cleaning); incidents with severity, location, affected assets, evidence, owner and closure approval. Recording a permit does not imply the operation is legally authorised.

### 3.10 Machinery, workforce and commercial links
Agricultural activity context lives in HandyFarm; employment and payroll stay in HR; equipment master and maintenance in Fleet; fuel transactions in Fuel; customers in CRM; invoices and receivables in Invoicing; ledger in Accounting. HandyFarm builds no duplicate payroll, fleet, fuel, invoicing or ledger. See `HANDYFARM_ARCHITECTURE.md` section 5.

### 3.11 AI and decision support `[Deferred]`
Advisory only: summarise records, flag anomalies, compare cycles, show source records and uncertainty. Never confirm a diagnosis, prescribe medication, invent pesticide instructions or make irreversible decisions; respect tenant and user permissions.

### 3.12 South African localisation
ZAR, configurable VAT, metric units, local date and number formats, regional season calendars (do not assume one planting season), low-connectivity operation, local address conventions, more languages as the product grows.

## 4. Personas
Farm Owner, Farm Manager, Production Manager, Agronomist, Veterinarian, Livestock Manager, Farm Supervisor, Field Worker, Irrigation Operator, Machinery Operator, Storekeeper, Harvest Manager, Procurement Officer, Accountant, Compliance Officer, Agricultural Consultant (controlled, assigned access). Users can hold several roles. A veterinarian may need animal health records without payroll or full financials.

## 5. Mobile versus web
The web portal owns configuration; mobile owns execution. Mobile has full capture of activities, treatments and harvests (permission-controlled), view and drill-down reports, limited farm and worker setup. Permissions, approval policies, integrations and crop or livestock reference configuration stay web-only.

## 6. First release definition `[Approved]`
A mixed-farm operational product, not a recording app.

Must be operational:
- [ ] Farm, field, paddock and enterprise setup
- [ ] Crop production records and activities
- [ ] Individual animal and group/batch records
- [ ] Livestock health, movement and production records
- [ ] Dedicated Field Worker Mode
- [ ] Offline capture and server validation
- [ ] Photo, GPS and evidence capture
- [ ] Basic input catalogue and consumption
- [ ] Basic farm purchasing
- [ ] Basic production costs and enterprise gross-margin reporting
- [ ] Android, iOS and tablet-compatible experience

Can follow: advanced poultry and aquaculture, automated weather and sensors, satellite and NDVI, AI-assisted observations, advanced enterprise forecasting, automated machinery and irrigation integrations.

## 7. Acceptance scenarios (production-ready gate)
Mixed farm setup; multiple farms with respected boundaries; individual animal full lifecycle; batch management without losing individual history; offline crop activity synchronised once and audited; offline livestock treatment with server-side conflict and withdrawal validation; Field Worker Mode limited to assigned and authorised operations; basic purchasing without Supply Chain; Supply Chain integration without duplicate records; costs allocated to the right cycle; closed-cycle gross margin stays stable; core workflows on Android, iOS and tablet; no cross-tenant or unauthorised farm access. Detailed scenarios: `HANDYFARM_ROADMAP_AND_TEST_PLAN.md`.
