// What the cases screen lets a person do next. The server enforces every rule; this only keeps obviously wrong
// clicks from being sent and explains why a button is held back.

export const MIN_REASON = 15

export const STATUS_LABELS: Record<string, string> = {
  OPEN: "Open", IN_PROGRESS: "In progress", AWAITING_CLIENT: "Waiting on the client", RESOLVED: "Resolved", CLOSED: "Closed", CANCELLED: "Cancelled",
}
export const statusLabel = (s: string) => STATUS_LABELS[s] ?? s

export const SEVERITIES: [string, string][] = [["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"], ["CRITICAL", "Critical"]]
export const severityLabel = (s: string) => SEVERITIES.find(x => x[0] === s)?.[1] ?? s

export const isLive = (status: string) => status === "OPEN" || status === "IN_PROGRESS" || status === "AWAITING_CLIENT" || status === "RESOLVED"
/** Steps and owner can change only until the case is closed or cancelled. Resolved cases can still be reopened. */
export const isEditable = (status: string) => status === "OPEN" || status === "IN_PROGRESS" || status === "AWAITING_CLIENT"

export type Action = { action: string; label: string; needsNote: boolean }

const ACTIONS: Record<string, Action[]> = {
  OPEN: [{ action: "START", label: "Start work", needsNote: false }, { action: "CANCEL", label: "Cancel", needsNote: true }],
  IN_PROGRESS: [
    { action: "AWAIT_CLIENT", label: "Waiting on the client", needsNote: false },
    { action: "RESOLVE", label: "Resolve", needsNote: true },
    { action: "CANCEL", label: "Cancel", needsNote: true },
  ],
  AWAITING_CLIENT: [
    { action: "RESUME", label: "Client replied, resume", needsNote: false },
    { action: "RESOLVE", label: "Resolve", needsNote: true },
    { action: "CANCEL", label: "Cancel", needsNote: true },
  ],
  RESOLVED: [{ action: "CLOSE", label: "Close", needsNote: false }, { action: "REOPEN", label: "Reopen", needsNote: true }],
}
export const actionsFor = (status: string): Action[] => ACTIONS[status] ?? []

export type CaseLike = { status: string; ownerId: string | null; openSteps: number; resolvedBy?: string | null }

/** Why an action cannot be sent yet, or null when it can. */
export function actionProblem(a: Action, c: CaseLike, note: string, me: string | null | undefined): string | null {
  const n = note.trim()
  if (a.action === "START" && !c.ownerId) return "Give the case an owner first"
  if (a.action === "RESOLVE") {
    if (c.openSteps > 0) return `${c.openSteps} ${c.openSteps === 1 ? "step is" : "steps are"} still open. Finish or cancel ${c.openSteps === 1 ? "it" : "them"}`
    if (n.length < MIN_REASON) return `Say how it was resolved, in at least ${MIN_REASON} characters`
  }
  if (a.action === "CLOSE" && me && c.resolvedBy === me && n.length < MIN_REASON) {
    return `You resolved this case, so someone else should close it. If no one else can, give a reason of at least ${MIN_REASON} characters`
  }
  if ((a.action === "CANCEL" || a.action === "REOPEN") && n.length < MIN_REASON) return `Say why, in at least ${MIN_REASON} characters`
  return null
}

/** The note box matters when the action needs one, or when closing your own resolution. */
export const showsNote = (a: Action, c: CaseLike, me: string | null | undefined) =>
  a.needsNote || (a.action === "CLOSE" && !!me && c.resolvedBy === me)

export function stepProblem(description: string): string | null {
  const t = description.trim()
  return t.length < 3 ? "Describe the step in at least 3 characters" : t.length > 500 ? "A step can be at most 500 characters" : null
}

export function dueText(due: string | null, today: string, status: string): string {
  if (!due) return "No due date"
  if (!isEditable(status)) return `Due ${due}`
  const d = Math.round((Date.parse(due) - Date.parse(today)) / 86400000)
  if (d < 0) return `${-d} ${d === -1 ? "day" : "days"} overdue`
  if (d === 0) return "Due today"
  return `Due in ${d} ${d === 1 ? "day" : "days"}`
}

export const EVENT_LABELS: Record<string, string> = {
  OPENED: "Opened", START: "Work started", AWAIT_CLIENT: "Waiting on the client", RESUME: "Resumed", RESOLVE: "Resolved", CLOSE: "Closed",
  REOPEN: "Reopened", CANCEL: "Cancelled", OWNER_CHANGED: "Owner changed", DUE_CHANGED: "Due date changed", STEP_ADDED: "Step added",
  STEP_DONE: "Step done", STEP_CANCELLED: "Step cancelled",
}
export const eventLabel = (e: string) => EVENT_LABELS[e] ?? e

/** Items an assessment can raise a case for. Mirrors the server's list of problem statuses. */
export const PROBLEM_STATUSES = ["NON_COMPLIANT", "PARTIALLY_COMPLIANT", "EVIDENCE_MISSING", "EXPIRED", "UNDER_REVIEW"]
export const isProblem = (status: string) => PROBLEM_STATUSES.includes(status)
