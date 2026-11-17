# Fuel: contract volume pricing (D2) — notes, 2026-11-17

Status: written, not compiled or run (Java). Web tests and tsc run.

## The rule
- Prices stay in the one price list and its approval (B07). A contract holds no prices of its own.
- A contract can link several prices of one fuel and give each a "from litres" step (0 = the starting price).
- When a customer has exactly one ACTIVE contract for a fuel that has a ladder (a link with from litres above 0), a new price lookup picks the live, approved linked price with the highest step at or below the litres already delivered on that contract.
- A pending, rejected, ended or not-yet-started step never applies, so the next lower step does.
- If nothing qualifies, or the customer has more than one active ladder contract for that fuel, nothing is guessed: ordinary price resolution runs (account price, then standard, with per-order minimum litres).
- The price is stored on the order when it is created. Later deliveries do not re-price it.
- While a ladder is in use, a step's own per-order minimum litres is ignored; the contract volume decides.
- Two prices of one fuel cannot share the same non-zero step on a contract. Linking again changes the step.

## Changes
- V709: `fuel_contract_prices.from_litres` (default 0, check >= 0). Existing links keep 0, so existing contracts behave as before.
- `FuelContractService.linkPrice` takes an optional step; `view` marks the step in effect today.
- `FuelContractTierPricing` resolves the tier; `FuelPricingService.resolve` asks it first when an account is given.
- `PUT /api/v1/fuel/contracts/{id}/prices/{priceId}` accepts an optional body `{ "fromLitres": n }`.
- Web: "From litres" box when linking, step text and "in effect now" on linked prices.

## Decisions for you
- Is cumulative contract volume the right measure, or should it be per calendar month?
- Should a customer with two ladder contracts for one fuel be blocked from creating the second?
