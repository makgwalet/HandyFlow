# Fuel: freight added to a stock receipt later (2026-11-15)

Status: written, not compiled or run (Java). Web tests (488 in the fuel folder) and `tsc` pass. Flyway V694 is new.

## What it does
Phase 7b only allowed freight when the fuel was received. A carrier invoice that arrives later can now be added to the receipt:
`POST /api/v1/fuel/receipts/{id}/freight` `{amount, note}` and `GET /api/v1/fuel/receipts/{id}/freight` (history). Same permissions as receiving fuel (FUEL_MANAGE or FUEL_OPERATIONS_MANAGE to add, FUEL_READ to read). A **Freight** button on each receipt row opens the dialog.

## The rule (a costing policy, stated so it can be challenged)
The tank mixes fuel, so it cannot say which litres came from which receipt.
- Share still in the tank = the lesser of the receipt's litres and the tank's current litres.
- Absorbed amount = freight x share / receipt litres. It is added to the tank's cost: the weighted average moves by absorbed / litres in the tank.
- The rest is **unabsorbed**: it belongs to fuel already sold. It is recorded, shown on screen, and never pushed back into deliveries already costed, so closed margin reports do not move.
- An empty tank, or one with no cost yet, absorbs nothing.
- The supplier's price per litre and total cost never change, so purchase order and supplier invoice matching are unaffected.

## History kept
Every addition is its own append-only row in `fuel_receipt_freight_adjustments` (amount, note, litres in the tank at the time, absorbed, unabsorbed, cost before and after, who, when). The receipt's `freight_amount` is the sum of the original and all additions. A reversed receipt cannot take freight. The tank row is locked while it is applied.

## Limits
- Positive amounts only. A carrier credit (reducing freight) is not built.
- The "share in the tank" is an estimate: if other receipts came in after this one, fuel from them counts toward the litres in the tank. It is capped at the receipt's litres.
- Not posted to accounts payable, no VAT handling (same as 7b).
