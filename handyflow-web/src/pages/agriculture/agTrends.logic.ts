// src/pages/agriculture/agTrends.logic.ts
// Pure helpers for the trends screens: labels, chart data and whether a change is good or bad news.
import type { AgTrends, Comparison } from "./agTrends.api"
import { fmtNum } from "./agDashboard.logic"
import { fmtMoney } from "./constants"

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
/** "2026-10" becomes "Oct 26" (fixed wording, so it does not depend on the browser's locale data). */
export function monthLabel(key: string): string {
  const [y, m] = key.split("-").map(Number)
  return `${MONTHS[m - 1] ?? key} ${String(y).slice(2)}`
}

/** Whether a rise is good news. Costs, deaths and high-severity findings are better lower; harvest and births are better higher. */
const HIGHER_IS_BETTER: Record<string, boolean> = {
  TOTAL_COST: false, CROP_COST: false, LIVESTOCK_COST: false, DEATHS: false, HIGH_SEVERITY_SCOUTING: false,
  HARVEST_TONNES: true, BIRTHS: true,
}
export type Tone = "good" | "bad" | "flat" | "none"

/** Colour meaning for a comparison: good, bad, flat (no change) or none (nothing to compare against). */
export function deltaTone(c: Pick<Comparison, "key" | "current" | "previous" | "changePercent">): Tone {
  if (c.current === c.previous) return c.current === 0 ? "none" : "flat"
  if (c.changePercent == null) return "none"                  // earlier period was zero: no meaningful direction
  const up = c.current > c.previous
  const better = HIGHER_IS_BETTER[c.key]
  if (better == null) return "flat"
  return up === better ? "good" : "bad"
}

/** "+12.5%", "−25%" (a real minus sign), "No earlier data" when the earlier period had nothing, "No change". */
export function deltaText(c: Pick<Comparison, "current" | "previous" | "changePercent">): string {
  if (c.current === c.previous) return c.current === 0 ? "No data yet" : "No change"
  if (c.changePercent == null) return "No earlier data"
  const abs = Math.abs(c.changePercent)
  const n = Number.isInteger(abs) ? String(abs) : abs.toFixed(1)
  return `${c.changePercent > 0 ? "+" : "\u2212"}${n}%`
}

export const monthRow = (key: string, partial: boolean) => ({ name: monthLabel(key) + (partial ? "*" : ""), key })

export function costChartData(t: AgTrends) {
  return t.costs.map((c, i) => ({ ...monthRow(c.month, t.months[i]?.partial ?? false), Seed: c.seed, Inputs: c.inputs, Feed: c.feed, Health: c.health, "Animal purchases": c.animalPurchases }))
}
export function tonnesChartData(t: AgTrends) {
  return t.production.tonnes.map((p, i) => ({ ...monthRow(p.month, t.months[i]?.partial ?? false), Tonnes: p.tonnes }))
}
export function livestockChartData(t: AgTrends) {
  return t.livestock.map((l, i) => ({ ...monthRow(l.month, t.months[i]?.partial ?? false), Births: l.births, Deaths: l.deaths }))
}

export const hasAny = (values: number[]) => values.some(v => v !== 0)
export const hasCosts = (t: AgTrends) => t.costs.some(c => c.total !== 0)
export const hasHarvest = (t: AgTrends) => hasAny(t.production.tonnes.map(p => p.tonnes))
export const hasLivestockEvents = (t: AgTrends) => t.livestock.some(l => l.births !== 0 || l.deaths !== 0)

/** A comparison value in its own unit: rand, tonnes, head or a plain count. */
export function formatComparison(c: Pick<Comparison, "unit">, n: number): string {
  if (c.unit === "R") return fmtMoney(n)
  if (c.unit === "t") return `${fmtNum(n, 2)} t`
  return `${fmtNum(n, 0)}${c.unit === "head" ? " head" : ""}`
}
