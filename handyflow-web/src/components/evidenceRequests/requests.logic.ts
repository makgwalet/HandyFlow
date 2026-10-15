import type { EvidenceRequestView, RequestStatus } from "./types"

export const MIN_REASON = 10

export const STATUS_TEXT: Record<RequestStatus, string> = {
  OPEN: "Waiting for the business", SUBMITTED: "Waiting for the auditor", NOT_AVAILABLE: "Marked not available",
  APPROVED: "Approved", CLOSED_NOT_AVAILABLE: "Closed: not available", CANCELLED: "Cancelled",
}

export type Side = "business" | "auditor"

/** Whose turn it is. The tabs and the "needs you" count come from this. */
export function needsAttention(r: EvidenceRequestView, side: Side): boolean {
  if (side === "business") return r.status === "OPEN"
  return r.status === "SUBMITTED" || r.status === "NOT_AVAILABLE"
}

export const isFinished = (r: EvidenceRequestView) => r.status === "APPROVED" || r.status === "CLOSED_NOT_AVAILABLE" || r.status === "CANCELLED"

/** What this side may do with the request right now. */
export function actionsFor(r: EvidenceRequestView, side: Side) {
  const open = r.status === "OPEN"
  const waiting = r.status === "SUBMITTED" || r.status === "NOT_AVAILABLE"
  return {
    upload: side === "business" && open,
    send: side === "business" && open,
    notAvailable: side === "business" && open,
    approve: side === "auditor" && waiting,
    sendBack: side === "auditor" && waiting,
    cancel: !isFinished(r) && r.requestedByKind === (side === "business" ? "STAFF" : "AUDITOR"),
    comment: !isFinished(r),
  }
}

export const reasonOk = (t: string) => t.trim().length >= MIN_REASON

const PRIORITY_RANK = { HIGH: 0, MEDIUM: 1, LOW: 2 } as const
/** Overdue first, then high priority, then soonest due. Finished requests go last. */
export function sortRequests(rs: EvidenceRequestView[]): EvidenceRequestView[] {
  return [...rs].sort((a, b) =>
    Number(isFinished(a)) - Number(isFinished(b)) ||
    Number(b.overdue) - Number(a.overdue) ||
    PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority] ||
    (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999"))
}
