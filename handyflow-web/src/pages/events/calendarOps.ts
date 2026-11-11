// src/pages/events/calendarOps.ts — pure month-grid helpers. Dates are handled as "YYYY-MM-DD" strings taken straight from the server's local date-times,
// so no time-zone shift can move an event to a neighbouring day.
export interface CalEvent { id: string; title: string; status: string; eventType?: string | null; venueName?: string | null; startDatetime: string; endDatetime?: string | null }
export interface Cell { date: string; inMonth: boolean; isToday: boolean }

const pad = (n: number) => String(n).padStart(2, "0")
export const ymd = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`   // m is 0-based
export const dayOf = (dt: string) => dt.slice(0, 10)
export const timeOf = (dt: string) => dt.slice(11, 16)

/** Six weeks starting on Monday that contain the month, so the grid never changes height. */
export function monthGrid(year: number, month: number, today: string): Cell[] {
  const first = new Date(Date.UTC(year, month, 1))
  const lead = (first.getUTCDay() + 6) % 7                       // Monday = 0
  const cells: Cell[] = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(Date.UTC(year, month, 1 - lead + i))
    const date = ymd(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())
    cells.push({ date, inMonth: d.getUTCMonth() === month, isToday: date === today })
  }
  return cells
}

export const shiftMonth = (year: number, month: number, by: number) => { const d = new Date(Date.UTC(year, month + by, 1)); return { year: d.getUTCFullYear(), month: d.getUTCMonth() } }
export const monthTitle = (year: number, month: number) => new Date(Date.UTC(year, month, 1)).toLocaleDateString("en-ZA", { month: "long", year: "numeric", timeZone: "UTC" })

/** Every day an event covers, so a three-day festival shows on all three. An end before the start counts as a one-day event. */
export function daysCovered(e: CalEvent): string[] {
  const s = dayOf(e.startDatetime), en = e.endDatetime ? dayOf(e.endDatetime) : s
  const last = en < s ? s : en
  const out: string[] = []
  let [y, m, d] = s.split("-").map(Number)
  for (let guard = 0; guard < 62; guard++) {
    const cur = ymd(y, m - 1, d); out.push(cur)
    if (cur >= last) break
    const n = new Date(Date.UTC(y, m - 1, d + 1)); y = n.getUTCFullYear(); m = n.getUTCMonth() + 1; d = n.getUTCDate()
  }
  return out
}

export function byDay(events: CalEvent[], hideCancelled: boolean): Record<string, CalEvent[]> {
  const map: Record<string, CalEvent[]> = {}
  for (const e of events) {
    if (hideCancelled && e.status === "CANCELLED") continue
    for (const day of daysCovered(e)) (map[day] ??= []).push(e)
  }
  for (const k of Object.keys(map)) map[k].sort((a, b) => a.startDatetime.localeCompare(b.startDatetime) || a.title.localeCompare(b.title))
  return map
}

export const monthCount = (map: Record<string, CalEvent[]>, year: number, month: number) => new Set(Object.entries(map).filter(([d]) => d.startsWith(`${year}-${pad(month + 1)}`)).flatMap(([, v]) => v.map(e => e.id))).size
export const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
