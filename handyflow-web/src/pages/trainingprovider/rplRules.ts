// Wording and checks for recognition of prior learning. The server enforces every rule.
// An RPL decision is its own record. It is not a completion, not a certificate, and no evidence count decides it.

export type Evidence = {
  id: string; applicationId: string; kind: string; description: string; sourceRef: string | null; receivedOn: string; status: string; withdrawReason: string | null
  review: string | null; reviewNote: string | null; reviewedOn: string | null
}
export type Application = {
  id: string; rplNo: string; enrolmentId: string; enrolmentNo: string; learnerName: string | null; programmeTitle: string | null; moduleId: string | null; moduleTitle: string | null
  claim: string; appliedOn: string; status: string; outcome: string | null; decisionBasis: string | null; assessorName: string | null; decidedOn: string | null
  singleReason: string | null; confirmedOn: string | null; withdrawReason: string | null; liveEvidence: number; unreviewedEvidence: number; acceptedEvidence: number; stateLabel: string
}
export type ApplicationDetail = { application: Application; evidence: Evidence[] }

export const STATUSES: Record<string, string> = { OPEN: "Open", DECIDED: "Waiting for confirmation", CONFIRMED: "Confirmed", WITHDRAWN: "Withdrawn" }
export const OUTCOMES: Record<string, string> = { RECOGNISED: "Recognised", PARTLY_RECOGNISED: "Partly recognised", NOT_RECOGNISED: "Not recognised" }
export const KINDS: Record<string, string> = { DOCUMENT: "Document", REFERENCE: "Reference", WORK_SAMPLE: "Work sample", INTERVIEW: "Interview", OTHER: "Other" }
export const REVIEWS: Record<string, string> = { ACCEPTED: "Accepted", QUERIED: "Queried" }
export const RPL_NOTE = "An RPL decision is its own record. It is not a completion and not a certificate, and no count of evidence decides it."
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const MIN_REASON = 10

export function statusTone(s: string): "good" | "warn" | "bad" | "neutral" {
  return s === "CONFIRMED" ? "good" : s === "DECIDED" || s === "OPEN" ? "warn" : "neutral"
}

const long = (s: string, n: number) => s.trim().length >= n
export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export type ApplicationForm = { moduleId: string; claim: string; appliedOn: string }
export const emptyApplication = (today: string): ApplicationForm => ({ moduleId: "", claim: "", appliedOn: today })
export function applicationProblem(f: ApplicationForm, enrolmentStart: string | null, today: string): string | null {
  if (!long(f.claim, 10)) return "Say what prior learning is being claimed, in at least 10 characters"
  if (!f.appliedOn) return "Enter the day of the application"
  if (f.appliedOn > today) return "The day cannot be in the future"
  if (enrolmentStart && f.appliedOn < enrolmentStart) return "The day cannot be before the enrolment started"
  return null
}
export const applicationBody = (enrolmentId: string, f: ApplicationForm) => ({ enrolmentId, moduleId: f.moduleId || null, claim: f.claim.trim(), appliedOn: f.appliedOn })

export type EvidenceForm = { kind: string; description: string; sourceRef: string; receivedOn: string }
export const emptyEvidence = (today: string): EvidenceForm => ({ kind: "", description: "", sourceRef: "", receivedOn: today })
export function evidenceProblem(f: EvidenceForm, appliedOn: string, today: string): string | null {
  if (!f.kind) return "Choose the kind of evidence"
  if (!long(f.description, 10)) return "Describe the evidence, in at least 10 characters"
  if (!f.receivedOn) return "Enter the day it was received"
  if (f.receivedOn > today) return "The day cannot be in the future"
  if (f.receivedOn < appliedOn) return "The evidence cannot be received before the application"
  return null
}
export const evidenceBody = (f: EvidenceForm) => ({ kind: f.kind, description: f.description.trim(), sourceRef: f.sourceRef.trim() || null, receivedOn: f.receivedOn })

export function reviewProblem(review: string, note: string, on: string, receivedOn: string, today: string): string | null {
  if (!review) return "Choose accepted or queried"
  if (!long(note, 10)) return "Write the reason for the review, in at least 10 characters"
  if (!on) return "Enter the day of the review"
  if (on > today) return "The day cannot be in the future"
  if (on < receivedOn) return "The review cannot be before the evidence was received"
  return null
}

export type DecisionForm = { outcome: string; basis: string; assessorName: string; decidedOn: string; confirmAlone: boolean; singleReason: string }
export const emptyDecision = (today: string): DecisionForm => ({ outcome: "", basis: "", assessorName: "", decidedOn: today, confirmAlone: false, singleReason: "" })
export function decisionProblem(f: DecisionForm, a: Pick<Application, "liveEvidence" | "unreviewedEvidence" | "acceptedEvidence" | "appliedOn">, today: string): string | null {
  if (a.liveEvidence === 0) return "There is no evidence on this application yet"
  if (a.unreviewedEvidence > 0) return `${a.unreviewedEvidence} ${a.unreviewedEvidence === 1 ? "piece of evidence has" : "pieces of evidence have"} not been reviewed yet`
  if (!f.outcome) return "Choose recognised, partly recognised or not recognised"
  if (f.outcome !== "NOT_RECOGNISED" && a.acceptedEvidence === 0) return "Recognition needs at least one piece of accepted evidence"
  if (!long(f.basis, 10)) return "Write the basis for the decision, in at least 10 characters"
  if (!long(f.assessorName, 2)) return "Give the name of the assessor"
  if (!f.decidedOn) return "Enter the day of the decision"
  if (f.decidedOn > today) return "The day cannot be in the future"
  if (f.decidedOn < a.appliedOn) return "The decision cannot be before the application"
  if (f.confirmAlone && !long(f.singleReason, 10)) return "To confirm without a second person, say why in at least 10 characters"
  return null
}
export const decisionBody = (f: DecisionForm) => ({
  outcome: f.outcome, basis: f.basis.trim(), assessorName: f.assessorName.trim(), decidedOn: f.decidedOn, confirmAlone: f.confirmAlone, singleReason: f.confirmAlone ? f.singleReason.trim() : null,
})

export function confirmProblem(on: string, decidedOn: string | null, today: string): string | null {
  if (!on) return "Enter the day of the confirmation"
  if (on > today) return "The day cannot be in the future"
  if (decidedOn && on < decidedOn) return "It cannot be confirmed before it was decided"
  return null
}
