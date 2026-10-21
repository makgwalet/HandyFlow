// Sales forecast from the open pipeline: each deal counts at the chance of winning at its stage, grouped by the month it should close.
// The chances are fixed rules of thumb, shown on screen, not a prediction.
import { OPEN_STAGES, isOpen, stageOf, type PipelineLead, type Stage } from "./pipeline.logic"

export const STAGE_CHANCE: Record<Stage, number> = { NEW: 10, CONTACTED: 25, QUALIFIED: 50, WON: 100, LOST: 0 }
export const NO_DATE = "none"
export interface ForecastMonth { month: string; deals: number; total: number; weighted: number }
export interface Forecast { months: ForecastMonth[]; deals: number; total: number; weighted: number; noValue: number; undated: number }

export const weighted = (l: PipelineLead) => Math.round((l.dealValue ?? 0) * STAGE_CHANCE[stageOf(l)]) / 100
/** "2026-11" for a date, or NO_DATE. */
export const monthOf = (l: PipelineLead) => (l.expectedCloseDate && /^\d{4}-\d{2}/.test(l.expectedCloseDate) ? l.expectedCloseDate.slice(0, 7) : NO_DATE)
export const monthLabel = (m: string) => {
  if (m === NO_DATE) return "No close date"
  const [y, mo] = m.split("-").map(Number)
  return `${["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"][mo - 1]} ${y}`
}

/** Open deals only. Deals already past their close date are shown under "Overdue" so they are not hidden in an old month. */
export const OVERDUE = "overdue"
export function buildForecast(leads: PipelineLead[], today: string): Forecast {
  const open = leads.filter(isOpen), byMonth = new Map<string, ForecastMonth>()
  let noValue = 0, undated = 0
  for (const l of open) {
    if (l.dealValue == null) noValue++
    if (monthOf(l) === NO_DATE) undated++
    const key = l.expectedCloseDate && l.expectedCloseDate < today ? OVERDUE : monthOf(l)
    const row = byMonth.get(key) ?? { month: key, deals: 0, total: 0, weighted: 0 }
    row.deals++; row.total += l.dealValue ?? 0; row.weighted += weighted(l)
    byMonth.set(key, row)
  }
  const rank = (m: string) => (m === OVERDUE ? "0" : m === NO_DATE ? "9" : "1" + m)
  const months = [...byMonth.values()].sort((a, b) => rank(a.month).localeCompare(rank(b.month)))
  return { months, deals: open.length, total: months.reduce((s, m) => s + m.total, 0), weighted: months.reduce((s, m) => s + m.weighted, 0), noValue, undated }
}
export const rowLabel = (m: string) => (m === OVERDUE ? "Overdue (close date has passed)" : monthLabel(m))
export const CHANCE_TEXT = OPEN_STAGES.map(s => `${s.charAt(0)}${s.slice(1).toLowerCase()} ${STAGE_CHANCE[s]}%`).join(" · ")
