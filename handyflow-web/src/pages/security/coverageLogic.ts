// Coverage board: wording and small rules. The server decides what is short; this only words and checks input.
import { getCompanyZone } from "./companyZone"

export interface CoverageGap { start: string; end: string; have: number; need: number }
export interface CoverageRow {
  siteId: string; siteName: string; postId: string; postName: string; day: string; start: string; end: string
  needed: number; state: "COVERED" | "SHORT" | "UNCOVERED" | string; gaps: CoverageGap[]
}
export interface CoverageBoard { from: string; days: number; postCount: number; shortCount: number; uncoveredCount: number; rows: CoverageRow[] }
export interface SitePost { id: string; siteId: string; name: string; weekdays: number[]; start: string; end: string; minGuards: number }

export const STATE_LABEL: Record<string, string> = { COVERED: "Covered", SHORT: "Short", UNCOVERED: "No one rostered" }
export const STATE_TONE: Record<string, "ok" | "warn" | "bad" | "neutral"> = { COVERED: "ok", SHORT: "warn", UNCOVERED: "bad" }
export const WEEKDAYS = [{ n: 1, label: "Mon" }, { n: 2, label: "Tue" }, { n: 3, label: "Wed" }, { n: 4, label: "Thu" }, { n: 5, label: "Fri" }, { n: 6, label: "Sat" }, { n: 7, label: "Sun" }]
export const DAY_CHOICES = [3, 7, 14]

export function stateLabel(s: string): string { return STATE_LABEL[s] ?? s }

export const hhmm = (iso: string): string =>
  new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: getCompanyZone() })

/** "06:00-18:00" in South African time. */
export function rangeText(startIso: string, endIso: string): string { return `${hhmm(startIso)}–${hhmm(endIso)}` }

export function gapText(g: CoverageGap): string {
  return `${rangeText(g.start, g.end)}: ${g.have} of ${g.need} guards`
}

export function dayLabel(date: string): string {
  const [y, m, d] = date.split("-").map(Number)
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" })
}

export function summaryText(b: CoverageBoard): string {
  if (b.postCount === 0) return "No posts are set up yet, so there is nothing to measure. Add what each site needs below."
  const bad = b.shortCount + b.uncoveredCount
  if (bad === 0) return `Every post is covered for the next ${b.days} days.`
  return `${bad} ${bad === 1 ? "post-day is" : "post-days are"} short over the next ${b.days} days` + (b.uncoveredCount > 0 ? `, ${b.uncoveredCount} with no one rostered.` : ".")
}

/** "Mon-Fri", "Every day", "Mon, Wed, Sat". */
export function weekdaysText(days: number[]): string {
  const d = [...new Set(days)].sort((a, b) => a - b)
  if (d.length === 7) return "Every day"
  const label = (n: number) => WEEKDAYS.find(w => w.n === n)?.label ?? String(n)
  if (d.length >= 3 && d.every((v, i) => i === 0 || v === d[i - 1] + 1)) return `${label(d[0])}–${label(d[d.length - 1])}`
  return d.map(label).join(", ")
}

export const timeOnly = (t: string): string => t.slice(0, 5)

/** A post runs past midnight when its end is not later than its start. */
export function runsPastMidnight(start: string, end: string): boolean { return timeOnly(end) <= timeOnly(start) }

export function postProblem(name: string, days: number[], start: string, end: string, guards: string): string | null {
  const n = name.trim()
  if (!n) return "Give the post a name."
  if (n.length > 80) return "Keep the name under 80 characters."
  if (days.length === 0) return "Pick at least one weekday."
  if (!start || !end) return "Set a start and an end time."
  const g = Number(guards)
  if (!Number.isInteger(g) || g < 1 || g > 50) return "Guards needed must be a whole number from 1 to 50."
  return null
}

export function postBody(name: string, days: number[], start: string, end: string, guards: string) {
  return { name: name.trim(), weekdays: [...new Set(days)].sort((a, b) => a - b), start: timeOnly(start), end: timeOnly(end), minGuards: Number(guards) }
}

export function toggleDay(days: number[], n: number): number[] {
  return days.includes(n) ? days.filter(d => d !== n) : [...days, n].sort((a, b) => a - b)
}
