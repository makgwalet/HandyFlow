// src/pages/agriculture/agLabour.logic.ts
//
// Pure rules for the labour-costing screen. The server is the authority (it re-reads the rate, applies the on-cost, snapshots it and refuses
// double-costing); these mirror its arithmetic so a figure shown before costing is the figure that gets recorded.
import type { LabourCandidate } from "./agLabour.api"

export const MAX_HOURS_PER_WEEK = 84

const round = (n: number, places: number) => { const f = 10 ** places; return Math.round((n + Number.EPSILON) * f) / f }

/** The base hourly rate loaded with the employer's on-cost, to four places: what the ledger snapshots. */
export const loadedRate = (baseRate: number, onCostPercent: number) => round(baseRate * (1 + onCostPercent / 100), 4)

/** The cost of the hours at the loaded rate, to the cent. */
export const labourAmount = (hours: number, loaded: number) => round(hours * loaded, 2)

/** A typed rate as a number, or null when it is blank or not a positive number. */
export function parseRate(text: string | undefined): number | null {
  if (text == null || text.trim() === "") return null
  const n = Number(text.trim().replace(",", "."))
  return Number.isFinite(n) && n > 0 ? n : null
}

/** The base rate that will be used for a row: what was typed, else the one HR offered, else none. */
export function baseRateFor(row: LabourCandidate, typed: string | undefined): number | null {
  return parseRate(typed) ?? row.suggestedRate
}

/** What the row will cost at its rate and the on-cost, or null while it has no rate. */
export function costFor(row: LabourCandidate, typed: string | undefined, onCostPercent: number): number | null {
  const base = baseRateFor(row, typed)
  return base == null ? null : labourAmount(row.hours, loadedRate(base, onCostPercent))
}

export const rowKey = (r: Pick<LabourCandidate, "sourceType" | "sourceId">) => `${r.sourceType}:${r.sourceId}`

/** The first reason the selected rows cannot be costed, or null when they can. */
export function validateCosting(selected: LabourCandidate[], typed: Record<string, string>): string | null {
  if (selected.length === 0) return "Choose the work to cost."
  for (const r of selected) {
    const t = typed[rowKey(r)]
    if (t != null && t.trim() !== "" && parseRate(t) == null) return `Enter a rate above zero for the work on ${r.date}.`
    if (baseRateFor(r, t) == null) return `Enter an hourly rate for the work on ${r.date}${r.workerName ? ` (${r.workerName})` : ""}.`
  }
  return null
}

/** The first thing wrong with the settings about to be saved, or null. Same ranges as the server. */
export function settingsProblem(hours: string, onCost: string): string | null {
  const h = Number(hours.trim().replace(",", ".")), p = Number(onCost.trim().replace(",", "."))
  if (hours.trim() === "" || !Number.isFinite(h) || h <= 0 || h > MAX_HOURS_PER_WEEK) return `Standard hours per week must be above 0 and at most ${MAX_HOURS_PER_WEEK}.`
  if (onCost.trim() === "" || !Number.isFinite(p) || p < 0 || p > 100) return "The on-cost must be between 0 and 100 percent."
  return null
}

export const hoursText = (h: number) => `${Number(h.toLocaleString("en-US", { maximumFractionDigits: 2, useGrouping: false }))} h`
