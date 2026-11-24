// What the obligations tab asks of a person making a decision. The server enforces the same rules.

export const STATES: [string, string][] = [["APPLICABLE", "Applies"], ["NOT_APPLICABLE", "Does not apply"], ["PENDING", "Undecided"]]
export const MIN_REASON = 15
export const MAX_REVIEW_MONTHS = 24

export const stateLabel = (s: string) => STATES.find(x => x[0] === s)?.[1] ?? s

export type DecisionDraft = { state: string; reason: string; reviewDue: string }

const addMonths = (d: Date, n: number) => { const r = new Date(d); r.setMonth(r.getMonth() + n); return r }
const iso = (d: Date) => d.toISOString().slice(0, 10)

/** Why the decision cannot be saved yet, or null. */
export function decisionProblem(d: DecisionDraft, suggested: string, today: Date = new Date()): string | null {
  if (d.state !== suggested && d.reason.trim().length < MIN_REASON) {
    return `This differs from the suggestion (${stateLabel(suggested).toLowerCase()}), so give a reason of at least ${MIN_REASON} characters`
  }
  if (d.state === "NOT_APPLICABLE") {
    if (!d.reviewDue) return "Say when this will be looked at again"
    if (d.reviewDue <= iso(today)) return "The review date must be in the future"
    if (d.reviewDue > iso(addMonths(today, MAX_REVIEW_MONTHS))) return `Look at it again within ${MAX_REVIEW_MONTHS} months`
  }
  return null
}

/** Removing something that is not already suggested as removed needs a second person to agree. */
export const needsApproval = (state: string, suggested: string) => state === "NOT_APPLICABLE" && suggested !== "NOT_APPLICABLE"

/** The person who made the decision needs a reason to approve it themselves. */
export function approveNeedsReason(decidedBy: string | null, userId: string | null | undefined): boolean {
  return !!userId && !!decidedBy && decidedBy === userId
}

export function approveProblem(needsReason: boolean, reason: string): string | null {
  return needsReason && reason.trim().length < MIN_REASON
    ? `You made this decision, so someone else should approve it. If no one else can, give a reason of at least ${MIN_REASON} characters.`
    : null
}

export type Row = {
  effectiveState: string; state: string; suggestedState: string; approvalStatus: string; basedOnUnconfirmed: boolean
  overridden: boolean; reviewNeeded: boolean; decidedBy: string | null
}

/** Short notes to show beside a row, in the order they matter. */
export function rowNotes(r: Row): string[] {
  const n: string[] = []
  if (r.approvalStatus === "AWAITING") n.push("Removal waits for approval and still counts as applicable")
  if (r.reviewNeeded) n.push("Needs another look: a newer version exists or the review date has come")
  if (r.overridden && r.approvalStatus !== "AWAITING") n.push(`Decided differently from the suggestion (${stateLabel(r.suggestedState).toLowerCase()})`)
  if (r.basedOnUnconfirmed) n.push("Based on profile facts nobody has confirmed yet")
  return n
}
