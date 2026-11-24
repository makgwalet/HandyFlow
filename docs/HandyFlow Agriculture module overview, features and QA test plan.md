# HandyFlow Agriculture module: overview, features and QA test plan

Nov 23, 2026 · @Thabang

## 1. What the module is

The Agriculture module is HandyFlow's farm-management add-on: it lets a farming business record its animals, crops, feed, stock and costs in one place, and shows whether each farm is making money and staying compliant.

It is built for farm owners and managers (who need profit and compliance answers), farm workers (who record daily events such as feeding, weights, health treatments and births), and bookkeepers (who need costs and sale documents). It runs inside a HandyFlow tenant, so each business only ever sees its own farms.

The problems it solves:

- **Scattered records.** Animal, health, feed and crop data live in one system instead of notebooks and spreadsheets.
- **Compliance risk.** Medicine withdrawal periods are tracked, so an animal still inside its withdrawal window is flagged before it is sold or slaughtered.
- **Hidden costs.** Feed, health, input and other costs roll up into per-farm and per-enterprise profitability.
- **Slow onboarding.** CSV import and a getting-started checklist get an existing herd and stock list into the system quickly.
- **Accountability.** Records show who added and who last changed them.

## 2. Core concepts

Everything in the module hangs off a **farm**. These are the terms QA and users will meet.

| Term | Meaning |
| --- | --- |
| Farm | A physical or business unit belonging to a tenant. All other records belong to a farm. |
| Production area | A named part of a farm (paddock, pen, field, shed) where animals or crops are kept. |
| Species | The kind of livestock (cattle, sheep, goats, pigs, poultry and so on). Drives starter breed and feed lists. |
| Animal | An individually tracked animal, identified by a tag number (or a temporary tag until a permanent one is fitted). |
| Group | A batch of animals managed together (a flock or pen) where individual tracking is not needed. |
| Health event | A treatment, vaccination or vet visit. Medicines carry a withdrawal period. |
| Withdrawal period | Days after treatment during which the animal must not be sold or slaughtered. |
| Breeding record | A mating or birth. Recording a birth can create the offspring as new animals. |
| Feed record | What was fed, to which animal or group, in what quantity, at what cost. |
| Inventory item and stock movement | Items held in store (feed, medicine, inputs) and every receipt, issue or adjustment against them. |
| Crop cycle | One growing season of one crop in a production area, from planting to harvest. |
| Scouting record | A field check on a crop cycle (pests, disease, growth) with an optional follow-up date. |
| Harvest record | Quantity and quality harvested from a crop cycle. |
| Input application | Fertiliser, chemical or seed applied to a crop cycle. |
| Sale document | A record of a sale of animals or produce, checked against withdrawal periods. |
| Audit stamp | Who created a record and who last changed it, with times. |

## 3. Features by area

The module covers eleven areas. Each one is listed with what a user can do, so QA can map every line to a test in section 6.

### Farms, areas and setup

- Create and edit farms, production areas and species.
- A getting-started checklist on the farm page shows what is still to do (areas, animals, stock) and links to the right screen.

### Livestock

- Register animals with tag, species, breed, sex, birth date, area and status; edit and retire them.
- Manage groups and move animals or groups between areas (movements are logged).
- Record weights and mortality; weight history is kept per animal.
- Temporary tags: an animal can carry a temporary tag and be re-tagged later; the tag history is kept and a reminder is raised for animals still on a temporary tag.
- Breed fields suggest a starter list for the species plus every breed already used on that farm.

### Health and compliance

- Record treatments, vaccinations and vet visits; events can be edited afterwards.
- Each medicine sets a withdrawal period; the system calculates the date the animal is clear.
- Compliance checks flag animals inside a withdrawal period, and sale documents are checked against them.

### Breeding

- Record matings and births. A birth can create the offspring as new animals (with temporary tags) in the same step.

### Feed and stock

- Record feed given to animals or groups, with quantity and cost.
- Feed stock items can set a pack size in kg per bag, so bags and kilograms convert correctly.
- Stock movements (receipt, issue, adjustment) update the inventory; the inventory tab shows current levels.
- Feed names suggest a starter list plus the farm's own entries.

### Crops

- Open and close crop cycles per production area.
- Record scouting (with follow-up dates), harvests and input applications against a cycle.
- Overdue scouting follow-ups feed the calendar and notifications.

### Costs and profitability

- Costs from feed, health, inputs and other spend roll up to cost per animal, per group and per enterprise.
- A dashboard shows profitability and trends over time for each farm.

### Sales

- Create sale documents for animals or produce, with a withdrawal check before the sale is recorded.

### Farm calendar

- One calendar lists upcoming and overdue items: withdrawal end dates, scouting follow-ups, temporary-tag reminders.

### Imports

- **Animals CSV**: dry-run first, up to 500 rows, duplicate tags are skipped, errors are reported per row.
- **Stock items CSV**: dry-run first, duplicate names are skipped, an opening-stock receipt is created for each item.

### Audit trail

- Every main record shows "added by / changed by" with times. Add-only logs (weights, mortality, movements, feed, scouting, harvest, input applications) show who added them.

## 4. Access, tenancy and background jobs

Two permissions control the module, and a tenant must have the module switched on.

| Control | Effect |
| --- | --- |
| Feature guard (`agriculture`) | If the tenant does not have the module enabled, every Agriculture endpoint is refused. |
| `AGRICULTURE_READ` | View farms, animals, crops, stock, costs, calendar and audit lines. |
| `AGRICULTURE_MANAGE` | Create, edit and delete records, run imports, record sales. Includes read. |
| Tenancy | Every query is filtered by the caller's tenant. A user can never see or change another tenant's farm data. |
| Farm scope | Today a user with the permission sees all farms of the tenant. Per-farm access is not built yet (see section 7). |

Error behaviour QA should expect: bad input returns 400, a rule conflict (for example a duplicate or an invalid state change) returns 409, a missing record returns 404, a missing permission returns 403.

Background jobs and notifications run across tenants and only for farms that are active:

- overdue scouting follow-ups are found and raised as reminders;
- animals still on a temporary tag are reminded;
- withdrawal end dates appear on the calendar.

## 5. Technical map

The module is a Spring Boot 3.4 Modulith package on the backend and a set of React screens in `handyflow-web`. Requests flow from a screen, through a controller, a service and a repository to the database.

### Backend layers

| Layer | Role |
| --- | --- |
| `api/Ag*Controller` | REST endpoints under `/api/v1/agriculture`; check permission and the `agriculture` feature guard; read the tenant from the request context. |
| `application/internal/Ag*Service` | Business flow and transactions. |
| `domain/rules` | Pure rule classes (withdrawal dates, import validation, audit text, suggestions); easy to unit-test. |
| `domain/model` and `domain/repository` | JPA entities and JPQL queries, always filtered by tenant. |
| `dto` | Request and response records. |

### Migrations

| Migration | Change |
| --- | --- |
| V888 | Births and offspring, temporary tag history. |
| V889 | Pack size (kg per bag) on stock items. |
| V890 | Audit columns on six main tables. |
| V891 | Audit columns on seven log tables. |

### Notable endpoints

| Endpoint | Purpose |
| --- | --- |
| `POST /farms/{farmId}/inventory-items/import` | Stock CSV import (supports dry run). |
| `GET /farms/{farmId}/suggestions` | Breed and feed suggestions for type-ahead fields. |
| `GET /audit/{type}/{id}` | Added-by and changed-by for one record. |
| `GET /audit/{type}?ids=` | The same for up to 200 records at once. |

All paths are relative to `/api/v1/agriculture`. The animals import and the other record endpoints follow the same controller pattern.

### Frontend

The farm page (`AgFarmDetail`) hosts tabs for animals, groups, health events, feed records, inventory, crops, costs and the calendar. Supporting pieces: `AgFarmSetup` (checklist), `AgStockImport` (and the animals import), `AgAuditLine` (added-by line), and the `agLookups`, `agSetup.logic`, `agAudit.logic` and `agStockImport.logic` helpers with unit tests.

## 6. How QA will test it

QA tests the module in three layers: automated tests run by developers, manual functional tests in a browser, and a regression and non-functional pass before sign-off. Manual browser testing is the biggest gap today, because the screens have so far only been checked by type checks, lint and unit tests.

### Environment and preparation

1. Back up the database, then start the backend so Flyway applies V888 to V891. Confirm the log shows no migration errors.
2. Start the web app and sign in as a tenant with the `agriculture` feature enabled.
3. Create three test users: read-only (`AGRICULTURE_READ`), manager (`AGRICULTURE_MANAGE`), and one with neither.
4. Create a second tenant with its own farm, to prove tenant isolation.
5. Prepare two CSV files: a valid animals file with 10 rows and a mixed file with a duplicate tag, a bad date and a missing tag; and a stock file with a duplicate name.

### Test data

- Farm A: two production areas, one cattle group, five cattle, three sheep, one with a temporary tag.
- Stock: two feed items (one with a 25 kg bag size), one medicine with a 14-day withdrawal period.
- One crop cycle with one scouting record due yesterday.

### Functional test cases

| ID | Area | Test | Expected result |
| --- | --- | --- | --- |
| AG-01 | Setup | Open a new farm with no data. | The getting-started checklist lists areas, animals and stock as to do and links to each screen. |
| AG-02 | Setup | Add an area and an animal. | The matching checklist items turn done. |
| AG-03 | Livestock | Create an animal with a duplicate tag in the same farm. | The save is refused with a clear message. |
| AG-04 | Livestock | Move an animal to another area. | A movement record is added with the user and time. |
| AG-05 | Livestock | Record a weight and a mortality. | Both appear in history; mortality changes the animal status. |
| AG-06 | Tags | Give an animal a temporary tag, then replace it with a permanent one. | Tag history shows both tags; the temporary-tag reminder disappears. |
| AG-07 | Suggestions | Type in the breed field for a cattle animal. | Starter breeds and breeds already used on the farm are offered. |
| AG-08 | Health | Record a treatment with the 14-day medicine. | The clear date is the treatment date plus 14 days. |
| AG-09 | Health | Edit the health event and change the date. | The clear date is recalculated. |
| AG-10 | Compliance | Check the treated animal on day 5. | It is flagged as inside withdrawal. |
| AG-11 | Sales | Create a sale document that includes the flagged animal. | The sale is blocked or warned according to the compliance rule. |
| AG-12 | Breeding | Record a birth with two offspring. | Two new animals are created with temporary tags and linked to the dam. |
| AG-13 | Feed | Record feed in bags for the 25 kg item. | Quantity converts to kg correctly, cost is calculated, stock reduces. |
| AG-14 | Stock | Receive, issue and adjust stock. | Each movement is logged and the level in the inventory tab is correct. |
| AG-15 | Crops | Open a cycle, add scouting, an input application and a harvest. | All three show on the cycle; closing the cycle locks further changes as designed. |
| AG-16 | Calendar | Open the farm calendar. | The overdue scouting follow-up, withdrawal end and temporary-tag reminder are listed. |
| AG-17 | Costs | Add feed, health and input costs, then open profitability. | Totals per farm and enterprise equal the sum of the records. |
| AG-18 | Import | Dry-run the 10-row animals file. | Preview shows 10 valid rows and nothing is saved. |
| AG-19 | Import | Run the mixed animals file. | Valid rows are saved, the duplicate is skipped, bad rows are reported by row number. |
| AG-20 | Import | Upload a file with 501 rows. | The file is rejected with a row-limit message. |
| AG-21 | Import | Import the stock file. | Items are created with an opening-stock receipt; the duplicate name is skipped. |
| AG-22 | Audit | Create then edit an animal as two different users. | The record shows added by user 1 and changed by user 2, with times. |
| AG-23 | Audit | Open movement, feed and scouting lists. | Each row shows "by" the user who added it. |

### Security and tenancy test cases

| ID | Test | Expected result |
| --- | --- | --- |
| AG-S1 | Call any Agriculture endpoint as the user with neither permission. | 403, no data. |
| AG-S2 | Try to create, edit, import or delete as the read-only user. | 403; buttons are hidden or disabled in the screen. |
| AG-S3 | Request a farm or animal id that belongs to the second tenant. | 404 or no data, never the other tenant's record. |
| AG-S4 | Use a tenant without the `agriculture` feature. | Module hidden in the app; endpoints refused. |
| AG-S5 | Ask the audit endpoint for more than 200 ids at once. | The request is refused or limited. |

### Regression and non-functional checks

- **Regression:** run the full backend suite, including the bean-name test, and the web unit tests and lint; confirm other HandyFlow modules still start and their screens load.
- **Migrations:** apply V888 to V891 on a copy of production data and on an empty database; both must succeed, and existing rows must show no audit stamp rather than an error.
- **Performance:** list screens with 1,000 animals and 5,000 feed records load in a few seconds; the audit batch call returns for a full page of 200 ids.
- **Data integrity:** after imports and births, counts in the dashboard match counts in the lists.
- **Browser and device:** latest Chrome and Edge on desktop, plus one phone-size window for the daily recording screens.
- **Usability:** a farm worker can record a feed, a weight and a treatment without help.

### Exit criteria and sign-off

| Gate | Criteria |
| --- | --- |
| Entry | Build passes, migrations apply cleanly, test users and data exist. |
| Exit | All AG and AG-S cases pass; no open critical or high defects; regression suite green. |
| Sign-off | QA lead and product owner sign off, with the list of known limits from section 7 accepted. |

## 7. Known limits, unverified items and open work

The backend compiles and starts on the development machine, but not everything has been proven yet.

**Not yet verified**

- Screens have only been checked by type checks, lint and unit tests, not in a browser. Section 6 is the first real browser pass.
- The Mockito-based controller tests and the rules and wiring tests have not yet run on the development machine; they were blocked by a Java agent attach problem and then low memory (Windows paging file), both environment issues.
- Exact 403 and 404 behaviour in AG-S1 to AG-S3, and that manage includes read, should be confirmed against a running system.
- The starter breed and feed lists have not been reviewed by a livestock expert.

**Known limits**

- No farm-level access: any user with the permission sees every farm in the tenant.
- The setup checklist guides but is not a full step-by-step wizard.
- Add-only logs show who added a record but no "last changed" (they cannot be edited).
- Scouting, harvest and input-application screens do not yet show the "added by" line (only the movement and feed lists do).
- The database reports schema version 999 above 891, so out-of-order migration warnings are expected and harmless.

**Open work, if wanted**

- Farm-level roles (a `farm_access` table, migration V892; users with no rows keep seeing all farms).
- A full setup wizard.
- Review of the starter breed and feed lists.
- "Added by" display on the scouting, harvest and input screens.
