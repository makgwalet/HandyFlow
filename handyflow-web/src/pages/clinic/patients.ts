// Helpers for the Patients directory: saved views, the server query, and the row facts.
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

export const visitsLabel = (n: number) => n === 0 ? "No visits" : n === 1 ? "1 visit" : `${n} visits`
export const pagesOf = (total: number, size = DIRECTORY_PAGE_SIZE) => Math.ceil(total / size)

/** Views offered to this user: "My patients" needs a practitioner record matching the login. */
export function availableViews(practitionerId: string) {
  return PATIENT_VIEWS.filter(v => v.id !== "MINE" || !!practitionerId)
}
