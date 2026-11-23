import { dayIn, getCompanyZone } from "./companyZone"
// Weekly hours: wording and small rules. The server decides who is flagged; this only words and orders it.

export interface RestBreach { from: string; to: string; hours: number }
export interface DayBreach { day: string; hours: number }
export interface HoursRow {
  guardId: string; guardName: string | null; hours: number; state: "OK" | "NEAR" | "OVER" | "EXCEEDS" | string; shifts: number; overlaps: boolean; restBreaches: RestBreach[]
  /** From the working-time policy that applies to this guard. Older servers do not send them. */
  category?: string; limit?: number; ceiling?: number; dayBreaches?: DayBreach[]
}
export interface HoursThresholds { warnAt: number; limit: number; ceiling: number; minRestHours: number; maxDaily?: number }

/** The limits that apply to this guard: their own category's, else the company's. */
export function rowThresholds(r: HoursRow, t: HoursThresholds): HoursThresholds {
  return { ...t, limit: typeof r.limit === "number" ? r.limit : t.limit, ceiling: typeof r.ceiling === "number" ? r.ceiling : t.ceiling }
}
export interface HoursReport { weekStart: string; guardsRostered: number; flagged: number; thresholds: HoursThresholds; truncated: boolean; rows: HoursRow[] }

export type Tone = "ok" | "warn" | "bad" | "neutral"
export const STATE_TONE: Record<string, Tone> = { OK: "ok", NEAR: "warn", OVER: "bad", EXCEEDS: "bad" }

export function stateLabel(state: string, t: HoursThresholds): string {
  switch (state) {
    case "OK": return "Within limits"
    case "NEAR": return `Near the ${t.limit} h limit`
    case "OVER": return `Over ${t.limit} h`
    case "EXCEEDS": return `Over ${t.ceiling} h`
    default: return state
  }
}

const pad = (n: number) => String(n).padStart(2, "0")
const iso = (d: Date) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`
const parse = (s: string) => { const [y, m, d] = s.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d)) }

/** Today as YYYY-MM-DD in the company's zone (South Africa unless the company has set another; see companyZone.ts). The name is kept for existing callers. */
export function todaySast(now: Date = new Date()): string { return dayIn(getCompanyZone(), now) }

export function addDays(date: string, n: number): string { const d = parse(date); d.setUTCDate(d.getUTCDate() + n); return iso(d) }

/** The Monday of the week containing the day. */
export function mondayOf(date: string): string {
  const d = parse(date)
  const back = (d.getUTCDay() + 6) % 7 // Mon=0 .. Sun=6
  return addDays(date, -back)
}

/** The week after the current one is not offered. */
export function canGoNextWeek(weekStart: string, now: Date = new Date()): boolean { return weekStart < mondayOf(todaySast(now)) }

export function weekTitle(weekStart: string): string {
  const fmt = (s: string) => parse(s).toLocaleDateString("en-ZA", { day: "numeric", month: "short", timeZone: "UTC" })
  return `${fmt(weekStart)} to ${fmt(addDays(weekStart, 6))}`
}

export function restText(b: RestBreach): string {
  const f = (s: string) => new Date(s).toLocaleString("en-ZA", { weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false, timeZone: getCompanyZone() })
  return `Only ${b.hours} h rest (${f(b.from)} to ${f(b.to)})`
}

const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
export function dayText(b: DayBreach): string {
  const [y, m, d] = b.day.split("-").map(Number)
  const when = `${DAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]} ${d} ${MONTHS[m - 1]}`
  return `${b.hours} h rostered on ${when}, over the daily limit`
}

export function problems(r: HoursRow): string[] {
  const out: string[] = []
  if (r.overlaps) out.push("Double booked: two shifts overlap")
  for (const b of r.restBreaches) out.push(restText(b))
  for (const d of Array.isArray(r.dayBreaches) ? r.dayBreaches : []) out.push(dayText(d))
  return out
}

export function summaryText(r: HoursReport): string {
  if (r.guardsRostered === 0) return "No guards are rostered this week."
  if (r.flagged === 0) return `None of the ${r.guardsRostered} rostered guards needs attention.`
  return `${r.flagged} of ${r.guardsRostered} rostered guards ${r.flagged === 1 ? "needs" : "need"} attention.`
}

export function limitsText(t: HoursThresholds): string {
  const daily = typeof t.maxDaily === "number" && t.maxDaily > 0 ? `, ${t.maxDaily} h a day` : ""
  return `Company default: warns from ${t.warnAt} h, ${t.limit} h ordinary limit, ${t.ceiling} h ceiling${daily}, ${t.minRestHours} h rest between shifts. Guards in another category use that category's limits. Based on rostered hours.`
}
