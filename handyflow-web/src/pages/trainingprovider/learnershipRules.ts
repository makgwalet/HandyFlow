// Wording and checks for learnership agreements. The server enforces every rule.
// An agreement is a record of the paper agreement. The registration reference is kept as entered and is never verified here.

export type Agreement = {
  id: string; agreementNo: string; enrolmentId: string; enrolmentNo: string; learnerName: string | null; programmeTitle: string | null; clientId: string | null
  employerName: string; employerContact: string | null; startDate: string; endDate: string | null; status: string; learnerSignedOn: string | null
  employerSignedOn: string | null; providerSignedOn: string | null; registrationRef: string | null; registrationRecordedOn: string | null; endedOn: string | null
  endReason: string | null; cancelReason: string | null; summary: string
}

export const STATUSES: Record<string, string> = { DRAFT: "Draft", SIGNED: "Signed", ENDED: "Ended", CANCELLED: "Cancelled" }
export const LEARNERSHIP_NOTE = "This is a record of the paper agreement. It does not confirm that the learnership is registered or approved."
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const MIN_REASON = 10

export function statusTone(s: string): "good" | "warn" | "bad" | "neutral" {
  return s === "SIGNED" ? "good" : s === "DRAFT" ? "warn" : "neutral"
}

const long = (s: string, n: number) => s.trim().length >= n
export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export type AgreementForm = { clientId: string; employerName: string; employerContact: string; startDate: string; endDate: string }
export const emptyAgreement: AgreementForm = { clientId: "", employerName: "", employerContact: "", startDate: "", endDate: "" }
export const agreementForm = (a: Agreement): AgreementForm => ({ clientId: a.clientId ?? "", employerName: a.employerName, employerContact: a.employerContact ?? "", startDate: a.startDate, endDate: a.endDate ?? "" })
export function agreementProblem(f: AgreementForm, enrolmentStart: string | null): string | null {
  if (!long(f.employerName, 2)) return "Give the name of the employer"
  if (!f.startDate) return "Enter the planned start day"
  if (f.endDate && f.endDate < f.startDate) return "The end day cannot be before the start day"
  if (enrolmentStart && f.startDate < `${Number(enrolmentStart.slice(0, 4)) - 1}${enrolmentStart.slice(4)}`) return "The start day is more than a year before the enrolment started"
  return null
}
export const agreementBody = (f: AgreementForm) => ({
  clientId: f.clientId || null, employerName: f.employerName.trim(), employerContact: f.employerContact.trim() || null, startDate: f.startDate, endDate: f.endDate || null,
})

export type SignForm = { learnerSignedOn: string; employerSignedOn: string; providerSignedOn: string }
export const emptySign: SignForm = { learnerSignedOn: "", employerSignedOn: "", providerSignedOn: "" }
export function signProblem(f: SignForm, today: string): string | null {
  if (!f.learnerSignedOn || !f.employerSignedOn || !f.providerSignedOn) return "Enter the day each of the learner, the employer and the provider signed"
  if ([f.learnerSignedOn, f.employerSignedOn, f.providerSignedOn].some(d => d > today)) return "A signing day cannot be in the future"
  return null
}

export function registrationProblem(reference: string, on: string, today: string): string | null {
  if (!long(reference, 3)) return "Enter the reference exactly as it appears on the document"
  if (!on) return "Enter the day the reference was recorded"
  if (on > today) return "The day cannot be in the future"
  return null
}

export function endProblem(on: string, latestSigned: string | null, reason: string, today: string): string | null {
  if (!on) return "Enter the day the agreement ended"
  if (on > today) return "The day cannot be in the future"
  if (latestSigned && on < latestSigned) return "It cannot end before it was signed"
  return reasonProblem(reason)
}

export const latestSigned = (a: Pick<Agreement, "learnerSignedOn" | "employerSignedOn" | "providerSignedOn">): string | null =>
  [a.learnerSignedOn, a.employerSignedOn, a.providerSignedOn].filter((d): d is string => !!d).sort().pop() ?? null
