# Fuel Phase 7a: returned litres to tank (patch 0800)

## Implemented
- **Distribution > Returned fuel.** Lists loads where litres were declared as brought back on a failed attempt (C04) and not yet all confirmed into a tank, with brought back, confirmed and still to confirm. A person with FUEL_MANAGE or FUEL_TRANSPORT_MANAGE confirms part or all into a chosen tank. Append-only record (who, when, tank, litres, note); a retry with the same request id changes nothing.
- **Limit.** For a load the most that can be confirmed is what was declared returned on its failed attempts, plus, once it is delivered, the loaded litres that were not delivered. Anything more is refused with the amount still available.

## Decision to know about: no stock movement
A tank is only deducted when a delivery completes. Fuel that came back on the truck was therefore never taken out of the tank's book balance. Booking it "back" would overstate stock, so this feature records where the fuel physically went and shows what is unconfirmed; it does not add a stock movement and does not change cost. If you later want the tank deducted at loading (a bigger change to the stock model), returns would then need to book a movement.

## Preserved
Failed attempts (C04), stock, ledger, variances and tank reconciliation are unchanged.

## Files
- DB: V679 `fuel_load_returns` (append-only; litres > 0; unique request id per load). No new permission.
- Java: FuelLoadReturn, FuelLoadReturnRepository, FuelLoadReturnService, FuelLoadReturnController, LoadReturnDtos, repository addition `FuelDeliveryExceptionRepository.findAllWithReturnedLitres`, FuelDivisionRules ("returned-fuel" with distribution).
- Web: ReturnedFuelTab, returnedFuelOps, menu "Returned fuel" under Distribution, FuelPage case.

## API
GET /api/v1/fuel/load-returns/outstanding and GET /api/v1/fuel/deliveries/{id}/returns (FUEL_READ); POST /api/v1/fuel/deliveries/{id}/returns (FUEL_MANAGE or FUEL_TRANSPORT_MANAGE). Module guard on each; tenant from context.

## Tests
- Java: FuelLoadReturnServiceTest (8). Parse-checked only; not run (you run Maven).
- Web: ReturnedFuelTab (4), returnedFuelOps (2). tsc clean.

## Gaps and risks
- A return is not tied to a tank dip or the tank reconciliation; it is an audit trail only.
- Returned fuel of a different grade into the wrong tank is not checked (the load's fuel type is not compared to the tank's).
- Outstanding list is built in memory from failed attempts; fine for normal volumes.

## Next
Compare fuel type to the tank; show returns inside tank reconciliation as an explanation.
