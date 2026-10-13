// Helpers for the Patients directory: saved views, the server query, and the row facts.
import { saIdParts } from "./patientFile.shared"
export type PatientView = "ALL" | "RECENT" | "TODAY" | "MINE" | "FOLLOW_UP" | "NEVER_SEEN" | "DUPLICATES"

export const PATIENT_VIEWS: { id: PatientView; label: string; hint: string }[] = [
  { id: "ALL", label: "All patients", hint: "Everyone, A to Z" },
  { id: "RECENT", label: "Recently seen", hint: "Newest visit first" },
  { id: "TODAY", label: "Today", hint: "Seen or booked today" },
  { id: "MINE", label: "My patients", hint: "Patients you have consulted" },
  { id: "FOLLOW_UP", label: "Needs follow-up", hint: "A follow-up is due and not booked" },
  { id: "NEVER_SEEN", label: "Never seen", hint: "Registered, no completed visit yet" },
  { id: "DUPLICATES", label: "Possible duplicates", hint: "Same first and last name" },
]

export const DIRECTORY_PAGE_SIZE = 25

export interface DirectoryEntry {
  patient: { id: string; [k: string]: any }
  visitCount: number; lastVisitAt?: string | null; nextAppointmentAt?: string | null
  sameNameCount: number; followUpDue: boolean
}
export interface DirectoryPage { content: DirectoryEntry[]; page: number; size: number; total: number }

export function directoryUrl(p: { view: PatientView; search: string; includeArchived: boolean; practitionerId: string; page: number }): string {
  const qs = new URLSearchParams({ view: p.view, page: String(p.page), size: String(DIRECTORY_PAGE_SIZE) })
  if (p.search.trim()) qs.set("search", p.search.trim())
  if (p.includeArchived) qs.set("includeArchived", "true")
  if (p.view === "MINE" && p.practitionerId) qs.set("practitionerId", p.practitionerId)
  return `/api/v1/clinic/patients/directory?${qs}`
}

/** A directory entry as one row object: the patient record plus the visit facts. */
export function toRow(e: DirectoryEntry) {
  return { ...e.patient, lastVisitAt: e.lastVisitAt ?? undefined, visitCount: e.visitCount,
    nextAppointmentAt: e.nextAppointmentAt ?? undefined, sameNameCount: e.sameNameCount, followUpDue: e.followUpDue }
}

/** Whole calendar months from a "YYYY-MM-DD" birth date to today, or null if the date is unusable or in the future. */
export function ageInMonths(dob: string | null | undefined, today: Date = new Date()): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(dob ?? "")
  if (!m) return null
  const [y, mo, d] = [+m[1], +m[2], +m[3]]
  let months = (today.getFullYear() - y) * 12 + (today.getMonth() + 1 - mo)
  if (today.getDate() < d) months -= 1
  return months < 0 ? null : months
}

/** Age as a clinician reads it: days under a month, months under two years, then years. */
export function ageLabel(dob: string | null | undefined, today: Date = new Date()): string | null {
  const months = ageInMonths(dob, today)
  if (months === null) return null
  if (months === 0) {
    const days = Math.floor((Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()) - Date.parse(dob!.slice(0, 10))) / 86400000)
    return `${days} day${days === 1 ? "" : "s"}`
  }
  if (months < 24) return `${months} mth${months === 1 ? "" : "s"}`
  const years = Math.floor(months / 12)
  return `${years} yrs`
}

const SEX_LABEL: Record<string, string> = { MALE: "Male", FEMALE: "Female", NON_BINARY: "Non-binary" }

/**
 * The "age · sex" line on a patient row (patch 0202). Uses the stored date of birth and gender first and the SA ID number only
 * as a fallback, so children registered without an ID (birth certificate, passport, or no document yet) still show both.
 */
export function ageSexLine(p: { dateOfBirth?: string | null; gender?: string | null; idNumber?: string | null }, today: Date = new Date()): string | null {
  const id = saIdParts(p.idNumber ?? undefined)
  const dob = p.dateOfBirth || (id ? `${id.year}-${String(id.month).padStart(2, "0")}-${String(id.day).padStart(2, "0")}` : null)
  const sex = (p.gender && SEX_LABEL[p.gender.toUpperCase()]) || (!p.gender && id ? (id.male ? "Male" : "Female") : null)
  const parts = [ageLabel(dob, today), sex].filter(Boolean)
  return parts.length ? parts.join(" · ") : null
}

export const visitsLabel =(n: number) => n === 0 ? "No visits" : n === 1 ? "1 visit" : `${n} visits`
export const pagesOf = (total: number, size = DIRECTORY_PAGE_SIZE) => Math.ceil(total / size)

/** Views offered to this user: "My patients" needs a practitioner record matching the login. */
export function availableViews(practitionerId: string) {
  return PATIENT_VIEWS.filter(v => v.id !== "MINE" || !!practitionerId)
}
