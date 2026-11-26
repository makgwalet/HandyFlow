// Wording and checks for the authority overview: current, transitional and historical records, the dates and source the authority gave,
// and the evidence behind each. The server enforces every rule; these save a round trip. Nothing here says the provider is accredited:
// that is the recorded status, checked against its scope and dates.

export type FrameworkRow = {
  id: string; title: string; accreditationNumber: string | null; status: string; effectiveStatus: string; currency: string; currencyNote: string | null
  endedOn: string | null; supersededById: string | null; effectiveFrom: string | null; approvalDate: string | null; expiryDate: string | null
  reviewDueOn: string | null; lastVerifiedOn: string | null; sourceType: string | null; sourceReference: string | null; sourceVersionDate: string | null
  sourceLocation: string | null; liveScopes: number; sites: number; verifiedEvidence: number; pendingEvidence: number; flags: string[]
}
export type FrameworkAuthority = { id: string; name: string; authorityType: string; rank: number; status: string; liveObligations: number; accreditations: FrameworkRow[] }
export type Evidence = {
  id: string; accreditationId: string; kind: string; description: string; reference: string | null; documentDate: string | null; receivedOn: string
  status: string; addedBy: string; decidedBy: string | null; decisionNote: string | null
}

export const MIN_NOTE = 10
const long = (s: string | null | undefined, n = MIN_NOTE) => (s ?? "").trim().length >= n
const blank = (s: string) => (s.trim() === "" ? null : s.trim())

export const CURRENCY: Record<string, string> = { CURRENT: "Current", TRANSITIONAL: "Transitional", HISTORICAL: "Historical" }
export const FLAG: Record<string, string> = {
  EXPIRED: "Expired", TRANSITIONAL: "Transitional arrangement", NO_VERIFIED_EVIDENCE: "No verified evidence on file", EVIDENCE_PENDING: "Evidence waiting to be checked", REVIEW_OVERDUE: "Review overdue",
}
export const SOURCE_TYPES: Record<string, string> = {
  LETTER: "Letter", CERTIFICATE: "Certificate", REGISTER_ENTRY: "Register entry", NOTICE: "Notice", EMAIL: "Email", WEBSITE: "Website", OTHER: "Other",
}
export const EVIDENCE_KINDS: Record<string, string> = { LETTER: "Letter", CERTIFICATE: "Certificate", REGISTER_ENTRY: "Register entry", NOTICE: "Notice", EMAIL: "Email", OTHER: "Other" }
export const EVIDENCE_STATUS: Record<string, string> = { PENDING: "Waiting to be checked", VERIFIED: "Verified", REJECTED: "Rejected", WITHDRAWN: "Withdrawn" }

export type FrameworkForm = { effectiveFrom: string; reviewDueOn: string; sourceType: string; sourceReference: string; sourceVersionDate: string; sourceLocation: string }
export const frameworkForm = (r: FrameworkRow): FrameworkForm => ({
  effectiveFrom: r.effectiveFrom ?? "", reviewDueOn: r.reviewDueOn ?? "", sourceType: r.sourceType ?? "", sourceReference: r.sourceReference ?? "",
  sourceVersionDate: r.sourceVersionDate ?? "", sourceLocation: r.sourceLocation ?? "",
})
export function frameworkProblem(f: FrameworkForm, r: Pick<FrameworkRow, "approvalDate" | "expiryDate">, today: string): string | null {
  if (f.effectiveFrom && r.expiryDate && r.expiryDate < f.effectiveFrom) return "The expiry date cannot be before the date it takes effect"
  const start = f.effectiveFrom || r.approvalDate
  if (f.reviewDueOn && start && f.reviewDueOn < start) return "The review date cannot be before the approval takes effect"
  const any = f.sourceType || f.sourceReference.trim() || f.sourceVersionDate || f.sourceLocation.trim()
  if (any) {
    if (!f.sourceType) return "Say what kind of document the source is"
    if (!long(f.sourceReference, 5)) return "Give the document or reference the information came from (at least 5 characters)"
    if (f.sourceVersionDate && f.sourceVersionDate > today) return "The version date of the source cannot be in the future"
  }
  return null
}
export const frameworkBody = (f: FrameworkForm) => ({
  effectiveFrom: f.effectiveFrom || null, reviewDueOn: f.reviewDueOn || null, sourceType: f.sourceType || null, sourceReference: blank(f.sourceReference),
  sourceVersionDate: f.sourceVersionDate || null, sourceLocation: blank(f.sourceLocation),
})

export type CurrencyForm = { currency: string; note: string; endedOn: string; supersededById: string }
export const currencyForm = (r: FrameworkRow): CurrencyForm => ({ currency: r.currency, note: r.currencyNote ?? "", endedOn: r.endedOn ?? "", supersededById: r.supersededById ?? "" })
export function currencyProblem(f: CurrencyForm, approvalDate: string | null, today: string): string | null {
  if (f.currency === "CURRENT") return null
  if (!long(f.note)) return `Say why this is ${f.currency.toLowerCase()}, in at least ${MIN_NOTE} characters`
  if (f.currency === "HISTORICAL") {
    if (!f.endedOn) return "Enter the date this stopped applying"
    if (f.endedOn > today) return "The end date cannot be in the future. Leave it current until it ends"
    if (approvalDate && f.endedOn < approvalDate) return "It cannot have ended before it was approved"
  }
  return null
}
export const currencyBody = (f: CurrencyForm) => ({
  currency: f.currency, note: f.currency === "CURRENT" ? null : f.note.trim(), endedOn: f.currency === "HISTORICAL" ? f.endedOn || null : null,
  supersededById: f.currency === "HISTORICAL" ? f.supersededById || null : null,
})

export type EvidenceForm = { kind: string; description: string; reference: string; documentDate: string; receivedOn: string }
export const emptyEvidence = (today: string): EvidenceForm => ({ kind: "LETTER", description: "", reference: "", documentDate: "", receivedOn: today })
export function evidenceProblem(f: EvidenceForm, today: string): string | null {
  if (!long(f.description, 5)) return "Describe the document, in at least 5 characters"
  if (!f.receivedOn) return "Enter the date it was received"
  if (f.receivedOn > today) return "The received date cannot be in the future"
  if (f.documentDate && f.documentDate > f.receivedOn) return "The document cannot be dated after the day it was received"
  return null
}
export const evidenceBody = (f: EvidenceForm) => ({
  kind: f.kind, description: f.description.trim(), reference: blank(f.reference), documentDate: f.documentDate || null, receivedOn: f.receivedOn,
})
export function evidenceDecisionProblem(to: string, note: string): string | null {
  if (to !== "VERIFIED" && !long(note)) return `Say why, in at least ${MIN_NOTE} characters`
  return null
}
