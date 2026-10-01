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
- W3 to W9: not started. W3 needs the hourly-rate derivation and on-cost setting; W4 needs the Fleet changes in decision 6 (hours meter, operating rate,
  `FleetFacade`) and a Fuel facade; W5 combines costs and revenue (cost reports, trends and the dashboard do not include ledger costs or revenue yet).
