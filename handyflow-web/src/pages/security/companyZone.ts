// The company's time zone for the web. The server decides days, weeks and months in this zone (tenants.time_zone); the pages must agree with it,
// so "today", "this month" and clock times are read in the same zone, never the browser's and never a fixed one.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export const DEFAULT_COMPANY_ZONE = "Africa/Johannesburg"

let current = DEFAULT_COMPANY_ZONE

/** True when the browser knows the zone. */
export function usableZone(id: unknown): id is string {
  if (typeof id !== "string" || id.trim() === "" || id.length > 60) return false
  try { new Intl.DateTimeFormat("en", { timeZone: id }); return true } catch { return false }
}

/** The zone pages should use now: the company's once it is known, South Africa before that or if it cannot be loaded. */
export function getCompanyZone(): string { return current }

export function setCompanyZone(id: unknown): void { current = usableZone(id) ? id : DEFAULT_COMPANY_ZONE }

/** The date in the zone as YYYY-MM-DD. */
export function dayIn(zone: string, now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now)
}

/** The month in the zone as YYYY-MM. */
export function monthIn(zone: string, now: Date = new Date()): string { return dayIn(zone, now).slice(0, 7) }

/** A short name for the zone at that moment: SAST, CAT, UTC, or GMT+13 where the browser has no short name. */
export function abbreviationIn(zone: string, now: Date = new Date()): string {
  const part = new Intl.DateTimeFormat("en-ZA", { timeZone: zone, timeZoneName: "short" }).formatToParts(now).find(p => p.type === "timeZoneName")
  return part?.value ?? zone
}

/** How far the zone's clock is ahead of UTC at that moment, in milliseconds. */
export function offsetMs(zone: string, at: number): number {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
  }).formatToParts(new Date(at))
  const n = (t: string) => Number(parts.find(p => p.type === t)?.value)
  return Date.UTC(n("year"), n("month") - 1, n("day"), n("hour"), n("minute"), n("second")) - Math.floor(at / 1000) * 1000
}

/**
 * The instant at which the clock in the zone reads `day` (YYYY-MM-DD) at `time` (HH:mm). Correct across clock changes; a time that does not
 * exist (the hour skipped in spring) or exists twice (autumn) resolves to one of its two readings.
 */
export function instantOf(zone: string, day: string, time: string = "00:00"): Date {
  const [y, m, d] = day.split("-").map(Number)
  const [hh, mm] = time.split(":").map(Number)
  const wall = Date.UTC(y, m - 1, d, hh, mm)
  let t = wall - offsetMs(zone, wall)
  const again = offsetMs(zone, t)
  if (wall - again !== t) t = wall - again
  return new Date(t)
}

/** Date and time in the zone, e.g. "22 Nov 2026, 19:40". */
export function dateTimeIn(zone: string, iso: string | Date, withYear = true): string {
  return new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", ...(withYear ? { year: "numeric" as const } : {}), hour: "2-digit", minute: "2-digit", hour12: false, timeZone: zone })
}

export function timeIn(zone: string, iso: string | Date): string {
  return new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: zone })
}

/**
 * Loads the company's zone once (shared with the zone card, 5 minutes fresh), remembers it for the date helpers, and says when it is known.
 * `ready` becomes true when the answer, or a failure, has arrived, so a page can wait before it asks the server for "today".
 */
export function useCompanyZone(): { zone: string; ready: boolean } {
  const q = useQuery({
    queryKey: ["company-time-zone"], staleTime: 5 * 60_000,
    queryFn: async () => (await apiClient.get("/api/v1/security/company-time-zone")).data?.data,
  })
  const id = (q.data as { timeZone?: unknown } | undefined)?.timeZone
  setCompanyZone(usableZone(id) ? id : DEFAULT_COMPANY_ZONE)
  return { zone: getCompanyZone(), ready: !q.isPending }
}
