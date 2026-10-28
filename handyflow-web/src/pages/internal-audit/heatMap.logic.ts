// How the risk heat map is coloured and described. Scores are the 1 to 5 inputs of a risk assessment.

import type { HeatCell, HeatRow } from "../../api/auditHeatMap.api"

export type Band = "low" | "medium" | "high" | "critical"

/** The usual 5 by 5 reading: the product of the two scores, up to 4 low, up to 9 medium, up to 15 high, above that critical. */
export function cellBand(inherent: number, control: number): Band {
  const p = inherent * control
  return p <= 4 ? "low" : p <= 9 ? "medium" : p <= 15 ? "high" : "critical"
}

export const BAND_STYLE: Record<Band, { bg: string; color: string; label: string }> = {
  low: { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", label: "Low" },
  medium: { bg: "var(--hf-warning-soft)", color: "var(--hf-warning-text-strong)", label: "Medium" },
  high: { bg: "var(--hf-orange-soft)", color: "var(--hf-orange-text-strong)", label: "High" },
  critical: { bg: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text)", label: "Critical" },
}

const SCORE_WORDS = ["", "very low", "low", "moderate", "high", "very high"]
export const scoreWord = (n: number) => SCORE_WORDS[n] ?? String(n)

/** What a screen reader hears for one square. */
export function cellLabel(c: HeatCell): string {
  const base = `Inherent risk ${scoreWord(c.inherent)}, control risk ${scoreWord(c.control)}`
  return c.count === 0 ? `${base}: no areas` : `${base}: ${c.count} ${c.count === 1 ? "area" : "areas"}: ${c.areas.join(", ")}`
}

/** The squares as five rows of five, highest inherent risk first (the order the server sends). */
export function gridRows(cells: HeatCell[]): HeatCell[][] {
  const rows: HeatCell[][] = []
  for (const c of cells) {
    const i = 5 - c.inherent
    ;(rows[i] ??= [])[c.control - 1] = c
  }
  return rows.filter(Boolean)
}

/** The figures of one process-area row in column order. */
export const rowCounts = (r: HeatRow): { band: Band; n: number }[] => [
  { band: "low", n: r.low }, { band: "medium", n: r.medium }, { band: "high", n: r.high }, { band: "critical", n: r.critical },
]

/** Areas rated high or critical, as a share of those assessed; 0 when none have been. */
export function highShare(rows: HeatRow[]): number {
  const assessed = rows.reduce((s, r) => s + r.low + r.medium + r.high + r.critical, 0)
  const high = rows.reduce((s, r) => s + r.high + r.critical, 0)
  return assessed === 0 ? 0 : Math.round((high / assessed) * 100)
}
