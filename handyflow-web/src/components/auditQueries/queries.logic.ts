import type { AuditQueryView, QueryKind, QueryStatus } from "./types"

export const MIN_TEXT = 10
export type Side = "business" | "auditor"

export const KIND_TEXT: Record<QueryKind, string> = { QUERY: "Query", REVIEW_NOTE: "Review note" }
export const STATUS_TEXT: Record<QueryStatus, string> = { OPEN: "Waiting for the business", ANSWERED: "Waiting for the auditor", RESOLVED: "Resolved", WITHDRAWN: "Withdrawn" }
export const SUBJECTS: [string, string][] = [
  ["", "Nothing in particular"], ["JOURNAL_ENTRY", "A journal entry"], ["INVOICE", "An invoice"], ["BILL", "A bill"], ["PAYMENT", "A payment"],
  ["EVIDENCE", "A document"], ["EVIDENCE_REQUEST", "An evidence request"], ["ACCOUNT", "An account"], ["OTHER", "Something else"],
]

export const textOk = (t: string) => t.trim().length >= MIN_TEXT
export const isFinished = (q: AuditQueryView) => q.status === "RESOLVED" || q.status === "WITHDRAWN"

/** Whose turn it is: the business answers open ones, the auditor reviews answered ones. */
export const needsAttention = (q: AuditQueryView, side: Side) => (side === "business" ? q.status === "OPEN" : q.status === "ANSWERED")

export function actionsFor(q: AuditQueryView, side: Side) {
  return {
    answer: side === "business" && q.status === "OPEN",
    link: side === "business",
    followUp: side === "auditor" && q.status === "ANSWERED",
    resolve: side === "auditor" && q.status === "ANSWERED",
    withdraw: side === "auditor" && !isFinished(q),
  }
}

/** Overdue first, then ones waiting on the viewer, then newest. Finished last. */
export function sortQueries(qs: AuditQueryView[], side: Side): AuditQueryView[] {
  return [...qs].sort((a, b) =>
    Number(isFinished(a)) - Number(isFinished(b)) ||
    Number(b.overdue) - Number(a.overdue) ||
    Number(needsAttention(b, side)) - Number(needsAttention(a, side)) ||
    b.createdAt.localeCompare(a.createdAt))
}
