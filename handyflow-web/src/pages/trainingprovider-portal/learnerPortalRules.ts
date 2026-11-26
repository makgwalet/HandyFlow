// Rules and wording for the learner and employer portals. The portals show what the provider has recorded: attendance as counts, a completion outcome
// in plain words, never a competence result.

export type AttendanceCounts = { recorded: number; present: number; late: number; absent: number; excused: number }
export type EnrolmentView = {
  enrolmentNo: string; programmeCode: string | null; programmeTitle: string | null; status: string; startDate: string | null; expectedEndDate: string | null
  cohortName: string | null; attendance: AttendanceCounts; outcome: string; outcomeOn: string | null
}
export type SessionView = { cohortName: string | null; title: string | null; date: string; startTime: string; endTime: string }
export type LearnerMe = { learnerNo: string; fullName: string; note: string; enrolments: EnrolmentView[]; upcoming: SessionView[] }
export type EmployerRow = { learnerNo: string; fullName: string; enrolment: EnrolmentView }
export type EmployerLearners = { note: string; rows: EmployerRow[]; hiddenForConsent: number }

export const MIN_PASSWORD = 8
export const MIN_REASON = 10

export function passwordProblem(password: string, confirm: string): string | null {
  if (password.length < MIN_PASSWORD) return `Use at least ${MIN_PASSWORD} characters for the password`
  if (password !== confirm) return "The two passwords are not the same"
  return null
}

export function attendanceText(a: AttendanceCounts): string {
  if (a.recorded === 0) return "No attendance recorded yet"
  return `${a.recorded} session${a.recorded === 1 ? "" : "s"} recorded: ${a.present} present, ${a.late} late, ${a.absent} absent, ${a.excused} excused`
}

export function statusText(status: string): string {
  return status.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase())
}

export function periodText(start: string | null, end: string | null): string {
  if (start && end) return `${start} to ${end}`
  if (start) return `From ${start}`
  return "Dates not set"
}

export function hiddenText(n: number): string {
  if (n <= 0) return ""
  return `${n} enrolment${n === 1 ? " is" : "s are"} not shown because the learner's consent has not been recorded. Ask the training provider.`
}

export const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
export function inviteProblem(email: string, reason: string): string | null {
  if (!EMAIL.test(email.trim())) return "Enter a valid email address for the learner"
  if (reason.trim().length < MIN_REASON) return `Say why the learner is being given access, in at least ${MIN_REASON} characters`
  return null
}
export function endProblem(reason: string): string | null {
  return reason.trim().length >= MIN_REASON ? null : `Say why the access is ending, in at least ${MIN_REASON} characters`
}
export const grantText = (s: string): string => (s === "PENDING" ? "Invited, not yet accepted" : s === "ACCEPTED" ? "Has access" : "Ended")
