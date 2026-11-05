# Module catalogue and reconciliation (41 business modules plus Free Core)

Sources: your catalogue (41), the 45 backend packages, Flyway migrations (catalogue seeds), `requireModule` call sites, the 22 Oct and 29 Oct audits.
**Legend.** `Key` = the string passed to `FeatureGuard.requireModule`. `Cat.` = row found in `module_catalogue` by parsing migrations (price in ZAR per month; 22 Oct audit values used where my parse missed the insert). `UNKNOWN` = not verifiable without the live database or a module audit. Status words come from the 22 and 29 Oct audits and were **not re-verified** except where marked "now".

## 1. Reconciliation table

| # | Business name | Package / app code | Guard key in code | Catalogue (migrations) | Backend entitlement check | Notes (evidence) |
|---|---|---|---|---|---|---|
| 1 | Agriculture | `agriculture` | agriculture | R399 | yes | checks `supplychain` too |
| 2 | Auditor | `auditor` | none | **no row** | none | external auditor portal; imports internalaudit, controls |
| 3 | Internal Audit | `internalaudit` | internal-audit | **R0** | yes | key is hyphenated, package is not |
| 4 | Bookkeeping | `bookkeeping` | bookkeeping | R329 (22 Oct) | yes | client portal |
| 5 | Booking Agency | `bookingagency` | bookingagency | **no row** | yes | 22 Oct: 16 endpoints with malformed `hasAuthority` (unverified); not sellable |
| 6 | Clinic | `clinic` | clinic (interceptor) | R749 | yes (interceptor) | most mature; special-category health data |
| 7 | Debt Collection | `debtcollection` | debtcollection | **R0** | yes | own SMS sender |
| 8 | Collection Agency | `collectionsagency` | collectionsagency | **R0** | yes | provider model, client portal |
| 9 | Compliance Services | `complianceservices` | complianceservices | R499 (22 Oct) | yes | |
| 10 | Compliance Tender | `compliancetender` | compliancetender | R299 (22 Oct) | yes | |
| 11 | Creative | `creative` | none | R349 | none | public proof links |
| 12 | Desk | `desk` | none | R249 | none | public ticket portal |
| 13 | Events | `events` | none | R299 | none (report mode, patch 0608) | also "free" in Essential plan JSON |
| 14 | Earthmoving | `earthmoving` | earthmoving | R499 | yes | |
| 15 | Facilities | `facilities` | facilities | R249 (22 Oct) | yes | backend only (22 Oct) |
| 16 | Facilities Management | `facilitiesmanagement` | facilitiesmanagement | R349 (22 Oct) | yes | provider model; client portal built since |
| 17 | Insurance | `insurance` | insurance | **R0** | yes | backend only (22 Oct) |
| 18 | Insurance Broker | `insurancebrokerage` | insurancebrokerage | **R0** | yes | |
| 19 | Legal Compliance | `legalcompliance` | legalcompliance | **R0** | yes | imports debtcollection, contracting |
| 20 | Property | `property` | property | R399 | yes | tenant portal |
| 21 | Recruiter | `recruiter` | none | R349 | none | internal recruiting; public careers site |
| 22 | Recruitment Agency | `recruitmentagency` | recruitmentagency | **no row** | yes | provider model |
| 23 | Training | `training` | training | R249 | yes | internal learning and development |
| 24 | Training Provider | `trainingprovider` | trainingprovider | R449 | yes | provider model |
| 25 | Warehousing | `warehousing` | warehousing | **R0** | yes | 3PL |
| 26 | Projects | `projects` | none | **no row** | none | permissions prefixed `PM_` |
| 27 | POS & Stock | `pos` | none | R399 | none | |
| 28 | Accounting | `accounting` | none | R599 | none (report mode, 0608) | 17 modules import it; ledger owner |
| 29 | Accountant | `accountant` | none | **no row** | none | practice management; 29 Oct: Not Ready |
| 30 | Accounts Payable | `ap` | none | **no row** | none | uses approvals engine |
| 31 | Expenses | `expenses` | none | R199 | none (report mode, 0608) | |
| 32 | HR & Payroll | `hr` | none | R499 | none (report mode, 0608) | SARS PAYE; statutory content must never be tiered |
| 33 | Payroll Bureau | `payrollbureau` | payrollbureau | **no row** | yes | 29 Oct: Not Ready, withdraw from sale |
| 34 | Tasks | `tasks` | none | R149 | none | |
| 35 | Supply Chain | `supplychain` | supplychain | **key mismatch**: catalogue `supply_chain` R599 | none on its own controllers | Agriculture checks the other spelling |
| 36 | Bookings | `bookings` | none | R299 | none (report mode, 0608) | |
| 37 | Contracting | `contracting` | none | R349 | none (report mode, 0608) | public signing path separate |
| 38 | Fleet | `fleet` | fleet | R299 | yes | telematics path in report mode (0608) |
| 39 | Fuel | `fuel` | fuel | R349 | yes | imports fleet |
| 40 | Security | `security` | security | R349 | partial | owns the only API-key system (platform coupling) |
| 41 | Legal Practice | `legalpractice` | legalpractice | R399 | yes | backend only (22 Oct); portal added since |

**Not in your 41:** **CRM**, **Invoicing**, **Catalogue** (Free Core, section 3); **Marketing** (`marketing`, R299 in the catalogue, no module check; **it exists in code but not in your list. Add it or retire it: D-08**); platform packages (`admin`, `billing`, `identity`, `shared`, `config`, `notifications`, `approvals`, `audittrail`, `documents`, `evidence`, `capture`, `controls`, `businessreadiness`, `tenderpricing`, `secureaccess`), which are shared services, not products.

**Gaps to close for a sellable catalogue:** rows missing for 9 modules (Auditor, Booking Agency, Recruitment Agency, Projects, Accountant, AP, Payroll Bureau, Catalogue and CRM if they are to be priced) and 7 rows priced R0. Whether rows exist in the live table: UNKNOWN until the query in `04` section 4 is run.

## 2. Similar names: separate or merge?

Evidence: package dependencies and `package-info` text. Customer interviews were not available, so "Target customer" below is inferred.

| Pair | Different users? | Recommendation | Reason |
|---|---|---|---|
| Auditor vs Internal Audit | Yes: external firm auditing a client vs a company's own audit function | **Keep separate** | Different actors and permissions; `auditor` consumes `internalaudit` data through a portal; price separately (Auditor per engagement/client, Internal Audit per seat) |
| Debt Collection vs Collection Agency | Yes: a company recovering its own debts vs an agency collecting for clients | **Keep separate** | Provider model needs trust accounting and client portals |
| Recruiter vs Recruitment Agency | Yes (confirmed with you on 4 Nov: internal hiring has no clients) | **Keep separate** | Agency has clients and placements; internal does not |
| Training vs Training Provider | Yes | **Keep separate** | Internal L&D vs a provider selling courses with billing |
| Insurance vs Insurance Broker | Yes (own policies vs a brokerage book) | **Keep separate** | |
| Facilities vs Facilities Management | Yes (own premises vs managing others') | **Keep separate** | Provider model, SLA and client billing |
| Accountant vs Accounting | Yes: firm managing clients vs a business's own ledger | **Keep separate** | Accountant depends on client ledgers; priced per active client |
| Booking Agency vs Bookings | Yes (agency books for external clients) | **Keep separate** | Same provider pattern as above |
| HR & Payroll vs Payroll Bureau | Yes | **Keep separate**, but 29 Oct: rebuild the Bureau on the HR payroll engine before selling |

No merger recommended. The commercial risk is confusing names in the marketplace; use "for my own business" and "for my clients" groupings.

## 3. Free Core feature boundaries (CRM, Invoicing, Catalogue)

"Exists" below is only what is evidenced in repository documents; **a feature-by-feature code check is still needed (D-12)**. Tiers: **Core** = Free Core; **Paid** = paid upgrade; **Higher** = Growth or above; **Metered**; **Ent** = Enterprise.

### CRM
| Capability | Class | Exists (evidence) |
|---|---|---|
| Contacts, companies, customer history, notes | Core | yes (CRM-BUILD-STATUS) |
| Leads, opportunities, one basic pipeline, tasks and reminders | Core | yes |
| Basic search and filters, basic dashboard | Core | yes |
| Customer export | Core, **permission-controlled** (CRM-01 P0: any employee can export) | yes |
| Multiple pipelines, custom fields, segmentation | Paid | custom fields, segments exist (routes seen) |
| Workflows, scoring, routing, follow-up automation | Paid / Higher | workflows, scoring, routing exist (routes seen) |
| Bulk operations, advanced import | Paid | UNKNOWN |
| Reports and scheduled reports, forecasting | Paid / Higher | reports, report schedules exist |
| Webhooks / integrations | Higher | webhooks exist |
| Hierarchy, branch and territory scoping | Ent | hierarchy exists; no data scoping (29 Oct) |

### Invoicing
| Capability | Class | Exists |
|---|---|---|
| Quotations, invoices, numbering, PDF, VAT, payment status, email delivery | Core, **metered** (issued documents) | yes |
| Credit notes and corrections | Core, **unmetered**, never locked | yes |
| Customer statements | Core | UNKNOWN |
| Recurring invoices and variable-hours / retainers | Paid | yes (Billing screens doc) |
| Quote conversion to invoice | Core (uses one quote and one invoice) | yes; **INV-01 P0: converted invoices are not posted to the ledger** |
| Multi-currency, payment gateway, customer portal, reminders and collections, advanced approvals, multiple number sequences, branding | Paid / Higher | mostly UNKNOWN or missing; gateway missing (22 Oct F3) |
| Advanced financial analytics | Higher | partial |

### Catalogue
| Capability | Class | Exists |
|---|---|---|
| Products and services, categories, descriptions, SKU, price, images, active flag, search, use in quotes | Core | yes (MODULE-AUDIT-catalogue, 197 front-end tests) |
| Price lists, variants, bundles, barcodes, bulk import/export, approvals, channels | Paid | partly (approvals and channels mentioned 29 Oct) |
| API access | Higher / Ent | UNKNOWN |
| Stock and warehouse | **Not Catalogue** (POS, Warehousing, Supply Chain own it) | n/a |

Rule: **tenant isolation, audit trails, data export of own records, statutory content and credit notes are never locked behind a tier.**

## 4. Full feature classification for the other 38 modules

I did not produce a per-feature matrix for all 41 modules. Doing so honestly needs a feature inventory from each module's controllers and screens, which is a larger piece of work (one module audit each). What I can state now:

* **Tier allocation principle (recommended):** Core features (everything needed to run the workflow once) in the module's base price; automation, advanced reporting, integrations and multi-entity/branch features in Growth and above; negotiated items (SSO, dedicated support, custom retention, API volume) in Enterprise; metered items (employees, clients, seats, tenders, documents) priced by band.
* **Already tiered by evidence (29 Oct, not approved):** HR (per employee), Accountant and Compliance Services (per active client), Internal Audit (seats), Compliance Tender (per active tender), Finance bundle (Accounting, AP, Invoicing paid tier, Expenses).
* **Proposed next step:** generate the matrix per module from the permission list and route list (a script, then a review by a person), starting with the wave order in the 22 Oct audit. Marked as D-12 in the decision register.

## 5. Shared services consumed (from the 22 Oct dependency map)

Billing (28 importers), notifications (30), accounting (17), evidence (17), identity (17), approvals (8), hr (9), crm (7), invoicing (4). Hard dependencies from code: `fuel`→`fleet`; `pos`→`catalogue`, `accounting`; `invoicing`→`catalogue`, `crm`; `supplychain`→`ap`; `legalcompliance`→`debtcollection`, `contracting`; `agriculture`→`hr`, `supplychain`, `fleet`, `fuel`, `invoicing`; `earthmoving`→`crm`, `contracting`, `projects`, `invoicing`. Invoicing events post to Accounting **regardless of an Accounting subscription**; that is an integration, not a hard dependency, and should stay so. Examples in your brief that the code does **not** show: Earthmoving→Fleet, Fleet→Accounting, Legal Practice→Debt Collection (22 Oct). The remaining pairs in your list are UNKNOWN until each is checked.

## Target customers (from the June 2026 Platform Analysis, section 7.2)

These are the segments the platform was positioned for, useful for bundle design and for deciding which modules are core to a segment. They are marketing positions, not validated demand.

| Segment | Modules the analysis pairs |
|---|---|
| Earthmoving contractor | Fleet, Fuel, Earthmoving, Invoicing |
| Security company (PSiRA) | Security, Fleet, Invoicing, HR |
| Accounting firm | Accountant |
| Property manager | Property, Contracts, Invoicing |
| Creative agency | Creative, Contracts, Tasks, Marketing |
| Growing retail store | POS, Catalogue, Expenses, Invoicing |
| GP practice | Clinic, Bookings, Invoicing |
| Mining operation | Fuel, Earthmoving, Security |

Note that the Platform Analysis names Marketing as part of the Creative bundle, which supports a decision to keep it (D-08) even though it is not in the 41-module brief.
