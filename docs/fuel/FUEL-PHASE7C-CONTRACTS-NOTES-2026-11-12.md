# Fuel Phase 7c: supply contracts, B06b (patch 0802)

## Decision applied
Contracts **group and report only**. A contract holds no prices. It refers to the customer's existing price entries in the price list (B07), so price approvals, margin checks and the single price master are unchanged. Nothing here creates, changes, approves or ends a price.

## Implemented
- **Customers > Contracts.** A contract has a number (unique per company, ignoring case), title, customer account, start and end date, optional fuel type, optional committed litres and terms text.
- **Status** from the dates and cancellation: Not started, Active, Expired, Cancelled. Days left while active ("Ends today" on the last day).
- **Report.** Litres delivered to the customer from the start date to today (or the end date once passed), of the contract's fuel type when it names one, against the commitment: percent used and litres remaining. No commitment means just litres delivered. Nothing before the start date counts. Other customers' and other fuels' deliveries are ignored.
- **Prices.** Link an existing price of the same customer account (and the same fuel type when the contract names one); unlink. The linked prices are shown read-only with their approval state and whether live. A standard price, another customer's price or another fuel cannot be linked.
- **Cancel** needs a reason and ends editing; the record stays. Edit changes the title, dates, fuel, commitment and terms; the account and number are fixed.

## Preserved
Price lists, price approvals, orders, invoicing and customer accounts are unchanged; nothing reads contracts yet (orders and invoices do not consult them).

## Files
- DB: V681 `fuel_contracts`, `fuel_contract_prices` (end >= start, commitment > 0, cancelled rows carry reason and time, unique number per company). No new permission.
- Java: FuelContract, FuelContractPrice, two repositories, ContractDtos, FuelContractService, FuelContractController, FuelDivisionRules ("contracts" with wholesale and distribution).
- Web: ContractsTab, contractOps, menu "Contracts" under Customers, FuelPage case.

## API
/api/v1/fuel/contracts: GET list and GET /{id} (FUEL_READ); POST, PUT /{id}, POST /{id}/cancel, PUT and DELETE /{id}/prices/{priceId} (FUEL_MANAGE, FUEL_CUSTOMERS_MANAGE or FUEL_PRICING_MANAGE). Module guard on each; tenant from context.

## Tests
- Java: FuelContractServiceTest (9): create, refusals, report against commitment with other customers and fuels excluded, no fuel and no commitment, status by date, cancel rules, link and its refusals, unlink touches only the link, edit. Parse-checked only; not run (you run Maven).
- Web: ContractsTab (6), contractOps (4). tsc clean.

## Gaps and risks
- Terms are text only: no document upload yet (the evidence file service could be reused).
- Orders and invoices do not check a contract (no price override, no volume enforcement). That is deliberate for now: contracts report.
- Litres are counted from delivered loads by customer; a customer with several accounts or sites is one customer, so one contract counts all their deliveries of that fuel.
- No renewal, no expiry notice, no volume-tier pricing (decided against).
- Unlinking a price after it was linked leaves no history of the link.

## Next
Expiry reminder to administrators; terms document upload; contract column on the order screen.
