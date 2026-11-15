// Late freight on a stock receipt. Display and checks only; the server splits the amount and moves the tank's cost.
export interface FreightAdjustment {
  id: string; amount: number; note: string; litresInTank: number; absorbedAmount: number; unabsorbedAmount: number
  costBefore: number | null; costAfter: number | null; createdAt: string
}
export interface ReceiptFreight { receiptId: string; freightTotal: number | null; landedPricePerLitre: number; adjustments: FreightAdjustment[] }

/** Message for the form, or null when it can be sent. */
export function validateFreight(amount: string, note: string): string | null {
  const a = Number(amount)
  if (amount.trim() === "" || !Number.isFinite(a) || a <= 0) return "Enter the freight in rand, more than zero."
  if (!note.trim()) return "Say what the freight is for, for example the carrier invoice number."
  if (note.trim().length > 300) return "The note can be at most 300 characters."
  return null
}

const r2 = (n: number) => `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** One line saying what an addition did to the tank's cost, in words. */
export function adjustmentText(a: FreightAdjustment): string {
  if (a.absorbedAmount > 0 && a.unabsorbedAmount > 0) return `${r2(a.absorbedAmount)} went into the tank's cost; ${r2(a.unabsorbedAmount)} belongs to fuel already sold and was not applied to past deliveries.`
  if (a.absorbedAmount > 0) return `All ${r2(a.absorbedAmount)} went into the tank's cost.`
  return `The tank holds none of this fuel any more, so ${r2(a.unabsorbedAmount)} was recorded but did not change the tank's cost.`
}
