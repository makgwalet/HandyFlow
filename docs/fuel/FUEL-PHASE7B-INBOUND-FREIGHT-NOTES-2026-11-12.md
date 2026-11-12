# Fuel Phase 7b: inbound freight (patch 0801)

## Implemented
- **Freight on a stock receipt.** When fuel is received into a tank, an optional total freight amount (rand) and note can be entered. The freight is spread over the litres received and added to the supplier's price to build the tank's weighted-average cost, so cost at sale, margin, landed cost per litre and the stock value all include it.
- **The supplier's figures do not change.** The receipt's price per litre and total cost stay exactly as the supplier charged them. Purchase order matching and supplier invoice matching read those, so they are unaffected. Freight is stored beside them and shown on the receipts list ("+ R1,500.00 freight").
- Zero or blank freight records nothing. Negative freight is refused (screen, request validation, database check).
- The Landed cost screen text now says the cost at sale includes freight entered at receipt.

## Preserved
Receipts with no freight behave exactly as before, and existing receipts are untouched (the new columns are empty). Reversing a receipt removes litres the same way as before; the tank's average cost is not recalculated on removal, as it never was.

## Files
- DB: V680 (`fuel_receipts.freight_amount`, `freight_note`, check freight >= 0). No new permission: receiving uses its existing permission.
- Java: FuelReceipt (landedPrice, recordFreight, landedPricePerLitre), ReceiveFuelRequest (two optional fields; the old 7-argument constructor kept), ReceiptResponse (freightAmount, freightNote, landedPricePerLitre), FuelService.receiveFuel and its response mapping.
- Web: receiveOps, TanksTab (receive form), ReceiptsTab, LandedCostTab text.

## Tests
- Java: FuelReceiptFreightTest (7): no freight unchanged, spread over litres and rounding, negative refused, supplier figures kept, zero records nothing, the tank's average cost with freight (R20.65 in the worked example), note length. Parse-checked only; not run (you run Maven).
- Web: receiveOps (3). Fuel suite passes (404); tsc clean.

## Gaps and risks
- Freight must be entered when the fuel is received. A carrier invoice that arrives later cannot be added to an existing receipt, because that would mean restating the average cost after fuel may already have been sold. Enter the best known figure at receipt.
- One freight amount per receipt, in rand; no split by carrier, no VAT handling (enter ex VAT if VAT is claimable).
- Freight is not posted to accounts payable. Supplier invoice matching still compares the fuel only.
- The purchase order screen does not yet show freight.

## Next
Late freight adjustment through a controlled restatement; carrier invoices through AP; show freight on the purchase order match.
