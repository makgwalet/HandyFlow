// Wording and checks for cohorts, venues, the timetable and attendance. The server enforces every rule; these save a round trip.
// Attendance is a record of who was there. Nothing here says a learner is competent or has completed.

export type Venue = { id: string; siteId: string; siteName: string; name: string; capacity: number | null; notes: string | null; status: string; inactiveReason: string | null }
export type Cohort = {
  id: string; cohortNo: string; name: string; programmeId: string; programmeTitle: string; programmeVersionNo: number | null; siteId: string; siteName: string
  startDate: string; endDate: string | null; capacity: number | null; notes: string | null; status: string; statusReason: string | null
  members: number; facilitators: number; nextStatuses: string[]
}
export type Member = { id: string; enrolmentId: string; enrolmentNo: string; learnerId: string | null; learnerName: string; enrolmentStatus: string; status: string; removedReason: string | null; addedAt: string }
export type CohortFacilitator = { id: string; practitionerId: string; practitionerNo: string; name: string; appointmentId: string; standing: string; standingNote: string; status: string; removedReason: string | null; sitePermission?: string; sitePermissionNote?: string }
export type CohortEvent = { id: string; action: string; note: string | null; actedBy: string; actedAt: string }
export type CohortDetail = { cohort: Cohort; members: Member[]; facilitators: CohortFacilitator[]; history: CohortEvent[] }
export type Slot = {
  id: string; cohortId: string; cohortName: string; slotDate: string; startTime: string; endTime: string; title: string; moduleId: string | null; moduleTitle: string | null
  venueId: string | null; venueName: string | null; practitionerId: string | null; facilitatorName: string | null; facilitatorStanding: string | null
  facilitatorStandingNote: string | null; status: string; cancelledReason: string | null
}
export type RegisterRow = { memberId: string; enrolmentId: string; enrolmentNo: string; learnerId: string | null; learnerName: string; attendanceId: string | null; status: string | null; note: string | null; corrections: number }
export type Register = { slotId: string; cohortId: string; cohortName: string; title: string; slotDate: string; startTime: string; endTime: string; slotStatus: string; canRecord: boolean; cannotRecordBecause: string | null; rows: RegisterRow[] }
export type SummaryRow = { memberId: string; enrolmentId: string; enrolmentNo: string; learnerId: string | null; learnerName: string; expected: number; attended: number; absent: number; excused: number; unrecorded: number; ratePercent: number | null }
export type CohortSummary = { cohortId: string; note: string; rows: SummaryRow[] }
export type LearnerCohortRow = {
  memberId: string; cohortId: string; cohortNo: string; cohortName: string; programmeTitle: string; cohortStatus: string; memberStatus: string; removedReason: string | null
  enrolmentNo: string; startDate: string; endDate: string | null; expected: number; attended: number; absent: number; excused: number; unrecorded: number; ratePercent: number | null
}
export type LearnerCohorts = { note: string; rows: LearnerCohortRow[] }
export type HistoryLine = { id: string; action: string; fromStatus: string | null; toStatus: string; reason: string | null; actedBy: string; actedAt: string }

export const MIN_REASON = 10
export const COHORT_STATUS: Record<string, string> = { PLANNED: "Planned", RUNNING: "Running", COMPLETED: "Completed", CANCELLED: "Cancelled" }
export const ATTENDANCE: Record<string, string> = { PRESENT: "Present", LATE: "Late", ABSENT: "Absent", EXCUSED: "Excused" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

export function cohortTone(s: string): "good" | "warn" | "bad" | "neutral" {
  if (s === "RUNNING") return "good"
  if (s === "PLANNED") return "warn"
  return "neutral"
}
export function attendanceTone(s: string | null): "good" | "warn" | "bad" | "neutral" {
  if (s === "PRESENT") return "good"
  if (s === "LATE" || s === "EXCUSED") return "warn"
  if (s === "ABSENT") return "bad"
  return "neutral"
}
export function standingWarning(standing: string | null): string | null {
  return standing && standing !== "VERIFIED_IN_FORCE" ? "This facilitator is not checked and in force on this date" : null
}

const long = (s: string, n: number) => s.trim().length >= n
export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)
const blank = (s: string) => (s.trim() ? s.trim() : null)
const intOrNull = (s: string): number | null => (s.trim() === "" ? null : Number(s))

export type CohortForm = { name: string; programmeId: string; siteId: string; startDate: string; endDate: string; capacity: string; notes: string }
export const emptyCohort: CohortForm = { name: "", programmeId: "", siteId: "", startDate: "", endDate: "", capacity: "", notes: "" }
export function cohortProblem(f: CohortForm, creating: boolean): string | null {
  if (f.name.trim().length < 2) return "Give the cohort a name"
  if (creating && !f.programmeId) return "Choose the programme"
  if (creating && !f.siteId) return "Choose the site"
  if (!f.startDate) return "Enter the start date"
  if (f.endDate && f.endDate < f.startDate) return "The end date cannot be before the start date"
  if (f.capacity.trim() !== "" && (!Number.isInteger(Number(f.capacity)) || Number(f.capacity) < 1)) return "Capacity must be a whole number, at least 1"
  return null
}
export const cohortBody = (f: CohortForm) => ({
  name: f.name.trim(), programmeId: f.programmeId, siteId: f.siteId, startDate: f.startDate, endDate: f.endDate || null, capacity: intOrNull(f.capacity), notes: blank(f.notes),
})

export type VenueForm = { siteId: string; name: string; capacity: string; notes: string }
export const emptyVenue: VenueForm = { siteId: "", name: "", capacity: "", notes: "" }
export function venueProblem(f: VenueForm): string | null {
  if (!f.siteId) return "Choose the site"
  if (f.name.trim().length < 2) return "Give the venue a name"
  if (f.capacity.trim() !== "" && (!Number.isInteger(Number(f.capacity)) || Number(f.capacity) < 1)) return "Capacity must be a whole number, at least 1"
  return null
}
export const venueBody = (f: VenueForm) => ({ siteId: f.siteId, name: f.name.trim(), capacity: intOrNull(f.capacity), notes: blank(f.notes) })

export type SlotForm = { title: string; slotDate: string; startTime: string; endTime: string; moduleId: string; venueId: string; practitionerId: string }
export const emptySlot: SlotForm = { title: "", slotDate: "", startTime: "09:00", endTime: "11:00", moduleId: "", venueId: "", practitionerId: "" }
export function slotForm(s: Slot): SlotForm {
  return { title: s.title, slotDate: s.slotDate, startTime: s.startTime.slice(0, 5), endTime: s.endTime.slice(0, 5), moduleId: s.moduleId ?? "", venueId: s.venueId ?? "", practitionerId: s.practitionerId ?? "" }
}
export function slotProblem(f: SlotForm, cohort: Pick<Cohort, "startDate" | "endDate">): string | null {
  if (f.title.trim().length < 2) return "Give the slot a title"
  if (!f.slotDate || !f.startTime || !f.endTime) return "Enter the date, start time and end time"
  if (f.endTime <= f.startTime) return "The end time must be after the start time"
  if (f.slotDate < cohort.startDate) return `The slot is before the cohort starts on ${cohort.startDate}`
  if (cohort.endDate && f.slotDate > cohort.endDate) return `The slot is after the cohort ends on ${cohort.endDate}`
  return null
}
export const slotBody = (f: SlotForm) => ({
  title: f.title.trim(), slotDate: f.slotDate, startTime: f.startTime, endTime: f.endTime, moduleId: f.moduleId || null, venueId: f.venueId || null, practitionerId: f.practitionerId || null,
})

export type TransitionForm = { toStatus: string; reason: string }
export function transitionProblem(f: TransitionForm, allowed: string[]): string | null {
  if (!allowed.includes(f.toStatus)) return "Choose what happens to the cohort"
  if (f.toStatus === "CANCELLED") return reasonProblem(f.reason)
  return null
}
export const transitionBody = (f: TransitionForm) => ({ toStatus: f.toStatus, reason: f.toStatus === "CANCELLED" ? f.reason.trim() : null })

/** Entries of a register that the person has marked. Rows left unmarked are not sent. */
export type Marks = Record<string, { status: string; note: string }>
export function registerProblem(marks: Marks): string | null {
  const entries = Object.entries(marks)
  if (entries.length === 0) return "Mark at least one learner"
  for (const [, m] of entries) {
    if (m.status === "EXCUSED" && !long(m.note, MIN_REASON)) return `Say why a learner is excused, in at least ${MIN_REASON} characters`
  }
  return null
}
export const registerBody = (marks: Marks) => ({
  entries: Object.entries(marks).map(([memberId, m]) => ({ memberId, status: m.status, note: blank(m.note) })),
})

export type CorrectionForm = { status: string; note: string; reason: string }
export function correctionProblem(from: { status: string | null; note: string | null }, f: CorrectionForm): string | null {
  if (!ATTENDANCE[f.status]) return "Choose present, late, absent or excused"
  if (f.status === from.status && (from.note ?? "").trim() === f.note.trim()) return "Nothing has changed"
  if (f.status === "EXCUSED" && !long(f.note, MIN_REASON)) return `Say why the learner is excused, in at least ${MIN_REASON} characters`
  return reasonProblem(f.reason)
}
export const correctionBody = (f: CorrectionForm) => ({ status: f.status, note: blank(f.note), reason: f.reason.trim() })

/** Plain statement shown with every attendance count, so a rate is never read as a result. */
export const ATTENDANCE_NOTE = "Counts of what was recorded. The rate leaves out excused and unrecorded sessions. It is not a result and does not say a learner is competent."
export function rateText(r: Pick<SummaryRow, "ratePercent">): string {
  return r.ratePercent === null ? "no rate yet" : `${r.ratePercent}% of recorded sessions`
}

/** One line for a learner's cohort: where they are, and counts of what was recorded. Never a result or a completion. */
export function learnerCohortLine(r: LearnerCohortRow): string {
  const where = r.memberStatus === "ACTIVE" ? "In the cohort" : `Left the cohort${r.removedReason ? `: ${r.removedReason}` : ""}`
  const counts = `${r.attended} attended, ${r.absent} absent, ${r.excused} excused, ${r.unrecorded} not recorded of ${r.expected} sessions held`
  return `${where}. ${counts}, ${rateText(r)}.`
}
