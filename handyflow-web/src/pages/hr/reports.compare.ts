// src/pages/hr/reports.compare.ts
//
// Comparing a report with an earlier period, and the data behind the charts. Pure: the server works every figure out; this only
// lines two reports up and says how a figure moved. A group hidden for being too small has no number here, so it cannot be worked out
// from a difference.
import type { Cell, Group, Period } from "./reports.logic"
import type { Compliance, PayrollReport, TimeReport, Workforce } from "./reports.shared"

export type CompareMode = "none" | "previous" | "year"

export const COMPARE_LABEL: Record<CompareMode, string> = { none: "No comparison", previous: "The period just before", year: "The same period last year" }

const utc = (s: string) => new Date(`${s}T00:00:00Z`)
const iso = (d: Date) => d.toISOString().slice(0, 10)

/** The period to compare with: the same number of days straight before, or the same dates a year earlier (29 February becomes 28 February). */
export function comparisonPeriod(p: Period, mode: CompareMode): Period | null {
  if (mode === "none" || !p.from || !p.to) return null
  if (mode === "previous") {
    const days = Math.round((utc(p.to).getTime() - utc(p.from).getTime()) / 86400000) + 1
    const to = utc(p.from); to.setUTCDate(to.getUTCDate() - 1)
    const from = new Date(to); from.setUTCDate(from.getUTCDate() - (days - 1))
    return { from: iso(from), to: iso(to) }
  }
  const back = (s: string) => {
    const d = utc(s)
    const m = d.getUTCMonth(), day = d.getUTCDate()
    const r = new Date(Date.UTC(d.getUTCFullYear() - 1, m, Math.min(day, 28)))
    // Keep the 29th when last year had one.
    if (day > 28) { const t = new Date(Date.UTC(d.getUTCFullYear() - 1, m, day)); if (t.getUTCMonth() === m) return iso(t) }
    return iso(r)
  }
  return { from: back(p.from), to: back(p.to) }
}

export type Unit = "count" | "money" | "percent" | "days" | "hours"
/** Which way is better: "up", "down", or "none" when neither is (headcount, say). */
export type Good = "up" | "down" | "none"

export interface Metric { label: string; unit: Unit; good: Good; current: number | null; before: number | null }

const m = (label: string, unit: Unit, good: Good, current: number | null | undefined, before: number | null | undefined): Metric =>
  ({ label, unit, good, current: current ?? null, before: before ?? null })

export const workforceMetrics = (c: Workforce, b: Workforce): Metric[] => [
  m("Headcount at the end", "count", "none", c.closing, b.closing),
  m("Joiners", "count", "none", c.joiners, b.joiners),
  m("Leavers", "count", "down", c.leavers, b.leavers),
  m("Turnover for a year", "percent", "down", c.annualisedTurnoverPercent, b.annualisedTurnoverPercent),
]

export const timeMetrics = (c: TimeReport, b: TimeReport): Metric[] => {
  const days = (t: TimeReport) => t.leave.reduce((n, l) => n + l.days, 0)
  const hours = (t: TimeReport) => t.overtimeByKind.reduce((n, l) => n + (l.hours ?? 0), 0)
  return [
    m("Approved leave days", "days", "none", days(c), days(b)),
    m("Sick leave days", "days", "down", c.sickDays, b.sickDays),
    m("Days absent", "days", "down", c.attendance?.absentDays, b.attendance?.absentDays),
    m("Days late", "days", "down", c.attendance?.lateDays, b.attendance?.lateDays),
    m("Approved overtime hours", "hours", "down", hours(c), hours(b)),
  ]
}

export const payrollMetrics = (c: PayrollReport, b: PayrollReport): Metric[] => [
  m("Gross pay", "money", "none", c.totals.gross, b.totals.gross),
  m("Employer cost", "money", "down", c.totals.employerCost, b.totals.employerCost),
  m("PAYE", "money", "none", c.totals.paye, b.totals.paye),
  m("Net pay", "money", "none", c.totals.net, b.totals.net),
  m("Finished pay runs", "count", "none", c.totals.runs, b.totals.runs),
]

export const complianceMetrics = (c: Compliance, b: Compliance): Metric[] => {
  const open = (x: Compliance) => x.checks.reduce((n, k) => n + k.count, 0)
  const er = (x: Compliance) => x.employeeRelations
  return [
    m("Records and follow-up items to fix", "count", "down", open(c), open(b)),
    ...(er(c) && er(b) ? [
      m("Cases opened", "count", "down", er(c)!.opened, er(b)!.opened),
      m("Cases closed", "count", "up", er(c)!.closed, er(b)!.closed),
      m("Cases open at the end", "count", "down", er(c)!.open, er(b)!.open),
    ] : []),
  ]
}

export interface Change { amount: number | null; percent: number | null; direction: "up" | "down" | "same" | "none"; tone: "good" | "bad" | "neutral" }

/** How a figure moved. No change is worked out when either side is missing; a percentage needs a starting figure above nil. */
export function change(x: Metric): Change {
  if (x.current === null || x.before === null) return { amount: null, percent: null, direction: "none", tone: "neutral" }
  const amount = Math.round((x.current - x.before) * 100) / 100
  const percent = x.before === 0 ? null : Math.round(((x.current - x.before) / Math.abs(x.before)) * 1000) / 10
  const direction = amount === 0 ? "same" : amount > 0 ? "up" : "down"
  const tone = direction === "same" || x.good === "none" ? "neutral" : (direction === x.good ? "good" : "bad")
  return { amount, percent, direction, tone }
}

const ZAR = new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" })
const NUM = new Intl.NumberFormat("en-ZA", { maximumFractionDigits: 1 })

export function formatValue(v: number | null, unit: Unit): string {
  if (v === null) return "–"
  switch (unit) {
    case "money": return ZAR.format(v)
    case "percent": return `${v.toFixed(1)}%`
    case "days": return `${NUM.format(v)} days`
    case "hours": return `${NUM.format(v)} h`
    default: return NUM.format(v)
  }
}

export function formatChange(x: Metric): string {
  const c = change(x)
  if (c.amount === null) return "–"
  if (c.direction === "same") return "No change"
  const sign = c.amount > 0 ? "+" : "−"
  const abs = Math.abs(c.amount)
  const body = x.unit === "money" ? ZAR.format(abs) : x.unit === "percent" ? `${abs.toFixed(1)} points` : formatValue(abs, x.unit)
  return `${sign}${body}${c.percent !== null && x.unit !== "percent" ? ` (${c.percent > 0 ? "+" : "−"}${Math.abs(c.percent).toFixed(1)}%)` : ""}`
}

/** The comparison as spreadsheet rows, for adding under a report. */
export function comparisonRows(metrics: Metric[], now: Period, before: Period, modeLabel: string): Cell[][] {
  return [
    [], ["Comparison", modeLabel], ["", `${now.from} to ${now.to}`, `${before.from} to ${before.to}`, "Change"],
    ...metrics.map(x => [x.label, x.current, x.before, change(x).amount]),
  ]
}

// ── Chart data ──────────────────────────────────────────────────────────────────────────────────

export interface Bar { label: string; value: number }

/** Counts for a chart. A group hidden for being too small is left out, as its number is not known. */
export const groupBars = (groups: Group[]): Bar[] => groups.filter(g => !g.hidden && g.count !== null).map(g => ({ label: g.label, value: g.count as number }))

export const movementBars = (w: Workforce): Bar[] => [
  { label: "Start", value: w.opening }, { label: "Joiners", value: w.joiners }, { label: "Leavers", value: w.leavers }, { label: "End", value: w.closing },
]

export const leaveBars = (t: TimeReport): Bar[] => t.leave.map(l => ({ label: l.leaveType.replace(/_/g, " ").toLowerCase(), value: l.days }))

export const overtimeBars = (t: TimeReport): Bar[] => t.overtimeByKind.map(l => ({ label: l.label.replace(/_/g, " ").toLowerCase(), value: l.hours ?? 0 }))

export interface RunBar { label: string; gross: number; net: number; employerCost: number }
/** One bar group per finished pay run, oldest first. Runs not counted in the totals are left out so the chart agrees with them. */
export const runBars = (p: PayrollReport): RunBar[] => p.runs.filter(r => r.counted).slice().sort((a, b) => a.payDate.localeCompare(b.payDate))
  .map(r => ({ label: r.number, gross: r.gross, net: r.net, employerCost: r.employerCost }))

export const costBars = (p: PayrollReport): Bar[] => p.byDepartment.map(l => ({ label: l.label, value: l.amount ?? 0 }))

export const checkBars = (c: Compliance): Bar[] => c.checks.filter(k => k.count > 0).map(k => ({ label: k.title, value: k.count }))
