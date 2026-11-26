// Wording and checks for workplace placements and logbooks. The server enforces every rule.
// Hours are totalled and split into confirmed and unconfirmed. No required number of hours exists, and logged hours are not a competency decision.

export type Placement = {
  id: string; placementNo: string; enrolmentId: string; enrolmentNo: string; learnerName: string | null; programmeTitle: string | null; hostName: string; hostContact: string | null
  mentorName: string; mentorContact: string | null; agreementRef: string | null; startDate: string; endDate: string | null; status: string; startedOn: string | null
  endedOn: string | null; cancelReason: string | null; nextStatuses: string[]; confirmedHours: number; unconfirmedHours: number; summary: string
}
export type LogEntry = { id: string; placementId: string; logDate: string; hours: number; activity: string; status: string; voidReason: string | null; confirmedOn: string | null; confirmedByName: string | null }
export type ProgrammeHoursNote = { hours: number; sourceType: string; sourceReference: string; sourceSection: string; effectiveDate: string; verifiedOn: string; note: string }
export type PlacementDetail = { placement: Placement; logs: LogEntry[]; programmeHours?: ProgrammeHoursNote | null }

export const MIN_REASON = 10
export const STATUSES: Record<string, string> = { PLANNED: "Planned", ACTIVE: "Active", COMPLETED: "Completed", CANCELLED: "Cancelled" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const WORKPLACE_NOTE = "Hours here are a record of time spent in the workplace. They are not a competency decision. No required number of hours is assumed: a figure appears only where the programme version records it with its source."

export function statusTone(s: string): "good" | "warn" | "bad" | "neutral" {
  return s === "ACTIVE" ? "good" : s === "PLANNED" ? "warn" : "neutral"
}
export const enrolmentOpen = (status: string) => status === "ENROLLED" || status === "IN_PROGRESS"

const long = (s: string, n: number) => s.trim().length >= n
export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export type PlacementForm = { hostName: string; hostContact: string; mentorName: string; mentorContact: string; agreementRef: string; startDate: string; endDate: string }
export const emptyPlacement: PlacementForm = { hostName: "", hostContact: "", mentorName: "", mentorContact: "", agreementRef: "", startDate: "", endDate: "" }
export const placementForm = (p: Placement): PlacementForm => ({
  hostName: p.hostName, hostContact: p.hostContact ?? "", mentorName: p.mentorName, mentorContact: p.mentorContact ?? "", agreementRef: p.agreementRef ?? "",
  startDate: p.startDate, endDate: p.endDate ?? "",
})
export function placementProblem(f: PlacementForm, enrolmentStart: string | null): string | null {
  if (!long(f.hostName, 2)) return "Give the name of the host workplace"
  if (!long(f.mentorName, 2)) return "Give the name of the workplace mentor"
  if (!f.startDate) return "Enter the planned start day"
  if (f.endDate && f.endDate < f.startDate) return "The end day cannot be before the start day"
  if (enrolmentStart && f.startDate < enrolmentStart) return "The placement cannot start before the enrolment started"
  return null
}
const blank = (s: string) => (s.trim() ? s.trim() : null)
export const placementBody = (f: PlacementForm) => ({
  hostName: f.hostName.trim(), hostContact: blank(f.hostContact), mentorName: f.mentorName.trim(), mentorContact: blank(f.mentorContact), agreementRef: blank(f.agreementRef),
  startDate: f.startDate, endDate: f.endDate || null,
})

export type LogForm = { logDate: string; hours: string; activity: string }
export const emptyLog = (today: string): LogForm => ({ logDate: today, hours: "", activity: "" })
export function logProblem(f: LogForm, startedOn: string | null, endDate: string | null, today: string): string | null {
  if (!f.logDate) return "Enter the day"
  if (f.logDate > today) return "The day cannot be in the future"
  if (startedOn && f.logDate < startedOn) return "The day cannot be before the placement started"
  if (endDate && f.logDate > endDate) return "The day is after the placement's planned end"
  const h = Number(f.hours)
  if (f.hours.trim() === "" || !(h > 0) || h > 24) return "Hours must be more than 0 and no more than 24"
  if (!long(f.activity, 10)) return "Say what was done, in at least 10 characters"
  return null
}
export const logBody = (f: LogForm) => ({ logDate: f.logDate, hours: Number(f.hours), activity: f.activity.trim() })

export function dayProblem(on: string, earliest: string | null, today: string, what: string): string | null {
  if (!on) return `Enter the day ${what}`
  if (on > today) return "The day cannot be in the future"
  if (earliest && on < earliest) return `The day cannot be before ${earliest}`
  return null
}
export function confirmProblem(on: string, logDay: string, today: string, name: string): string | null {
  if (!on) return "Enter the day the mentor confirmed it"
  if (on > today) return "The day cannot be in the future"
  if (on < logDay) return "It cannot be confirmed before the day it records"
  if (!long(name, 2)) return "Give the name of the mentor who confirmed it"
  return null
}

export function logLine(l: Pick<LogEntry, "status" | "confirmedOn" | "confirmedByName" | "voidReason">): string {
  if (l.status === "VOID") return `Voided. Reason: ${l.voidReason ?? "—"}`
  return l.confirmedOn ? `Confirmed by ${l.confirmedByName} on ${l.confirmedOn}` : "Not yet confirmed by the mentor"
}
