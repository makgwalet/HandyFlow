// Wording and checks for learners, applications and learner enrolments. The server enforces every rule; these save a round trip.
// Nothing here decides eligibility or says a learner is competent: a person records the entry check.

export type Learner = {
  id: string; learnerNo: string; firstNames: string; surname: string; idType: string; idNumber: string | null; idNumberMasked: string | null
  dateOfBirth: string | null; email: string | null; phone: string | null; address: string | null; province: string | null
  clientId: string | null; clientName: string | null; delegateId: string | null; consentGiven: boolean; consentOn: string | null
  consentNote: string | null; status: string; archivedReason: string | null; liveEnrolments: number
}
export type Application = {
  id: string; learnerId: string; learnerName: string; programmeId: string; programmeTitle: string; siteId: string | null; siteName: string | null
  preferredStart: string | null; sourceNote: string | null; status: string; statusReason: string | null; entryRequirementsMet: boolean | null
  entryCheckNote: string | null; entryOverrideReason: string | null; nextStatuses: string[]; createdAt: string
}
export type Enrolment = {
  id: string; enrolmentNo: string; learnerId: string; learnerName: string; programmeId: string; programmeTitle: string; programmeVersionNo: number | null
  siteId: string; siteName: string; applicationId: string | null; clientId: string | null; startDate: string; expectedEndDate: string | null
  status: string; statusReason: string | null; trainingActivity: string; coverOutcome: string | null; coverCheckedOn: string | null
  coverOverrideReason: string | null; nextStatuses: string[]; createdAt: string
}
export type LearnerDetail = { learner: Learner; applications: Application[]; enrolments: Enrolment[] }

export const MIN_REASON = 10

export const ID_TYPES: Record<string, string> = { SA_ID: "South African ID", PASSPORT: "Passport", OTHER: "Other document", NONE: "No identity document" }
export const APPLICATION_STATUS: Record<string, string> = {
  RECEIVED: "Received", UNDER_REVIEW: "Under review", DOCUMENTS_REQUESTED: "Documents requested", ACCEPTED: "Accepted",
  DECLINED: "Declined", WITHDRAWN: "Withdrawn", ENROLLED: "Enrolled",
}
export const ENROLMENT_STATUS: Record<string, string> = { ENROLLED: "Enrolled", IN_PROGRESS: "In progress", ON_HOLD: "On hold", WITHDRAWN: "Withdrawn", CANCELLED: "Cancelled" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

export const APP_NEEDS_REASON = ["DECLINED", "WITHDRAWN", "DOCUMENTS_REQUESTED"]
export const ENROL_NEEDS_REASON = ["ON_HOLD", "WITHDRAWN", "CANCELLED"]

export function applicationTone(s: string): "good" | "warn" | "bad" | "neutral" {
  if (s === "ACCEPTED" || s === "ENROLLED") return "good"
  if (s === "DOCUMENTS_REQUESTED" || s === "UNDER_REVIEW") return "warn"
  if (s === "DECLINED") return "bad"
  return "neutral"
}
export function enrolmentTone(s: string): "good" | "warn" | "bad" | "neutral" {
  if (s === "ENROLLED" || s === "IN_PROGRESS") return "good"
  if (s === "ON_HOLD") return "warn"
  return "neutral"
}

export const COVER: Record<string, string> = {
  COVERED: "Covered", COVERED_WITH_CONDITIONS: "Covered, with conditions", COVERED_RENEWAL_PENDING: "Covered, renewal pending",
  NO_SCOPE: "No approved scope", SITE_NOT_COVERED: "Not covered at this site", SCOPE_WITHDRAWN: "Scope withdrawn",
  SCOPE_NOT_IN_FORCE: "Scope not in force on that date", NOT_APPROVED: "Accreditation not approved", SUSPENDED: "Accreditation suspended", EXPIRED: "Accreditation expired",
}
export const isCovered = (o: string | null | undefined) => o === "COVERED" || o === "COVERED_WITH_CONDITIONS" || o === "COVERED_RENEWAL_PENDING"

/** What the enrolment record honestly says about accreditation on the day it was made. */
export function coverNote(e: Pick<Enrolment, "trainingActivity" | "coverOutcome" | "coverCheckedOn" | "coverOverrideReason">): string {
  if (e.trainingActivity !== "ACCREDITED") return "Not an accredited programme"
  const on = e.coverCheckedOn ? ` on ${e.coverCheckedOn}` : ""
  if (isCovered(e.coverOutcome)) return `${label(COVER, e.coverOutcome)} when enrolled${on}`
  return `${label(COVER, e.coverOutcome)} when enrolled${on}. Enrolled by an administrator: ${e.coverOverrideReason ?? "no reason recorded"}`
}

const long = (s: string | null | undefined, n: number) => (s ?? "").trim().length >= n
const blank = (s: string) => (s.trim() === "" ? null : s.trim())

/** Luhn check over every digit, as used for South African ID numbers. */
export function luhnValid(d: string): boolean {
  if (!/^\d+$/.test(d)) return false
  let sum = 0
  let dbl = false
  for (let i = d.length - 1; i >= 0; i--) {
    let n = d.charCodeAt(i) - 48
    if (dbl) { n *= 2; if (n > 9) n -= 9 }
    sum += n
    dbl = !dbl
  }
  return sum % 10 === 0
}

export type LearnerForm = {
  firstNames: string; surname: string; idType: string; idNumber: string; dateOfBirth: string; email: string; phone: string; address: string
  province: string; clientId: string; consentGiven: boolean; consentOn: string; consentNote: string
}
export const emptyLearner: LearnerForm = {
  firstNames: "", surname: "", idType: "SA_ID", idNumber: "", dateOfBirth: "", email: "", phone: "", address: "", province: "", clientId: "",
  consentGiven: false, consentOn: "", consentNote: "",
}
export function learnerForm(l: Learner): LearnerForm {
  return {
    firstNames: l.firstNames, surname: l.surname, idType: l.idType, idNumber: l.idNumber ?? "", dateOfBirth: l.dateOfBirth ?? "", email: l.email ?? "",
    phone: l.phone ?? "", address: l.address ?? "", province: l.province ?? "", clientId: l.clientId ?? "", consentGiven: l.consentGiven,
    consentOn: l.consentOn ?? "", consentNote: l.consentNote ?? "",
  }
}

export function learnerProblem(f: LearnerForm, today: string): string | null {
  if (f.firstNames.trim().length < 2 || f.surname.trim().length < 2) return "Enter the learner's first names and surname"
  const n = f.idNumber.trim()
  if (f.idType === "NONE" && n) return "Leave the number blank when the learner has no identity document"
  if (f.idType !== "NONE" && n.length < 3) return "Enter the identity number"
  if (f.dateOfBirth && f.dateOfBirth > today) return "The date of birth cannot be in the future"
  if (f.idType === "SA_ID") {
    if (!/^\d{13}$/.test(n)) return "A South African ID number has 13 digits"
    if (!luhnValid(n)) return "That ID number does not pass its check digit. Please re-check it"
    if (f.dateOfBirth) {
      const yy = n.slice(0, 2), mmdd = n.slice(2, 6)
      const dob = f.dateOfBirth
      if (dob.slice(2, 4) !== yy || dob.slice(5, 7) + dob.slice(8, 10) !== mmdd) return "The date of birth does not match the date inside the ID number"
    }
  }
  if (f.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim())) return "That email address does not look right"
  if (f.consentGiven && !f.consentOn) return "Enter the date the learner gave consent"
  if (!f.consentGiven && f.consentOn) return "A consent date was entered without consent being ticked"
  if (f.consentOn && f.consentOn > today) return "The consent date cannot be in the future"
  return null
}
export const learnerBody = (f: LearnerForm) => ({
  firstNames: f.firstNames.trim(), surname: f.surname.trim(), idType: f.idType, idNumber: f.idType === "NONE" ? null : blank(f.idNumber),
  dateOfBirth: f.dateOfBirth || null, email: blank(f.email), phone: blank(f.phone), address: blank(f.address), province: blank(f.province),
  clientId: f.clientId || null, delegateId: null, consentGiven: f.consentGiven, consentOn: f.consentGiven ? f.consentOn || null : null, consentNote: blank(f.consentNote),
})

export type ApplicationForm = { programmeId: string; siteId: string; preferredStart: string; sourceNote: string }
export const emptyApplication: ApplicationForm = { programmeId: "", siteId: "", preferredStart: "", sourceNote: "" }
export function applicationFormProblem(f: ApplicationForm): string | null {
  return f.programmeId ? null : "Choose the programme the learner is applying for"
}
export const applicationBody = (learnerId: string, f: ApplicationForm) => ({
  learnerId, programmeId: f.programmeId, siteId: f.siteId || null, preferredStart: f.preferredStart || null, sourceNote: blank(f.sourceNote),
})

export type AppTransitionForm = { toStatus: string; reason: string; entryMet: "" | "yes" | "no"; entryNote: string; overrideReason: string }
export const emptyAppTransition = (toStatus = ""): AppTransitionForm => ({ toStatus, reason: "", entryMet: "", entryNote: "", overrideReason: "" })
export function appTransitionProblem(f: AppTransitionForm, allowed: string[]): string | null {
  if (!f.toStatus) return "Choose the new status"
  if (!allowed.includes(f.toStatus)) return "That change is not allowed from the current status"
  if (APP_NEEDS_REASON.includes(f.toStatus) && !long(f.reason, MIN_REASON)) return `Say why, in at least ${MIN_REASON} characters`
  if (f.toStatus === "ACCEPTED") {
    if (!f.entryMet) return "Record whether the learner meets the entry requirements, as checked by a person"
    if (!long(f.entryNote, MIN_REASON)) return `Say what was checked for entry, in at least ${MIN_REASON} characters`
    if (f.entryMet === "no" && !long(f.overrideReason, MIN_REASON)) return `The entry requirements are not met. To accept anyway, say why in at least ${MIN_REASON} characters`
  }
  return null
}
export const appTransitionBody = (f: AppTransitionForm) => ({
  toStatus: f.toStatus, reason: blank(f.reason), entryRequirementsMet: f.entryMet === "" ? null : f.entryMet === "yes",
  entryCheckNote: blank(f.entryNote), overrideReason: f.entryMet === "no" ? blank(f.overrideReason) : null,
})

export type EnrolForm = { programmeId: string; siteId: string; startDate: string; expectedEndDate: string; clientId: string; overrideReason: string }
export const emptyEnrol: EnrolForm = { programmeId: "", siteId: "", startDate: "", expectedEndDate: "", clientId: "", overrideReason: "" }
export function enrolFormProblem(f: EnrolForm): string | null {
  if (!f.programmeId) return "Choose the programme"
  if (!f.siteId) return "Choose the site"
  if (!f.startDate) return "Enter the start date"
  if (f.expectedEndDate && f.expectedEndDate < f.startDate) return "The expected end date cannot be before the start date"
  return null
}
export const enrolBody = (learnerId: string, f: EnrolForm, applicationId: string | null) => ({
  learnerId, programmeId: f.programmeId, siteId: f.siteId, applicationId, clientId: f.clientId || null, startDate: f.startDate,
  expectedEndDate: f.expectedEndDate || null, overrideReason: blank(f.overrideReason),
})

export function reasonProblem(reason: string): string | null {
  return long(reason, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`
}
export function enrolmentTransitionProblem(to: string, reason: string, allowed: string[]): string | null {
  if (!to) return "Choose the new status"
  if (!allowed.includes(to)) return "That change is not allowed from the current status"
  if (ENROL_NEEDS_REASON.includes(to) && !long(reason, MIN_REASON)) return `Say why, in at least ${MIN_REASON} characters`
  return null
}
