// src/pages/security/schedule.logic.ts
//
// Pure rules for the scheduler grid: the week and its days (South African time, UTC+2, no daylight saving), grouping
// shifts into guard-by-day cells, and the conflicts shown on them. No React, no network.
//
// Conflicts describe the schedule; they never change it. The server still refuses an overlapping shift, an inactive
// guard and an expired PSiRA registration when a shift is created. These flags also catch what was already scheduled
// before a guard's status or registration changed.
import type { Tone } from "./guard360.logic"

export interface GridShift { id: string; siteId: string; guardId: string; startAt: string; endAt: string; status: string; notes?: string | null }
export interface GridGuard { id: string; firstName: string; lastName: string; status: string; psiraExpiryDate: string | null }

export const SAST_OFFSET_HOURS = 2
export const MIN_REST_HOURS = 8
export const WEEKLY_HOURS_FLAG = 45
const HOUR = 3_600_000, DAY = 86_400_000

/** Statuses that occupy the guard's time. Cancelled, pulled and missed shifts do not. */
export const COUNTED = new Set(["SCHEDULED", "ACTIVE", "COMPLETED"])

const sast = (d: Date) => new Date(d.getTime() + SAST_OFFSET_HOURS * HOUR)
export const dayKey = (d: Date | string): string => sast(typeof d === "string" ? new Date(d) : d).toISOString().slice(0, 10)
export const addDays = (key: string, n: number): string => new Date(Date.parse(`${key}T00:00:00Z`) + n * DAY).toISOString().slice(0, 10)
/** The instant a South African calendar day begins. */
export const dayStart = (key: string): Date => new Date(Date.parse(`${key}T00:00:00+02:00`))

/** Monday of the week containing `d`, as a day key. */
export function mondayOf(d: Date): string {
  const k = dayKey(d)
  const dow = new Date(`${k}T00:00:00Z`).getUTCDay() // 0 Sunday
  return addDays(k, -((dow + 6) % 7))
}
export const weekDays = (monday: string): string[] => Array.from({ length: 7 }, (_, i) => addDays(monday, i))

/** The window to ask the server for: the week plus the day before, so a night shift that began the evening before still shows. */
export function fetchWindow(monday: string): { from: Date; to: Date } {
  return { from: dayStart(addDays(monday, -1)), to: dayStart(addDays(monday, 7)) }
}

export type ConflictKind = "OVERLAP" | "SHORT_REST" | "PSIRA_EXPIRED" | "GUARD_NOT_ACTIVE"
export interface Conflict { kind: ConflictKind; tone: Tone; text: string }

export const CONFLICT_TONE: Record<ConflictKind, Tone> = { OVERLAP: "bad", PSIRA_EXPIRED: "bad", SHORT_REST: "warn", GUARD_NOT_ACTIVE: "warn" }

const hrs = (ms: number) => Math.round(ms / HOUR * 10) / 10
export const guardName = (g: { firstName: string; lastName: string }) => `${g.firstName} ${g.lastName}`.trim()

/** Conflicts keyed by shift id. Overlap and short rest are reported on the later shift of the pair. */
export function detectConflicts(shifts: GridShift[], guards: GridGuard[]): Map<string, Conflict[]> {
  const out = new Map<string, Conflict[]>()
  const add = (id: string, c: Conflict) => out.set(id, [...(out.get(id) ?? []), c])
  const byGuard = new Map<string, GridShift[]>()
  for (const s of shifts) if (COUNTED.has(s.status)) byGuard.set(s.guardId, [...(byGuard.get(s.guardId) ?? []), s])

  for (const list of byGuard.values()) {
    list.sort((a, b) => Date.parse(a.startAt) - Date.parse(b.startAt))
    for (let i = 1; i < list.length; i++) {
      // Compare with the latest-ending earlier shift, so one long shift overlapping two later ones is caught for both.
      const prev = list.slice(0, i).reduce((m, s) => Date.parse(s.endAt) > Date.parse(m.endAt) ? s : m)
      const gap = Date.parse(list[i].startAt) - Date.parse(prev.endAt)
      if (gap < 0) add(list[i].id, { kind: "OVERLAP", tone: "bad", text: "Overlaps another shift for this guard" })
      else if (gap < MIN_REST_HOURS * HOUR) add(list[i].id, { kind: "SHORT_REST", tone: "warn", text: `Only ${hrs(gap)} h rest since the previous shift (under ${MIN_REST_HOURS} h)` })
    }
  }

  const gmap = new Map(guards.map(g => [g.id, g]))
  for (const s of shifts) {
    if (s.status !== "SCHEDULED") continue
    const g = gmap.get(s.guardId)
    if (!g) continue
    if (g.psiraExpiryDate && dayKey(s.startAt) > g.psiraExpiryDate) add(s.id, { kind: "PSIRA_EXPIRED", tone: "bad", text: `PSiRA registration expires ${g.psiraExpiryDate}, before this shift` })
    if (g.status !== "ACTIVE") add(s.id, { kind: "GUARD_NOT_ACTIVE", tone: "warn", text: `Guard is ${g.status.toLowerCase().replace(/_/g, " ")}` })
  }
  return out
}

/** Scheduled hours per guard for shifts that start in the given days. Counted statuses only. */
export function weeklyHours(shifts: GridShift[], days: string[]): Map<string, number> {
  const set = new Set(days), out = new Map<string, number>()
  for (const s of shifts) {
    if (!COUNTED.has(s.status) || !set.has(dayKey(s.startAt))) continue
    out.set(s.guardId, (out.get(s.guardId) ?? 0) + (Date.parse(s.endAt) - Date.parse(s.startAt)) / HOUR)
  }
  for (const [k, v] of out) out.set(k, hrs(v * HOUR))
  return out
}
export const overHours = (h: number | undefined) => (h ?? 0) > WEEKLY_HOURS_FLAG

/** Cells: guardId -> dayKey -> shifts starting that day, in time order. Cancelled shifts are left out of the grid. */
export function buildCells(shifts: GridShift[], days: string[]): Map<string, Map<string, GridShift[]>> {
  const set = new Set(days), out = new Map<string, Map<string, GridShift[]>>()
  for (const s of [...shifts].sort((a, b) => Date.parse(a.startAt) - Date.parse(b.startAt))) {
    if (s.status === "CANCELLED") continue
    const k = dayKey(s.startAt)
    if (!set.has(k)) continue
    const row = out.get(s.guardId) ?? new Map<string, GridShift[]>()
    row.set(k, [...(row.get(k) ?? []), s]); out.set(s.guardId, row)
  }
  return out
}

/** Rows to show: every guard who is schedulable or has a shift this week, optionally filtered by site and name. */
export function gridGuards(guards: GridGuard[], cells: Map<string, Map<string, GridShift[]>>, f: { search: string; siteId: string }): GridGuard[] {
  const q = f.search.trim().toLowerCase()
  return guards
    .filter(g => g.status === "ACTIVE" || cells.has(g.id))
    .filter(g => !q || guardName(g).toLowerCase().includes(q))
    .filter(g => !f.siteId || [...(cells.get(g.id)?.values() ?? [])].some(list => list.some(s => s.siteId === f.siteId)))
    .sort((a, b) => guardName(a).localeCompare(guardName(b)))
}

/** An ISO instant from a day key and a clock time ("HH:mm") in South African time; the end rolls to the next day for a night shift. */
export function toInstants(day: string, start: string, end: string): { startAt: string; endAt: string } | null {
  if (!/^\d{2}:\d{2}$/.test(start) || !/^\d{2}:\d{2}$/.test(end) || start === end) return null
  const s = new Date(`${day}T${start}:00+02:00`)
  let e = new Date(`${day}T${end}:00+02:00`)
  if (e <= s) e = new Date(e.getTime() + DAY)
  if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime())) return null
  return { startAt: s.toISOString(), endAt: e.toISOString() }
}

export function weekLabel(monday: string): string {
  const f = (k: string) => new Date(`${k}T00:00:00Z`).toLocaleDateString("en-ZA", { day: "numeric", month: "short", timeZone: "UTC" })
  return `${f(monday)} to ${f(addDays(monday, 6))}`
}
export const clock = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })
