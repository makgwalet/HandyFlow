// Wording and checks for readiness and completion records. The server enforces every rule.
// Readiness is advice built from decisions and moderation. A person records the outcome. Completion is not a certificate.

export type ReadinessRow = { instrumentId: string; instrumentNo: string; instrumentTitle: string; requiresModeration: boolean; state: string; attempts: number }
export type Readiness = {
  enrolmentId: string; enrolmentNo: string; enrolmentStatus: string; learnerName: string | null; programmeTitle: string | null; versionNo: number | null
  rows: ReadinessRow[]; openGaps: number; note: string
}
export type Completion = {
  id: string; completionNo: string; enrolmentId: string; enrolmentNo: string; learnerId: string; learnerName: string | null; programmeTitle: string | null
  outcome: string; outcomeOn: string; basis: string; openGaps: number; gapsSnapshot: string | null; gapsOverrideReason: string | null; status: string
  withdrawnReason: string | null; recordedAt: string
}

export const MIN_REASON = 10
export const STATES: Record<string, string> = {
  COMPETENT: "Competent", NO_ATTEMPT: "No attempt yet", WAITING_DECISION: "Waiting for a decision", NOT_YET_COMPETENT: "Not yet competent",
  WAITING_MODERATION: "Waiting for moderation", QUERY_OPEN: "Moderation query open",
}
export const OUTCOMES: Record<string, string> = { COMPLETED: "Completed", DID_NOT_COMPLETE: "Did not complete" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

export const READINESS_NOTE = "Readiness is built from competency decisions and moderation. It is advice. A person records the outcome, and completion is not a certificate."

export function stateTone(s: string): "good" | "warn" | "bad" | "neutral" {
  return s === "COMPETENT" ? "good" : s === "NOT_YET_COMPETENT" ? "bad" : "warn"
}
export function outcomeTone(o: string, status: string): "good" | "warn" | "bad" | "neutral" {
  if (status === "WITHDRAWN") return "neutral"
  return o === "COMPLETED" ? "good" : "warn"
}
export function readinessSummary(r: Pick<Readiness, "rows" | "openGaps">): string {
  if (r.rows.length === 0) return "No approved assessment instruments for this version, so there is nothing to check."
  if (r.openGaps === 0) return "Every approved instrument shows competent and, where required, moderated."
  return `${r.openGaps} of ${r.rows.length} instruments ${r.openGaps === 1 ? "is" : "are"} not yet competent and moderated.`
}

const long = (s: string, n: number) => s.trim().length >= n
export type OutcomeForm = { outcome: string; outcomeOn: string; basis: string; overrideReason: string }
export const emptyOutcome = (today: string): OutcomeForm => ({ outcome: "", outcomeOn: today, basis: "", overrideReason: "" })

/** Whether the written reason for open items is needed on this form. */
export const needsOverride = (f: Pick<OutcomeForm, "outcome">, openGaps: number): boolean => f.outcome === "COMPLETED" && openGaps > 0

export function outcomeProblem(f: OutcomeForm, openGaps: number, today: string, startDate: string | null): string | null {
  if (!OUTCOMES[f.outcome]) return "Choose completed or did not complete"
  if (!f.outcomeOn) return "Enter the day of the outcome"
  if (f.outcomeOn > today) return "The day of the outcome cannot be in the future"
  if (startDate && f.outcomeOn < startDate) return "The day of the outcome cannot be before the enrolment started"
  if (!long(f.basis, MIN_REASON)) return `Write the basis for the outcome, in at least ${MIN_REASON} characters`
  if (needsOverride(f, openGaps) && !long(f.overrideReason, MIN_REASON)) {
    return `${openGaps === 1 ? "1 assessment is" : `${openGaps} assessments are`} not yet competent and moderated. To complete anyway, say why in at least ${MIN_REASON} characters`
  }
  return null
}
export const outcomeBody = (enrolmentId: string, f: OutcomeForm, openGaps: number) => ({
  enrolmentId, outcome: f.outcome, outcomeOn: f.outcomeOn, basis: f.basis.trim(), overrideReason: needsOverride(f, openGaps) ? f.overrideReason.trim() : null,
})
export const withdrawProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export function completionLine(c: Pick<Completion, "outcome" | "outcomeOn" | "status" | "openGaps" | "gapsOverrideReason">): string {
  const base = `${label(OUTCOMES, c.outcome)} on ${c.outcomeOn}`
  if (c.status === "WITHDRAWN") return `${base} (withdrawn)`
  return c.outcome === "COMPLETED" && c.openGaps > 0 && c.gapsOverrideReason ? `${base}, with ${c.openGaps} open at the time. Reason given: ${c.gapsOverrideReason}` : base
}
