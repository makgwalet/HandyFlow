# Fuel: forecourt replenishment (decision 11) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests and tsc pass. Migration V713.

## Decision 11 (closed)
Internal depot to forecourt = one stock transfer linked to the dispatch. External supply = a receipt. Inter-entity = an inter-entity transfer. Never double-count.

## How it works
- **Dispatch form:** when "Delivering to forecourt" is chosen, a new "Into forecourt tank" choice lists tanks at that forecourt holding the same fuel. Choosing one makes the run **one stock transfer** linked to the dispatch (`fuel_dispatches.transfer_id`). Leaving it empty keeps today's behaviour: the dispatch takes the fuel out and the forecourt records a receipt.
- **Stock:** the transfer moves the stock and posts the two ledger lines (transfer out, transfer in). The dispatch records the run (litres, levels, cost at the supplying tank's average, approval review) and posts no stock line of its own, so nothing counts twice. A short or reserved source tank refuses before anything is recorded.
- **Matching report:** such a run shows as received in full, "delivered by stock transfer, no receipt needed". A receipt cannot be linked to it (refused), because the fuel is already in the tank.
- **Reversal:** reversing the dispatch reverses the transfer: the fuel goes back to the supplying tank and out of the forecourt tank, with a reversal line on each. Refused if the forecourt tank no longer holds the litres or they are reserved (same rule as reversing a receipt). The transfer is kept, marked reversed, and drops out of the supply reconciliation.
- **Supply reconciliation:** already counts transfers into a forecourt; reversed ones are now excluded.

## Refused on purpose
- Depots of **different legal entities**: that is an inter-entity transfer (it needs an invoice or recharge between entities), which is not built. Use a dispatch and a receipt for those. Depots with no legal entity set are not restricted.
- A run into a tank cannot also be issued to a vehicle, asset or customer; the tank must be at the chosen forecourt; same fuel; not the depot the fuel leaves.

## Not done
- Inter-entity transfers.
- Existing dispatch and receipt pairs are not converted; they keep matching by hand.
- No partial reversal; a transfer is reversed whole.
- Fleet vehicle fill-up events are unaffected (a run into a tank has no vehicle).

## Changes
V713 (`fuel_dispatches.transfer_id`, `fuel_transfers.reversed_at`); `FuelDispatch.linkTransfer`; `FuelService.dispatchIntoForecourtTank` and the reversal branch; `FuelStockService.reverseTransfer`; `FuelDispatchMatchService`; DTOs gain `destinationTankId`, `transferId`, `byTransfer`, `reversedAt` (all with compatible constructors). Web: DispatchesTab, ForecourtMatchingSection, forecourtSupplyOps. Tests: `FuelDispatchIntoForecourtTankTest` (new), `FuelStockServiceTest`, `FuelDispatchMatchServiceTest`, `ForecourtMatchingSection.test.tsx`.
