// src/pages/agriculture/agSales.logic.ts
//
// Pure rules for the sales-allocation screens. The server is the authority (it re-checks everything and computes revenue live); these exist
// to explain a problem before the request is sent and to preview the share of revenue a quantity will carry.
import { addDays } from "./agCrops.logic"
import type { TargetType } from "./agLedger.api"

/** Quantities are held to three decimals; this absorbs rounding when a line is allocated in full (same tolerance as the server). */
export const QUANTITY_TOLERANCE = 0.0005
export const DEFAULT_RANGE_DAYS = 90

/** The default look-back for finding invoice lines: the last 90 days. */
export function defaultRange(today: string): { from: string; to: string } {
  return { from: addDays(today, -DEFAULT_RANGE_DAYS), to: today }
}

export interface SaleShareInput { targetType: TargetType | ""; targetId: string; quantity: string; headCount: string }

const num = (s: string) => { const n = Number(s.trim().replace(",", ".")); return s.trim() === "" || !Number.isFinite(n) ? NaN : n }

// Rounded to six places only to remove floating-point noise (1.1 + 2.2); the real tolerance is QUANTITY_TOLERANCE, applied by validateSale.
export const totalQuantity = (shares: SaleShareInput[]) => Math.round(shares.reduce((t, s) => t + (num(s.quantity) || 0), 0) * 1_000_000) / 1_000_000

/** Display wording for what is left to allocate, e.g. "600 kg". */
export const quantityText = (qty: number, unit: string | null) => `${Number(qty.toLocaleString("en-US", { maximumFractionDigits: 3, useGrouping: false }))}${unit ? ` ${unit}` : ""}`

/**
 * Roughly what a quantity of a line will be worth: its proportional share of the line's net revenue, to the cent. The server's figure is the
 * one that counts (it apportions across every allocation of the line so the cents add up exactly), so this is only labelled "about".
 */
export function previewShare(netRevenue: number, lineQuantity: number, quantity: number): number | null {
  if (!(lineQuantity > 0) || !(quantity > 0) || !Number.isFinite(netRevenue)) return null
  return Math.round((netRevenue * Math.min(quantity, lineQuantity)) / lineQuantity * 100) / 100
}

/** The first thing wrong with an allocation about to be saved, or null when it can be. */
export function validateSale(line: { remainingQuantity: number; unit: string | null }, shares: SaleShareInput[], soldOn: string, today: string): string | null {
  if (soldOn && soldOn > today) return "The sale date can't be in the future."
  if (shares.length === 0) return "Choose what this sale belongs to."
  const seen = new Set<string>()
  for (const s of shares) {
    if (!s.targetType || !s.targetId) return "Choose a target for every line."
    if (!(num(s.quantity) > 0)) return "Enter a quantity above zero for every line."
    if (s.headCount.trim() !== "") {
      const h = num(s.headCount)
      if (!Number.isInteger(h) || h <= 0) return "Head count must be a whole number above zero."
      if (s.targetType === "ANIMAL" && h !== 1) return "A single animal is one head."
    }
    const key = `${s.targetType}:${s.targetId}`
    if (seen.has(key)) return "The same target is listed twice."
    seen.add(key)
  }
  if (totalQuantity(shares) - line.remainingQuantity > QUANTITY_TOLERANCE) return `Only ${quantityText(line.remainingQuantity, line.unit)} is left on this invoice line.`
  return null
}
