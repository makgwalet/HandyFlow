// How the risk heat map is coloured and described. Scores are the 1 to 5 inputs of a risk assessment.

import type { HeatArea, HeatCell, HeatRow } from "../../api/auditHeatMap.api"

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

// ── Residual risk and control effectiveness ────────────────────────────────────────────────────────────────
// Neither is stored. Both are read from the two scores in the assessment: control risk 1 to 5, where higher means weaker control.

/** Residual risk: what is left once controls are taken into account, read as the product of the two scores, banded as the grid is. */
export const residualBand = (a: Pick<HeatArea, "inherent" | "control">): Band => cellBand(a.inherent, a.control)

export type Effectiveness = "effective" | "partly" | "weak"
export const EFFECTIVENESS_STYLE: Record<Effectiveness, { label: string; tone: "success" | "info" | "warning"; hint: string }> = {
  effective: { label: "Effective", tone: "success", hint: "control risk 1 or 2" },
  partly: { label: "Partly effective", tone: "info", hint: "control risk 3" },
  weak: { label: "Weak", tone: "warning", hint: "control risk 4 or 5" },
}
export const effectiveness = (control: number): Effectiveness => (control <= 2 ? "effective" : control === 3 ? "partly" : "weak")

const BAND_ORDER: Band[] = ["low", "medium", "high", "critical"]
export const EFFECTIVENESS_ORDER: Effectiveness[] = ["effective", "partly", "weak"]

/** Areas worst residual first (then by product, then name). */
export function byResidual(areas: HeatArea[]): HeatArea[] {
  return [...areas].sort((a, b) =>
    BAND_ORDER.indexOf(residualBand(b)) - BAND_ORDER.indexOf(residualBand(a)) || b.inherent * b.control - a.inherent * a.control || a.name.localeCompare(b.name))
}

/** Areas weakest control first (then highest inherent risk, then name). */
export function byControl(areas: HeatArea[]): HeatArea[] {
  return [...areas].sort((a, b) => b.control - a.control || b.inherent - a.inherent || a.name.localeCompare(b.name))
}

export function residualCounts(areas: HeatArea[]): Record<Band, number> {
  const out: Record<Band, number> = { low: 0, medium: 0, high: 0, critical: 0 }
  areas.forEach(a => { out[residualBand(a)]++ })
  return out
}

export function effectivenessCounts(areas: HeatArea[]): Record<Effectiveness, number> {
  const out: Record<Effectiveness, number> = { effective: 0, partly: 0, weak: 0 }
  areas.forEach(a => { out[effectiveness(a.control)]++ })
  return out
}

/** Per process area, how many areas fall in each control effectiveness group; weakest areas first. */
export function controlByProcessArea(areas: HeatArea[]): { processArea: string; counts: Record<Effectiveness, number>; total: number }[] {
  const m = new Map<string, HeatArea[]>()
  areas.forEach(a => { const k = a.processArea.toLowerCase(); m.set(k, [...(m.get(k) ?? []), a]) })
  return [...m.values()]
    .map(list => ({ processArea: list[0].processArea, counts: effectivenessCounts(list), total: list.length }))
    .sort((a, b) => b.counts.weak - a.counts.weak || b.counts.partly - a.counts.partly || a.processArea.localeCompare(b.processArea))
}
