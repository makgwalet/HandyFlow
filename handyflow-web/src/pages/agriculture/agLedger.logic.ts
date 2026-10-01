// src/pages/agriculture/agLedger.logic.ts
//
// Pure rules for the cost ledger screens. splitPreview mirrors the server's AgCostAllocation (largest-remainder apportionment in whole
// cents), so the preview shows exactly the rows that will be saved. Keep the two in step.
import type { CostEntry, TargetType } from "./agLedger.api"

export const TARGET_TYPES: TargetType[] = ["CROP_CYCLE", "GROUP", "ANIMAL", "ENTERPRISE"]
export const TARGET_LABEL: Record<TargetType, string> = { CROP_CYCLE: "Crop cycle", GROUP: "Group", ANIMAL: "Animal", ENTERPRISE: "Enterprise" }
export const CATEGORY_LABEL: Record<string, string> = { LABOUR: "Labour", EQUIPMENT: "Equipment", FUEL: "Fuel", OTHER_DIRECT: "Other direct" }

/** Percentages must total 100 within this, like the server. */
export const PERCENT_TOLERANCE = 0.005

export interface ShareInput { targetType: TargetType | ""; targetId: string; percentage: string }

const num = (s: string) => { const n = Number(s.trim().replace(",", ".")); return s.trim() === "" || !Number.isFinite(n) ? NaN : n }
export const percentTotal = (shares: ShareInput[]) => Math.round(shares.reduce((t, s) => t + (num(s.percentage) || 0), 0) * 10000) / 10000

/** n percentages totalling exactly 100 to two decimals; the first ones take the extra hundredths ("33.34", "33.33", "33.33"). */
export function evenShares(n: number): string[] {
  if (n <= 0) return []
  const hundredths = 10000, base = Math.floor(hundredths / n), extra = hundredths - base * n
  return Array.from({ length: n }, (_, i) => ((base + (i < extra ? 1 : 0)) / 100).toFixed(2))
}

/**
 * The amount of each share in rand, adding up exactly to the cost: every share is rounded DOWN to the cent and the leftover cents go to
 * the largest fractional remainders (ties to the earlier share). Null when the amount or percentages are not usable.
 */
export function splitPreview(amount: number, percentages: number[]): number[] | null {
  const total = Math.round(amount * 100)
  if (!Number.isFinite(amount) || total <= 0 || percentages.length === 0 || percentages.some(p => !(p > 0))) return null
  const sum = percentages.reduce((a, b) => a + b, 0)
  const exact = percentages.map(p => (total * p) / sum)
  const cents = exact.map(x => Math.floor(x + 1e-9))
  const frac = exact.map((x, i) => x - cents[i])
  let leftover = total - cents.reduce((a, b) => a + b, 0)
  const order = frac.map((_, i) => i).sort((a, b) => (frac[b] - frac[a] > 1e-9 ? 1 : frac[a] - frac[b] > 1e-9 ? -1 : a - b))
  for (let k = 0; leftover > 0; k = (k + 1) % order.length, leftover--) cents[order[k]]++
  return cents.map(c => c / 100)
}

/** The first thing wrong with a cost about to be saved, or null when it can be saved. */
export function validateAllocation(amount: string, description: string, date: string, shares: ShareInput[], today: string): string | null {
  const a = num(amount)
  if (!description.trim()) return "Describe the cost."
  if (!date) return "Choose the date."
  if (date > today) return "The date can't be in the future."
  if (!(a > 0)) return "Enter an amount above zero."
  if (shares.length === 0) return "Choose what the cost is for."
  const seen = new Set<string>()
  for (const s of shares) {
    if (!s.targetType || !s.targetId) return "Choose a target for every line."
    if (!(num(s.percentage) > 0) || num(s.percentage) > 100) return "Each percentage must be above 0 and at most 100."
    const key = `${s.targetType}:${s.targetId}`
    if (seen.has(key)) return "The same target is listed twice."
    seen.add(key)
  }
  const total = percentTotal(shares)
  if (Math.abs(total - 100) > PERCENT_TOLERANCE) return `The percentages total ${total}, not 100.`
  return null
}

export interface CostGroup {
  id: string; date: string; description: string; category: string; sourceType: string
  rows: CostEntry[]            // the original rows, in the order saved
  status: "ACTIVE" | "REVERSED"
  total: number                // what the cost was, before any reversal
  reversedNote: string | null; reversedAt: string | null
}

/** One cost with its parts: ledger rows sharing an allocation group, newest first. Reversal rows fold into the group they undo. */
export function groupEntries(entries: CostEntry[]): CostGroup[] {
  const byGroup = new Map<string, CostEntry[]>()
  for (const e of entries) byGroup.set(e.allocationGroupId, [...(byGroup.get(e.allocationGroupId) ?? []), e])
  const groups: CostGroup[] = []
  for (const [id, rows] of byGroup) {
    const originals = rows.filter(r => r.status !== "REVERSAL").sort((a, b) => a.createdAt.localeCompare(b.createdAt))
    if (originals.length === 0) continue
    const reversal = rows.find(r => r.status === "REVERSAL")
    groups.push({
      id, date: originals[0].entryDate, description: originals[0].description, category: originals[0].category, sourceType: originals[0].sourceType, rows: originals,
      status: originals.every(r => r.status === "REVERSED") ? "REVERSED" : "ACTIVE",
      total: Math.round(originals.reduce((t, r) => t + r.amount, 0) * 100) / 100,
      reversedNote: reversal?.notes ?? null, reversedAt: reversal?.createdAt ?? null,
    })
  }
  return groups.sort((a, b) => b.date.localeCompare(a.date) || b.rows[0].createdAt.localeCompare(a.rows[0].createdAt))
}
