# ADR-001: Agriculture's financial integration (revenue, labour, equipment, fuel)

**Status:** Accepted in principle (1 Oct 2026). Implementation is gated on the open decisions in section 4.
**Scope:** Agriculture and its relationship to Invoicing, HR, Fleet, Fuel, Facilities, Inventory/Supply Chain and Accounting.

## 1. Decisions

**Agriculture orchestrates production; it does not own a second copy of any financial domain.**

| Concern | System of record | Agriculture stores |
|---|---|---|
| Revenue | Sales / Invoicing | Allocation of sale lines to production output |
| Labour cost | HR / Payroll (pay rate) | Hours and assignment, plus a snapshot of the rate used |
| Equipment cost | Fleet | Usage against a farm activity, plus a snapshot of the cost used |
| Fuel | Fuel / Fleet | Allocation of fuel use to a field, crop cycle or group |
| Stock valuation | Inventory / Supply Chain | (see 3.7: interim exception) |
| Suppliers | Supply Chain / AP | References, later |
| Weather, NDVI | External providers | Observations and references, later |

Rules that follow:
1. **Snapshot rule.** Whenever Agriculture costs something with a rate owned elsewhere (pay, equipment, fuel), it stores the rate and the resulting
   amount at the time. A 2026 cycle stays costed at 2026 rates after a 2027 pay rise.
2. **Gross margin, not net profit.** Agriculture reports revenue minus DIRECT production costs. Overheads, finance costs, depreciation, other expenses
   and tax belong to Accounting; Agriculture does not compute net profit.
3. **No parallel ledgers.** No Agriculture sales, pay-rate, equipment-asset or fuel tables. Reference the owning module.
4. **Revenue is ex-VAT**, net of credit notes.

## 2. Build sequence

W1 production cost allocation, W2 sales/revenue, W3 HR labour, W4 Fleet/Fuel, W5 profitability engine, W6 dashboard and reports,
W7 supplier/SCM, W8 weather, W9 NDVI/satellite. W7 to W9 do not block a first profitability version.

## 3. What the code actually offers today (read from source, 1 Oct 2026)

1. **Invoicing.** `InvoicingFacade` exposes only a VAT summary and outstanding invoices: no per-line read, no search by product, no way to create a sale.
   `InvoiceLineItem` has catalogue item, description, unit, quantity, unit price, `lineTotal` (ex-VAT) and `vatAmount`, but **no source reference**.
   Credit notes attach to the **invoice**, not to a line. Currency is ZAR. Statuses include DRAFT, ISSUED, PARTIALLY_PAID, PAID.
2. **HR.** `HrEmployee` has `grossSalary`, `payFrequency` (default MONTHLY), `salaryType`, `employmentType` (default PERMANENT). **There is no hourly
   rate**: it must be derived (gross salary, pay frequency, an hours assumption). Gross salary excludes employer on-costs. `HrFacade.findEmployeeById`
   already returns `grossSalary`, so Agriculture can read it today, which also means salary data is one call away from a module whose users should
   not necessarily see it. No salary-specific permission was found in `hr/api`.
3. **Fleet.** `Vehicle` has odometer, `purchasePrice`, `dailyRate`; `Trip`, `FuelFillup`, `VehicleService` (with cost). **No engine-hours meter and no
   cost-per-hour rate.** There is **no Fleet facade**.
4. **Equipment assets** in `Facilities` are building plant (HVAC, generator, lift...) tied to a site; tractors and harvesters would be Fleet vehicles.
5. **Fuel.** `FuelDispatch` (tank to vehicle or asset) records litres, `hoursReading` and `costPerLitreAtSale`, which is the tank's weighted-average cost
   snapshot at dispense time: exactly the snapshot this ADR asks for. There is **no Fuel facade**.
6. **Agriculture today.** Direct costs already exist per animal, group and crop cycle (feed, health, inputs, seed, animal purchases) and are reported by
   the cost reports and trends. Inputs and harvests carry `laborHours` (shown, never costed). Livestock `status` becomes SOLD with **no date, price or
   buyer** (a sale event does not exist). A crop cycle's output is its harvest records. Poultry (eggs, FCR) was explicitly out of scope; a broiler batch
   can be modelled as an `AgGroup`.
7. **Stock.** Agriculture has its own inventory and stock-movement ledger (`AgInventoryItem`, `AgStockMovement`), written when no Supply Chain facade
   existed. This contradicts the ownership table above and is treated as an INTERIM exception until W7.

## 4. Decisions on the open questions

**Defaults accepted on 1 Oct 2026**: every default in brackets below is the decision. Status per work item is in section 5.

1. **Production unit.** Cost and revenue attach to: crop cycle, animal group, individual animal, enterprise. [Yes; poultry batches as groups for W1-W6;
   eggs and FCR are a later, separate increment.]
2. **Revenue link.** A user allocates invoice lines to production output (needs read access to invoice lines through `InvoicingFacade`). [Manual allocation
   first, with a catalogue-item-to-crop/species mapping as a later prefill.]
3. **Revenue recognition.** [On invoice issue, statuses ISSUED/PARTIALLY_PAID/PAID/OVERDUE; excluding DRAFT and void; credit notes netted in proportion
   to the invoice's lines.] Cash basis is the alternative.
4. **Hourly rate.** [grossSalary divided by hours implied by payFrequency, default 45 h/week (195 h/month), configurable per tenant; employer on-cost
   percentage configurable, default 0; a manual rate override, audited, for casual workers not in HR.]
5. **Salary confidentiality.** [A new `AGRICULTURE_FINANCE` permission for anything showing labour cost, margin or revenue; rates never returned by
   general Agriculture endpoints.]
6. **Equipment rate.** Fleet has no hours meter or per-hour rate. [Fleet adds an engine-hours meter and an operating rate per hour (service and repairs,
   NOT fuel and NOT depreciation) behind a new `FleetFacade`; fuel is allocated separately from dispatches so it is never counted twice.]
7. **Breeding stock.** Buying a bull is capital, not a direct cost of one batch. [Gross margin is per completed production unit; animals flagged as
   breeding stock are excluded from batch margins.]
8. **Timing.** Costs fall in one period and revenue in a later one. [Margin per production unit over its life; farm-level rollup by season, with
   unsold stock shown as unrealised and not valued.]
9. **W1 shape.** [One cost-entry table (source, source reference, date, target, quantity, rate snapshot, amount, reversal link) that W3 and W4 write
   to. The existing direct costs stay where they are; the ledger holds only the NEW categories (labour, equipment, fuel, other direct), so nothing is
   counted twice.]

## 5. Progress

- **W1 (cost allocation ledger): built.** `ag_cost_entries` (V306), `AGRICULTURE_FINANCE` permission (ADMIN by default), manual OTHER_DIRECT costs split
  across crop cycles, groups, animals and enterprises with an exact largest-remainder split, append-only with reversal, Insights > Cost ledger.
  It holds ONLY the new categories, so the existing direct costs are not counted twice. Cost reports, trends and the dashboard do not include
  ledger costs yet: they will be combined in W5 (profitability engine) in one place, to avoid double counting.
- **W2 (sales and revenue): built.** Read-only `InvoicingFacade` extension (invoice lines with status, ex-VAT totals and credit notes), `ag_sales_allocations` (V307),
  Insights > Sales. A user attributes part of an issued invoice line to a crop cycle, group, animal or enterprise. **Revenue is never stored**: it is computed live
  (ex-VAT, issued onwards only, credit notes netted in proportion across the invoice's lines, apportioned across ALL allocations of a line to the exact cent), so a
  cancelled invoice or a later credit note is reflected. Needs AGRICULTURE_FINANCE **and** INVOICE_READ (it shows invoice data). It records attribution only: it does
  NOT mark animals sold or change a head count. Agriculture's allowed module dependencies now include `invoicing`.
- **W3 (labour from HR): built.** Costs the `laborHours` already recorded on input applications and harvests into the ledger as LABOUR entries against the crop cycle
  (`source_type` HR_LABOUR, `source_ref` the work record). Hourly rate = the employee's gross salary over the ordinary hours in their pay period (WEEKLY = the week,
  FORTNIGHTLY = two weeks, MONTHLY = 52/12 weeks; the week defaults to 45 h), loaded with a tenant-set employer on-cost %, and SNAPSHOTTED into the entry. Casual workers with
  no HR record are costed at a typed-in rate. Costing is an explicit finance action, not a side effect of recording the work. `ag_finance_settings` (V308) holds the two
  settings; a partial unique index makes double-costing impossible even under a race, and reversing the ledger entry frees the work to be costed again.
  **Privacy:** Agriculture never returns a salary, only the derived rate, and only reads HR for callers who also hold HR_READ, HR_MANAGE or USER_READ (HR's own rule).
  Everyone with AGRICULTURE_FINANCE can still cost at a typed rate. Anyone with AGRICULTURE_FINANCE can see the loaded rate on a ledger entry, so grant it only to people who
  may see payroll.
- **W4 (equipment and fuel): built.** Fleet gains `engine_hours` and `operating_rate_per_hour` on `fleet_vehicles` (V309; the rate is service and repairs ONLY, never fuel or
  depreciation), a `PATCH /fleet/vehicles/{id}/equipment`, a `GET /fleet/equipment` and a read-only `FleetFacade`. Fuel gains a read-only `FuelFacade` that offers only the tenant's
  OWN dispatches (to its own vehicles or assets, never customer sales). Agriculture costs a machine's day of use (hours x the snapshotted rate, source FLEET_USAGE, sourceRef the
  vehicle) and allocates a fuel dispatch (litres x the tank's snapshotted cost per litre, source FUEL_DISPATCH, sourceRef the dispatch), each split across targets as one allocation
  group. Fuel is never in the hourly rate, so it is never counted twice. **Access:** equipment needs AGRICULTURE_FINANCE + FLEET_READ; fuel needs AGRICULTURE_FINANCE +
  FUEL_MARGIN_READ (the permission Fuel uses for cost data); FUEL_READ alone is not enough. **Integrity:** a dispatch already allocated is refused, and a unique index (V309) stops the
  same dispatch going to the same target twice. Residual race: two people allocating the same dispatch to DIFFERENT targets at the same instant could both succeed, because a split
  dispatch legitimately has several rows. Reversing the group frees the dispatch.
- **Known limit:** Fleet's `vehicle_type` has a database CHECK (BAKKIE, SEDAN, SUV, TRUCK, VAN, BUS, MINIBUS, MOTORCYCLE, OTHER), so tractors and harvesters are filed as OTHER until
  tractor and harvester types are added (a migration and a UI list; a decision for Fleet).
- **W5 (profitability engine): built.** `GET /farms/{id}/profitability` (AGRICULTURE_FINANCE + INVOICE_READ, like Sales) combines, per crop cycle, group, animal and enterprise: the
  RECORDED costs the existing reports already count (feed, health, seed, inputs, an animal's purchase price), the cost-LEDGER totals by category (labour, equipment, fuel, other
  direct; reversals netted) and the live revenue. **Nothing is counted twice** because every cost and every sale names exactly ONE target; the farm total is the exact sum of the unit
  rows, and a cost or sale on a target that has since been removed goes into one catch-all "Removed or unlisted targets" row so the totals still equal the ledger and sales. Units
  whose life is over (harvested, failed or abandoned cycle; closed group; sold, dead, culled or transferred-out animal) are COMPLETE and their margin is final; the rest are IN_PROGRESS
  and show costs and sales TO DATE, with unsold stock not valued (decision 8). An enterprise is always ongoing and shows only what was allocated to it directly (its animals', groups'
  and cycles' own margins are separate rows, so nothing is rolled up twice). **Honesty caveats:** a finished unit that would normally have been sold but has no sales attributed says
  so; a PURCHASED group warns its purchase price is not recorded anywhere (the existing cost service says the same), so its margin is overstated; the notes say how many labour entries
  are not costed yet and how many sale allocations are not counted. Unit lists and sales are capped at 1000 each and the report says when a cap is reached. Insights > Profitability.
- **Breeding stock (decision 7): built.** `ag_animals.breeding_stock` (V310, default false, so every existing animal is reported as before), `PATCH /animals/{id}/breeding-stock`
  (AGRICULTURE_MANAGE; the body value is required, a missing one is a 400), `AnimalResponse.breedingStock`, and a "Breeding stock" tick on the animal screen. The flag changes how margins are
  REPORTED only: it never edits a cost, a sale or the animal's purchase price. A flagged animal's purchase price (capital) is taken out of its recorded cost; its feed, health, ledger costs
  and any sales stay. It appears as its own row (state BREEDING_STOCK, not expected to be sold so no "no sales" warning) and in its own Breeding stock subtotal, in NEITHER the finished nor
  the running subtotal, but IN the farm totals (its running costs and sales are real), so the three subtotals still add up to the total and the total still equals the ledger plus sales.
  Judgement call: a person with AGRICULTURE_MANAGE but not AGRICULTURE_FINANCE can flip the flag and so change the reported margins; the report is read-only for them and nothing is lost.
  The existing animal Cost reports still include the purchase price (W6 decides how they treat breeding stock).
- **Not done in W5 (open):** (1) The existing Cost reports, Trends and the Dashboard still
  do not include ledger costs or revenue (W6 brings them onto this report's numbers). (3) No season filter or CSV export yet (W6). (4) Unsold harvested stock is not quantified: sales
  quantities and harvest yields may be in different units, so Agriculture does not compare them.
- **W6a (reports agree and say what they count): built.** (1) **Season filter** on Profitability (`?seasonId=`): a season report covers that season's crop cycles only, with only
  their ledger rows and sales; livestock and enterprises are not tied to a season, so they are left out and the report says so; labour not yet costed is counted for those cycles only;
  uncounted sales are not claimed by season. It is a view of the same numbers, not a second calculation. (2) **CSV export** of Profitability, client-side: plain numbers to two places
  (a loss is negative), the not-net-profit warning, caveats and notes travel in the file, a safe file name, and Export is disabled while a new season is loading so a file can never
  mix one season's label with another's figures. (3) **Cost reports** now say they are RECORDED COSTS ONLY and point to Profitability; a breeding animal is marked, with a note that its
  purchase price is in those totals but left out of margins (`AnimalCostSummaryResponse.breedingStock`). (4) **Trends** wording corrected: it said labour, equipment and revenue "are not
  recorded", which stopped being true in W2 to W4; it now says what the chart does and does not include. (5) **CSV injection guard** in the shared `toCsv`: a text cell starting with
  = + - @ (or a tab or return) is prefixed with an apostrophe (numbers are untouched), which also protects the existing cost-report export.
  **Deliberately NOT done:** ledger costs, labour or margins are NOT added to Trends, Cost reports or the Dashboard, because they are open to AGRICULTURE_READ and labour cost is
  salary-derived (decision 5); margins stay behind AGRICULTURE_FINANCE + INVOICE_READ.
- **W6b (all-farms overview and Dashboard margin card): built.** `GET /profitability/overview` (AGRICULTURE_FINANCE + INVOICE_READ, like one farm's report) returns every active farm's
  gross margin side by side. It never calculates a margin itself: each farm's figures are that farm's own whole-farm report, and the overview is their EXACT SUM (so it cannot disagree
  with a farm's Profitability screen), with percentages recomputed from the sums, never averaged (a small farm cannot drag them). Farms are listed by name; per-farm warnings (labour not
  yet costed, sales not counted, caveated units) are counted as "cautions" and listed with the farm's name, capped at 30 with a summary; at most 50 farms, and the overview says when it is
  cut off. The Dashboard shows a "Gross margin, all farms" card between the KPIs and the Trends strip ONLY to users who hold both rights; for everyone else it is absent and no request
  is made (no "access denied" noise on a page they may read). A farm with costs but no sales yet shows its loss; only a farm with neither shows "No activity yet".
- **W6 leftovers: done.** (1) **Print / PDF** on Profitability: a self-contained HTML document opened in its own window and printed (the browser's print dialog also offers "Save as
  PDF"), so it does not depend on hiding the app's menus. EVERY value a user typed (farm, season, unit and tag names, caveats, and notes that embed a season name) is HTML-escaped; a loss is
  written as the word "Loss" so a black-and-white printout reads correctly; it always covers every unit whatever the screen's filters, names the season, and is disabled while a new season
  loads. A blocked pop-up is explained. (2) **All-farms CSV** on the Dashboard card (the server's exact totals, plain numbers, notes, formula-injection guard from the shared `toCsv`).
  (3) **Drill-down:** each farm name on the Dashboard card links to `/agriculture/profitability?farm=<id>` (URL-encoded). The selected farm was ALREADY kept in the URL (`AgFarmScope` reads
  `?farm=`), so no change to it was needed; an earlier note here saying farm choice was local state was wrong.
- **W7 (suppliers): built, as REFERENCES (the ADR's "Supply Chain / AP, references").** `ag_stock_movements.supplier_id` and `ag_inventory_items.supplier_id` (V311, by id, deliberately no
  foreign key into Supply Chain's tables). A read-only `SupplierFacade` in Supply Chain exposes IDENTITY ONLY (id, name, status): the supplier record also holds bank account, contact, VAT and
  BBBEE data, and none of it crosses the boundary. **Integrity:** a receipt takes the supplier chosen for it, else the item's usual one, and either must be an ACTIVE Supply Chain supplier;
  nothing is received from a blacklisted or inactive supplier, and a usual supplier that has since been blacklisted is NOT silently used (the receipt is refused and says so). The movement
  ledger stays append-only: the supplier is given when the receipt is created (a factory overload), never changed. **Access:** the picker needs AGRICULTURE_MANAGE + SCM_READ (Supply
  Chain's own right to list suppliers); spend by supplier needs AGRICULTURE_FINANCE + SCM_READ. **Spend by supplier** (`GET /farms/{id}/purchases/by-supplier`, Insights > Purchases) is
  the cost of stock RECEIVED into Agriculture inventory (each receipt's own recorded quantity x unit cost), grouped by supplier, with a "No supplier recorded" row and an "Unknown supplier
  (removed)" row so the rows always add up to the total; it says plainly that it is NOT Supply Chain purchase orders or invoices. The receive form now lets the user enter the unit cost
  actually paid (it was always the item's current cost), which spend depends on. Existing items keep their typed supplier name; nothing is migrated or guessed.
- **DECISION (W7): the stock ledger stays in Agriculture.** The ADR called Agriculture's own inventory and stock-movement ledger an interim exception "until W7". W7 does NOT migrate it into
  Supply Chain's inventory: the existing seed, input and feed costs, the cost reports and the profitability engine are all built on it, moving it means a data migration of live stock
  history and a change of owner for stock valuation, and none of that is needed to link receipts to suppliers. It is therefore a standing, deliberate exception, revisited only if
  Supply Chain's inventory gains what farms need (batch/lot tracking, per-farm locations, withdrawal periods). Until then Agriculture records receipts and Supply Chain owns suppliers.
- **Open after W7:** purchase orders and goods receipts are not linked to Agriculture receipts (so a PO raised in Supply Chain and received in Agriculture are two records); no supplier
  performance view; no creating a supplier from Agriculture (done in Supply Chain). W8 and W9: not started.
