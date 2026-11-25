// Wording and checks for assessment attempts, competency decisions and moderation. The server enforces every rule.
// The mark and the decision are separate. Attendance is not a result. Nothing here applies a pass mark.

export type AttemptEvent = { id: string; action: string; note: string | null; actedBy: string | null; actedAt: string }
export type DecisionView = { decision: string; basis: string; decidedBy: string | null; decidedOn: string }
export type Attempt = {
  id: string; attemptNo: string; instrumentId: string; instrumentNo: string; instrumentTitle: string; maxMark: number | null; requiresModeration: boolean
  memberId: string; cohortId: string; cohortName: string | null; enrolmentId: string; learnerId: string | null; learnerName: string | null; attemptSeq: number
  assessedOn: string; assessorId: string; assessorName: string | null; mark: number | null; notes: string | null; status: string; voidReason: string | null
  decision: DecisionView | null; history: AttemptEvent[]
}
export type Moderation = {
  id: string; moderationNo: string; attemptId: string; attemptNo: string; instrumentTitle: string | null; learnerName: string | null; assessorName: string | null
  decision: string | null; moderatorId: string; moderatorName: string | null; moderatedOn: string; outcome: string; basis: string; resolutionNote: string | null; resolvedAt: string | null
}
export type PendingRow = {
  attemptId: string; attemptNo: string; instrumentTitle: string | null; cohortName: string | null; learnerName: string | null; assessorId: string
  assessorName: string | null; decision: string | null; decidedOn: string | null
}

export const MIN_REASON = 10
export const DECISIONS: Record<string, string> = { COMPETENT: "Competent", NOT_YET_COMPETENT: "Not yet competent" }
export const OUTCOMES: Record<string, string> = { CONFIRMED: "Confirmed", QUERIED: "Queried" }
export const ATTEMPT_STATUS: Record<string, string> = { RECORDED: "Recorded", VOID: "Void" }
export const ACTIONS: Record<string, string> = { RECORDED: "Recorded", MARK_CORRECTED: "Mark corrected", DECIDED: "Decision recorded", VOIDED: "Voided" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

/** Shown wherever a mark and a decision sit together. */
export const SEPARATE_NOTE = "The mark is what was scored. The decision is the assessor's judgement of competence, recorded separately with its basis. Neither is taken from attendance."

export function decisionTone(d: string | null | undefined): "good" | "warn" | "bad" | "neutral" {
  return d === "COMPETENT" ? "good" : d === "NOT_YET_COMPETENT" ? "warn" : "neutral"
}
export function attemptTone(a: Pick<Attempt, "status" | "decision">): "good" | "warn" | "bad" | "neutral" {
  if (a.status === "VOID") return "neutral"
  return a.decision ? decisionTone(a.decision.decision) : "warn"
}
export function attemptStateText(a: Pick<Attempt, "status" | "decision">): string {
  if (a.status === "VOID") return "Void"
  return a.decision ? label(DECISIONS, a.decision.decision) : "Waiting for a decision"
}
export function markText(a: Pick<Attempt, "mark" | "maxMark">): string {
  if (a.maxMark === null) return "Not marked"
  return a.mark === null ? "No mark" : `${a.mark} out of ${a.maxMark}`
}
export const moderationText = (m: Pick<Moderation, "outcome" | "resolvedAt">): string =>
  m.outcome === "CONFIRMED" ? "Confirmed by a moderator" : m.resolvedAt ? "Queried, and the query has been answered" : "Queried, waiting for an answer"

const long = (s: string, n: number) => s.trim().length >= n
export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export type AttemptForm = { memberId: string; instrumentId: string; assessedOn: string; assessorId: string; mark: string; notes: string }
export const emptyAttempt = (today: string): AttemptForm => ({ memberId: "", instrumentId: "", assessedOn: today, assessorId: "", mark: "", notes: "" })

/** Parses a typed mark. Null means empty; NaN is returned as undefined so the caller can refuse it. */
export function parseMark(s: string): number | null | undefined {
  if (s.trim() === "") return null
  const n = Number(s)
  return Number.isFinite(n) ? n : undefined
}
export function attemptProblem(f: AttemptForm, maxMark: number | null, today: string): string | null {
  if (!f.memberId) return "Choose the learner"
  if (!f.instrumentId) return "Choose the instrument"
  if (!f.assessedOn) return "Enter the day of the assessment"
  if (f.assessedOn > today) return "The assessment day cannot be in the future"
  if (!f.assessorId) return "Choose the assessor"
  return markProblem(f.mark, maxMark)
}
export function markProblem(mark: string, maxMark: number | null): string | null {
  const m = parseMark(mark)
  if (m === undefined) return "The mark must be a number"
  if (maxMark === null) return m === null ? null : "This instrument is not marked, so leave the mark empty"
  if (m === null) return `This instrument is marked out of ${maxMark}. Enter the mark`
  if (m < 0) return "The mark cannot be below 0"
  if (m > maxMark) return `The mark cannot be more than the maximum of ${maxMark}`
  return null
}
export const attemptBody = (f: AttemptForm) => ({
  instrumentId: f.instrumentId, memberId: f.memberId, assessedOn: f.assessedOn, assessorId: f.assessorId, mark: parseMark(f.mark) ?? null, notes: f.notes.trim() || null,
})

export type CorrectForm = { mark: string; reason: string }
export function correctProblem(f: CorrectForm, maxMark: number | null, current: number | null): string | null {
  const p = markProblem(f.mark, maxMark); if (p) return p
  if ((parseMark(f.mark) ?? null) === current) return "That is the mark already recorded"
  return reasonProblem(f.reason)
}
export const correctBody = (f: CorrectForm) => ({ mark: parseMark(f.mark) ?? null, reason: f.reason.trim() })

export type DecisionForm = { decision: string; basis: string }
export const emptyDecision: DecisionForm = { decision: "", basis: "" }
export function decisionProblem(f: DecisionForm): string | null {
  if (!DECISIONS[f.decision]) return "Choose competent or not yet competent"
  if (!long(f.basis, MIN_REASON)) return `Write the basis for the decision, in at least ${MIN_REASON} characters`
  return null
}
export const decisionBody = (f: DecisionForm) => ({ decision: f.decision, basis: f.basis.trim() })

export type ModerationForm = { moderatorId: string; moderatedOn: string; outcome: string; basis: string }
export const emptyModeration = (today: string): ModerationForm => ({ moderatorId: "", moderatedOn: today, outcome: "", basis: "" })
export function moderationProblem(f: ModerationForm, today: string, decidedOn: string | null): string | null {
  if (!f.moderatorId) return "Choose the moderator"
  if (!f.moderatedOn) return "Enter the day of the moderation"
  if (f.moderatedOn > today) return "The moderation day cannot be in the future"
  if (decidedOn && f.moderatedOn < decidedOn) return "The moderation day cannot be before the decision was recorded"
  if (!OUTCOMES[f.outcome]) return "Choose confirmed or queried"
  if (!long(f.basis, MIN_REASON)) return `Write the basis for the moderation, in at least ${MIN_REASON} characters`
  return null
}
export const moderationBody = (f: ModerationForm) => ({ moderatorId: f.moderatorId, moderatedOn: f.moderatedOn, outcome: f.outcome, basis: f.basis.trim() })
export const resolveProblem = (note: string): string | null => (long(note, MIN_REASON) ? null : `Say how the query was answered, in at least ${MIN_REASON} characters`)
