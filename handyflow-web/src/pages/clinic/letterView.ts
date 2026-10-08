// Letter templates: kinds, merge fields, and how a template fills a dialog.
import type { CertForm } from "./historyView"

export type Kind = "SICK_NOTE" | "REFERRAL" | "PRESCRIPTION_LETTER" | "GENERAL_LETTER"
export interface Template { id: string; kind: Kind; name: string; title?: string | null; body?: string | null; specialty?: string | null; urgency?: string | null; unfitDays?: number | null }

export const KIND_LABEL: Record<Kind, string> = {
  SICK_NOTE: "Sick note", REFERRAL: "Referral letter", PRESCRIPTION_LETTER: "Prescription letter", GENERAL_LETTER: "General letter",
}
export const KINDS = Object.keys(KIND_LABEL) as Kind[]

/** What the title and text mean for each kind, so the editor can word its fields. */
export const FIELDS: Record<Kind, { title: string | null; body: string | null; titleHint?: string }> = {
  SICK_NOTE: { title: null, body: "Notes on the certificate" },
  REFERRAL: { title: "Reason for referral", body: "Additional notes" },
  PRESCRIPTION_LETTER: { title: "Letter title", body: "Letter text" },
  GENERAL_LETTER: { title: "Letter title", body: "Letter text" },
}

/** What each merge field is filled with. Visit and doctor fields show a dash when the letter has no visit. */
export const MERGE_LABEL: Record<string, string> = {
  "patient.name": "Patient's full name", "patient.firstName": "Patient's first name", "patient.lastName": "Patient's surname",
  "patient.dob": "Date of birth", "patient.age": "Age", "patient.idNumber": "ID or passport number", "patient.phone": "Phone", "patient.address": "Home address",
  "visit.date": "Visit date", "visit.reason": "Reason for the visit", "visit.diagnosis": "Diagnosis (the sickness)", "visit.treatment": "Treatment plan",
  "doctor.name": "Doctor", "doctor.hpcsa": "Doctor's HPCSA number", "doctor.practiceNumber": "Doctor's practice number",
  "practice.name": "Practice name", "today": "Today's date", "recipient.name": "Addressed to (name)", "recipient.company": "Addressed to (company)",
}
export const MERGE_FIELDS = Object.keys(MERGE_LABEL)
export const mergeToken = (f: string) => `{{${f}}}`

export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T12:00:00Z`); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10)
}

/** A rendered sick note template fills the notes, and the last day when it carries a number of days. The first day is today unless one is already chosen. */
export function applySickNote(form: CertForm, t: Pick<Template, "body" | "unfitDays">, today: string): CertForm {
  const from = form.unfitFrom || (t.unfitDays ? today : "")
  const to = t.unfitDays && from ? addDays(from, t.unfitDays - 1) : form.unfitTo
  return { ...form, notes: t.body ?? form.notes, unfitFrom: from, unfitTo: to }
}

export interface ReferralForm { specialistName: string; specialty: string; reason: string; urgency: string; additionalNotes: string }
export function applyReferral(form: ReferralForm, t: Pick<Template, "title" | "body" | "specialty" | "urgency">): ReferralForm {
  return { ...form, specialty: t.specialty ?? form.specialty, reason: t.title ?? form.reason, urgency: t.urgency ?? form.urgency, additionalNotes: t.body ?? form.additionalNotes }
}

/** Puts a merge field into the text where the cursor was, or at the end. */
export function insertAt(text: string, token: string, at: number | null | undefined): { text: string; cursor: number } {
  const pos = at == null || at < 0 || at > text.length ? text.length : at
  return { text: text.slice(0, pos) + token + text.slice(pos), cursor: pos + token.length }
}

export function templateProblem(kind: Kind, name: string, title: string, body: string): string | null {
  if (!name.trim()) return "Give the template a name."
  if ((kind === "GENERAL_LETTER" || kind === "PRESCRIPTION_LETTER") && !title.trim()) return "Give the letter a title."
  if (kind === "REFERRAL") return title.trim() || body.trim() ? null : "Write the reason for referral or the notes."
  return body.trim() ? null : "Write the text of the template."
}
