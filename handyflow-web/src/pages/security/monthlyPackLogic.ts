// Monthly service report: wording only. The server computes every figure; this words them and handles month arithmetic.
import { printCss } from "./printSection"

export interface PackCover { posts: number; measured: boolean; postDays: number; requiredGuardHours: number; coveredGuardHours: number; coveragePct: number | null; postDaysShort: number; postDaysUncovered: number }
export interface PackPatrols { rounds: number; complete: number; partial: number; missed: number; checkpointsExpected: number; checkpointsScanned: number; checkpointPct: number | null }
export interface Pack {
  siteId: string; siteName: string; month: string; from: string; to: string; inProgress: boolean; contractStatus: string | null
  cover: PackCover; shifts: Record<string, number>; patrols: PackPatrols
  incidents: { total: number; resolved: number; bySeverity: Record<string, number> }
  visitors: { total: number; withVehicle: number; byType: Record<string, number> }
  alarms: number; occurrenceBook: { daysInPeriod: number; daysSigned: number }
}

export const PRINT_CSS = printCss("mp-print", "mp")

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
const pad = (n: number) => String(n).padStart(2, "0")

export function currentMonthSast(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg", year: "numeric", month: "2-digit" }).format(now).slice(0, 7)
}

export function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split("-").map(Number)
  const t = y * 12 + (m - 1) + delta
  return `${Math.floor(t / 12)}-${pad((t % 12) + 1)}`
}

/** Last month, South African time: the month a client report is normally about. */
export function lastMonthSast(now: Date = new Date()): string { return shiftMonth(currentMonthSast(now), -1) }

export function canGoNextMonth(month: string, now: Date = new Date()): boolean { return month < currentMonthSast(now) }

export function monthTitle(month: string): string {
  const [y, m] = month.split("-").map(Number)
  return `${MONTHS[m - 1] ?? month} ${y}`
}

export const SHIFT_LABEL: Record<string, string> = { COMPLETED: "Completed", SCHEDULED: "Scheduled", ACTIVE: "In progress", MISSED: "Missed", CANCELLED: "Cancelled", PULLED: "Pulled early" }
export const SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")

export function coverText(c: PackCover): string {
  if (!c.measured) return "No posts are set up for this site, so cover is not measured."
  if (c.postDays === 0) return "No post-days had finished in this period."
  const pct = c.coveragePct == null ? "" : ` (${c.coveragePct}%)`
  return `${c.coveredGuardHours} of ${c.requiredGuardHours} required guard-hours covered${pct}.`
}

export function coverDetail(c: PackCover): string {
  if (!c.measured || c.postDays === 0) return ""
  const parts = [`${c.postDays} post-days measured`]
  if (c.postDaysShort > 0) parts.push(`${c.postDaysShort} short`)
  if (c.postDaysUncovered > 0) parts.push(`${c.postDaysUncovered} with no one rostered`)
  return parts.join(", ") + "."
}

export function patrolText(p: PackPatrols): string {
  if (p.rounds === 0) return "No patrol rounds were due."
  const pct = p.checkpointPct == null ? "" : ` (${p.checkpointPct}%)`
  return `${p.complete} of ${p.rounds} rounds complete, ${p.partial} partial, ${p.missed} missed. ${p.checkpointsScanned} of ${p.checkpointsExpected} checkpoints scanned${pct}.`
}

export function incidentText(i: Pack["incidents"]): string {
  if (i.total === 0) return "No incidents."
  const sev = SEVERITY_ORDER.filter(s => i.bySeverity[s]).map(s => `${i.bySeverity[s]} ${titleCase(s)}`).join(", ")
  return `${i.total} ${i.total === 1 ? "incident" : "incidents"} (${sev}), ${i.resolved} resolved.`
}

export function visitorText(v: Pack["visitors"]): string {
  if (v.total === 0) return "No visitors or contractors logged."
  const types = Object.keys(v.byType).sort().map(t => `${v.byType[t]} ${titleCase(t)}`).join(", ")
  return `${v.total} logged (${types}), ${v.withVehicle} with a vehicle.`
}

export function bookText(b: Pack["occurrenceBook"]): string { return `Occurrence Book signed off on ${b.daysSigned} of ${b.daysInPeriod} days.` }

export function shiftLines(shifts: Record<string, number>): { label: string; n: number }[] {
  return Object.keys(SHIFT_LABEL).filter(k => shifts[k]).map(k => ({ label: SHIFT_LABEL[k], n: shifts[k] }))
}

export function printedAt(now: Date = new Date()): string {
  return "Printed " + now.toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "Africa/Johannesburg" }) + " (SAST)"
}

export function printTitle(p: Pack): string { return `Monthly service report — ${p.siteName} — ${monthTitle(p.month)}` }
