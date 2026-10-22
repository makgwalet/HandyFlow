// The rules behind the HR reports screens: who may open which report, the period, and how a report becomes a CSV file.
import { todaySast } from "./er.logic"

export interface ReportRights { read: boolean; payroll: boolean; employeeRelations: boolean; sensitive: boolean }

export function reportRights(permissions: string[]): ReportRights {
  const has = (...p: string[]) => p.some(x => permissions.includes(x))
  return {
    read: has("HR_REPORT_READ"),
    payroll: has("HR_COMPENSATION_READ", "HR_COMPENSATION_MANAGE", "HR_COMPENSATION_APPROVE"),
    employeeRelations: has("HR_ER_READ", "HR_ER_MANAGE", "HR_ER_DECIDE"),
    sensitive: has("HR_SENSITIVE_READ"),
  }
}

export const MAX_DAYS = 366
export const MIN_GROUP = 5

export interface Period { from: string; to: string }

const iso = (d: Date) => d.toISOString().slice(0, 10)
const utc = (s: string) => new Date(`${s}T00:00:00Z`)

/** The dates a person can pick with one click, worked out from today in South Africa. */
export function presets(now: Date = new Date()): { id: string; label: string; period: Period }[] {
  const today = todaySast(now)
  const t = utc(today)
  const y = t.getUTCFullYear(), m = t.getUTCMonth()
  const q = Math.floor(m / 3) * 3
  const back = new Date(t); back.setUTCDate(back.getUTCDate() - 364)
  return [
    { id: "month", label: "This month", period: { from: iso(new Date(Date.UTC(y, m, 1))), to: today } },
    { id: "lastmonth", label: "Last month", period: { from: iso(new Date(Date.UTC(y, m - 1, 1))), to: iso(new Date(Date.UTC(y, m, 0))) } },
    { id: "quarter", label: "This quarter", period: { from: iso(new Date(Date.UTC(y, q, 1))), to: today } },
    { id: "year", label: "Year to date", period: { from: iso(new Date(Date.UTC(y, 0, 1))), to: today } },
    { id: "12m", label: "Last 12 months", period: { from: iso(back), to: today } },
  ]
}

export const defaultPeriod = (now: Date = new Date()): Period => presets(now)[3].period

export function periodProblem(p: Period): string | null {
  if (!p.from || !p.to) return "Choose the start and end of the period."
  if (p.to < p.from) return "The end of the period is before the start."
  const days = Math.round((utc(p.to).getTime() - utc(p.from).getTime()) / 86400000) + 1
  return days > MAX_DAYS ? `Choose a period of ${MAX_DAYS} days or less.` : null
}

export interface Group { label: string; count: number | null; hidden: boolean }

/** A count for a screen. A hidden group says why it is hidden rather than showing a blank. */
export const groupText = (g: Group) => (g.hidden || g.count === null ? `fewer than ${MIN_GROUP}` : String(g.count))

export function rand(v: number | null | undefined): string {
  if (v === null || v === undefined) return "–"
  return new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(v)
}

export const pct = (v: number | null | undefined) => (v === null || v === undefined ? "–" : `${v.toFixed(1)}%`)

export type Cell = string | number | null | undefined

/**
 * Rows as CSV text. Cells that start with = + - @ or a tab or return are prefixed with an apostrophe so a spreadsheet does not run them as a
 * formula. A number is written as it is, and a negative number is not a formula.
 */
export function toCsv(rows: Cell[][]): string {
  const cell = (c: Cell) => {
    if (c === null || c === undefined) return ""
    if (typeof c === "number") return String(c)
    const t = /^[=+\-@\t\r]/.test(c) ? `'${c}` : c
    return /[",\n\r]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t
  }
  return rows.map(r => r.map(cell).join(",")).join("\r\n") + "\r\n"
}

export const reportFileName = (kind: string, p: Period) => `hr-${kind}-${p.from}-to-${p.to}.csv`
