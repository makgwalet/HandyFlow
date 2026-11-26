// Wording and checks for practitioners (facilitators, assessors, moderators) and their appointments. The server enforces every rule.
// A credential reference is shown as typed. Nothing here says a person is registered or competent: a person records the check.

export type Practitioner = {
  id: string; practitionerNo: string; firstNames: string; surname: string; email: string | null; phone: string | null; employmentType: string
  userId: string | null; notes: string | null; status: string; inactiveReason: string | null; liveAppointments: number; verifiedRoles: string[]
}
export type AppointmentEvent = { id: string; action: string; note: string | null; actedBy: string | null; actedAt: string }
export type Appointment = {
  id: string; practitionerId: string; role: string; programmeId: string; programmeTitle: string; authorityId: string | null; authorityName: string | null
  credentialRef: string | null; credentialNote: string | null; validFrom: string | null; validTo: string | null; verificationSource: string | null
  verifiedOn: string | null; status: string; withdrawnReason: string | null; standing: string; standingNote: string; history: AppointmentEvent[]
}
export type PractitionerDetail = { practitioner: Practitioner; appointments: Appointment[] }
export type Eligible = { practitionerId: string; practitionerNo: string; name: string; role: string; appointmentId: string; verifiedOn: string; hasLogin?: boolean }

export const MIN_REASON = 10
export const MIN_SOURCE = 5
export const ROLES: Record<string, string> = { FACILITATOR: "Facilitator", ASSESSOR: "Assessor", MODERATOR: "Moderator", RPL_ASSESSOR: "RPL assessor" }
export const EMPLOYMENT: Record<string, string> = { EMPLOYEE: "Employee", CONTRACTOR: "Contractor", SECONDED: "Seconded" }
export const STANDING: Record<string, string> = {
  VERIFIED_IN_FORCE: "Checked and in force", NOT_VERIFIED: "Not yet checked", NOT_YET_VALID: "Not yet valid", EXPIRED: "Expired", WITHDRAWN: "Withdrawn",
}
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const needsCredential = (role: string) => role === "ASSESSOR" || role === "MODERATOR"

export function standingTone(s: string): "good" | "warn" | "bad" | "neutral" {
  if (s === "VERIFIED_IN_FORCE") return "good"
  if (s === "NOT_VERIFIED" || s === "NOT_YET_VALID") return "warn"
  if (s === "EXPIRED") return "bad"
  return "neutral"
}

const long = (s: string, n: number) => s.trim().length >= n

export type PersonForm = { firstNames: string; surname: string; email: string; phone: string; employmentType: string; notes: string }
export const emptyPerson: PersonForm = { firstNames: "", surname: "", email: "", phone: "", employmentType: "EMPLOYEE", notes: "" }
export const personForm = (p: Practitioner): PersonForm => ({
  firstNames: p.firstNames, surname: p.surname, email: p.email ?? "", phone: p.phone ?? "", employmentType: p.employmentType, notes: p.notes ?? "",
})
export function personProblem(f: PersonForm): string | null {
  if (f.firstNames.trim().length < 2 || f.surname.trim().length < 2) return "Enter first names and a surname"
  if (f.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim())) return "That email address does not look right"
  if (!EMPLOYMENT[f.employmentType]) return "Choose how the person works for the provider"
  return null
}
const blank = (s: string) => (s.trim() ? s.trim() : null)
export const personBody = (f: PersonForm) => ({
  firstNames: f.firstNames.trim(), surname: f.surname.trim(), email: blank(f.email), phone: blank(f.phone), employmentType: f.employmentType, userId: null, notes: blank(f.notes),
})

export type AppointmentForm = { role: string; programmeId: string; authorityId: string; credentialRef: string; credentialNote: string; validFrom: string; validTo: string }
export const emptyAppointment: AppointmentForm = { role: "FACILITATOR", programmeId: "", authorityId: "", credentialRef: "", credentialNote: "", validFrom: "", validTo: "" }
export function appointmentProblem(f: AppointmentForm): string | null {
  if (!ROLES[f.role]) return "Choose a role"
  if (!f.programmeId) return "Choose the programme"
  if (f.validFrom && f.validTo && f.validTo < f.validFrom) return "The end date cannot be before the start date"
  return null
}
export const appointmentBody = (f: AppointmentForm) => ({
  role: f.role, programmeId: f.programmeId, authorityId: f.authorityId || null, credentialRef: blank(f.credentialRef), credentialNote: blank(f.credentialNote),
  validFrom: f.validFrom || null, validTo: f.validTo || null,
})

export type VerifyForm = { source: string; on: string; noSecondPerson: boolean; overrideReason: string }
export const emptyVerify = (today: string): VerifyForm => ({ source: "", on: today, noSecondPerson: false, overrideReason: "" })
export function verifyProblem(role: string, credentialRef: string | null, f: VerifyForm, today: string): string | null {
  if (!long(f.source, MIN_SOURCE)) return `Say what you checked, for example the register entry, in at least ${MIN_SOURCE} characters`
  if (!f.on) return "Enter the date you checked it"
  if (f.on > today) return "The check date cannot be in the future"
  if (needsCredential(role) && !(credentialRef ?? "").trim()) return "Add the credential reference before recording a check"
  if (f.noSecondPerson && !long(f.overrideReason, MIN_REASON)) return `Say why no second person can check it, in at least ${MIN_REASON} characters`
  return null
}
export const verifyBody = (f: VerifyForm) => ({
  verificationSource: f.source.trim(), verifiedOn: f.on, overrideReason: f.noSecondPerson ? f.overrideReason.trim() : null,
})

export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

/** Plain statement of what a check does and does not mean, shown beside every checked appointment. */
export function checkNote(a: Pick<Appointment, "verifiedOn" | "verificationSource" | "credentialRef">): string {
  if (!a.verifiedOn) return "Nobody has recorded a check of this appointment. The reference is shown as it was typed."
  return `A person recorded a check on ${a.verifiedOn}: ${a.verificationSource ?? ""}. The reference is shown as typed${a.credentialRef ? ` (${a.credentialRef})` : ""}.`
}
