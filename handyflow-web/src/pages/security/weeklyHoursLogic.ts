// Weekly hours: wording and small rules. The server decides who is flagged; this only words and orders it.

export interface RestBreach { from: string; to: string; hours: number }
export interface HoursRow { guardId: string; guardName: string | null; hours: number; state: "OK" | "NEAR" | "OVER" | "EXCEEDS" | string; shifts: number; overlaps: boolean; restBreaches: RestBreach[] }
export interface HoursThresholds { warnAt: number; limit: number; ceiling: number; minRestHours: number }
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

/** Today in South Africa as YYYY-MM-DD. */
export function todaySast(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg", year: "numeric", month: "2-digit", day: "2-digit" }).format(now)
}

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
  const f = (s: string) => new Date(s).toLocaleString("en-ZA", { weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "Africa/Johannesburg" })
  return `Only ${b.hours} h rest (${f(b.from)} to ${f(b.to)})`
}

export function problems(r: HoursRow): string[] {
  const out: string[] = []
  if (r.overlaps) out.push("Double booked: two shifts overlap")
  for (const b of r.restBreaches) out.push(restText(b))
  return out
}

export function summaryText(r: HoursReport): string {
  if (r.guardsRostered === 0) return "No guards are rostered this week."
  if (r.flagged === 0) return `None of the ${r.guardsRostered} rostered guards needs attention.`
  return `${r.flagged} of ${r.guardsRostered} rostered guards ${r.flagged === 1 ? "needs" : "need"} attention.`
}

export function limitsText(t: HoursThresholds): string {
  return `Warns from ${t.warnAt} h, ${t.limit} h ordinary limit, ${t.ceiling} h ceiling, ${t.minRestHours} h rest between shifts. Based on rostered hours.`
}
