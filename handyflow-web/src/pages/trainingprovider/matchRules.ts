// Wording and checks for duplicate learners, unmatched delegates, the delegate link and merging. The server enforces every rule;
// these save a round trip. A match only says "look at this": nothing here decides that two records are one person.

export type PersonView = {
  kind: "LEARNER" | "DELEGATE"; id: string; number: string; name: string; idNumberMasked: string | null; dateOfBirth: string | null
  email: string | null; phone: string | null; status: string; linkedLearnerId: string | null
}
export type MatchView = { person: PersonView; strength: "STRONG" | "POSSIBLE"; reasons: string[]; conflicts: string[] }
export type PairView = { first: PersonView; second: PersonView; strength: "STRONG" | "POSSIBLE"; reasons: string[]; conflicts: string[] }
export type UnmatchedDelegate = { delegate: PersonView; clientId: string; clientName: string | null; candidates: MatchView[] }
export type MergeView = {
  id: string; status: string; survivorId: string; survivorName: string | null; survivorNo: string | null; mergedId: string; mergedName: string | null
  mergedNo: string | null; matchReasons: string[]; conflicts: string[]; conflictsAccepted: boolean; reason: string; survivorSnapshot: string
  mergedSnapshot: string; proposedBy: string; proposedAt: string; decidedBy: string | null; decidedAt: string | null; decisionReason: string | null
  movedEnrolments: number | null; movedApplications: number | null; movedDocuments: number | null; delegateNote: string | null
}
export type LearnerEvent = { id: string; eventType: string; delegateId: string | null; otherLearnerId: string | null; reason: string | null; actedBy: string; actedAt: string }

export const REASONS: Record<string, string> = {
  ID_NUMBER: "same identity number", NAME: "same name", DATE_OF_BIRTH: "same date of birth", EMAIL: "same email", PHONE: "same phone number",
}
export const STRENGTH: Record<string, string> = { STRONG: "Likely the same person", POSSIBLE: "Might be the same person" }
export const MERGE_STATUS: Record<string, string> = { PROPOSED: "Waiting for a decision", COMPLETED: "Merged", REJECTED: "Rejected", CANCELLED: "Cancelled" }
export const EVENT: Record<string, string> = {
  DELEGATE_LINKED: "Linked to a delegate", DELEGATE_UNLINKED: "Delegate link removed", MERGED_INTO: "Merged into another learner", MERGED_FROM: "Another learner merged into this one",
}

export const reasonText = (reasons: string[]) => reasons.map(r => REASONS[r] ?? r.toLowerCase()).join(", ")
export const MIN_REASON = 10
const long = (s: string | null | undefined) => (s ?? "").trim().length >= MIN_REASON

export type MergeProposalForm = { keepId: string; reason: string; conflictsAccepted: boolean }

/** Which of the two the person chose to keep. The other is merged into it. */
export function mergeProposalProblem(f: MergeProposalForm, conflicts: string[]): string | null {
  if (!f.keepId) return "Choose which record to keep"
  if (conflicts.length > 0 && !f.conflictsAccepted) return `The records disagree (${conflicts.join("; ")}). Tick the box to say you have checked this`
  if (!long(f.reason)) return `Say why these are the same person, in at least ${MIN_REASON} characters`
  return null
}
export const mergeProposalBody = (a: string, b: string, f: MergeProposalForm) => ({
  survivorId: f.keepId, mergedId: f.keepId === a ? b : a, reason: f.reason.trim(), conflictsAccepted: f.conflictsAccepted,
})

export type MergeDecisionForm = { reason: string; alone: boolean; aloneReason: string }
export function mergeDecisionProblem(f: MergeDecisionForm, proposedBy: string, me: string | null): string | null {
  if (!long(f.reason)) return `Say why, in at least ${MIN_REASON} characters`
  if (me && me === proposedBy) {
    if (!f.alone) return "A second person needs to decide a merge you proposed, or say why you are deciding it alone"
    if (!long(f.aloneReason)) return `Say why no second person is deciding, in at least ${MIN_REASON} characters`
  }
  return null
}
export const mergeDecisionBody = (f: MergeDecisionForm) => ({ reason: f.reason.trim(), confirmAlone: f.alone, singleReason: f.alone ? f.aloneReason.trim() : null })

/** Moving or removing a delegate link needs a reason. A first link does not, unless the identity numbers differ (the server checks that). */
export function linkChangeProblem(hadLink: boolean, reason: string, delegateId: string): string | null {
  if (!hadLink && !delegateId) return "Choose a delegate"
  if (hadLink && !long(reason)) return `Say why the link is changing, in at least ${MIN_REASON} characters`
  return null
}
export const linkBody = (delegateId: string, reason: string) => ({ delegateId: delegateId || null, reason: reason.trim() || null })
