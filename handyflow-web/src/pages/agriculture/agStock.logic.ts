// src/pages/agriculture/agStock.logic.ts
// Mirrors AgStockRules on the server: the average cost after a delivery, and what a stock-count correction does. The server
// repeats every check; these only explain a problem or show the result before Save.
export const MIN_REASON = 3

/** Weighted average unit cost after a delivery, rounded to cents; null when no cost is known. */
export function averageCostAfterReceipt(onHandQty: number, onHandCost: number | null, receivedQty: number, receivedCost: number | null): number | null {
  if (receivedCost == null) return onHandCost
  if (onHandCost == null || onHandQty <= 0) return Math.round(receivedCost * 100) / 100
  return Math.round(((onHandQty * onHandCost + receivedQty * receivedCost) / (onHandQty + receivedQty)) * 100) / 100
}

export interface AdjustmentPreview { direction: "up" | "down" | "none"; size: number }

export function adjustmentPreview(onHand: number, newQty: number): AdjustmentPreview {
  const diff = Math.round((newQty - onHand) * 1000) / 1000
  return { direction: diff > 0 ? "up" : diff < 0 ? "down" : "none", size: Math.abs(diff) }
}

export function adjustmentText(onHand: number, newQty: number, unit: string): string {
  const p = adjustmentPreview(onHand, newQty)
  if (p.direction === "none") return "Same as what is on hand: nothing to adjust."
  return `This writes ${p.size} ${unit} ${p.direction === "up" ? "on to" : "off"} the stock (${onHand} → ${newQty}).`
}

/** Why this adjustment cannot be sent, or null. */
export function adjustError(newQty: string, reason: string, onHand: number): string | null {
  if (newQty.trim() === "" || !(Number(newQty) >= 0)) return "Enter the counted quantity (0 or more)."
  if (adjustmentPreview(onHand, Number(newQty)).direction === "none") return "Same as what is on hand: nothing to adjust."
  if (reason.trim().length < MIN_REASON) return `Give a reason, for example "Stock count" (at least ${MIN_REASON} characters).`
  return null
}

/** Why this delivery cannot be sent, or null. A cost is optional. */
export function receiveError(qty: string, cost: string): string | null {
  if (!(Number(qty) > 0)) return "Enter a quantity greater than 0."
  if (cost.trim() !== "" && !(Number(cost) >= 0)) return "The cost cannot be negative."
  return null
}

const COUNT_UNITS = ["bag", "bags", "bale", "bales", "sack", "sacks"]

/** Why this kg per bag cannot be sent, or null. Empty is fine (no pack size). Mirrors AgPackRules on the server. */
export function packSizeError(unit: string, value: string): string | null {
  if (value.trim() === "") return null
  if (!COUNT_UNITS.includes(unit.trim().toLowerCase())) return "Kg per bag only applies to items counted in bags, bales or sacks."
  const n = Number(value)
  if (!(n > 0)) return "Kg per bag must be more than zero."
  if (n > 10000) return "Kg per bag is not credible. Check the number."
  return null
}

/** The value to send: a number for a count unit, otherwise null (clears it). */
export function packSizeToSend(unit: string, value: string): number | null {
  if (value.trim() === "" || packSizeError(unit, value)) return null
  return Number(value)
}
