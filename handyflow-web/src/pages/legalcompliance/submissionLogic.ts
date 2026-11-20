// Pure helpers for provider submissions (kept out of the component files so react-refresh stays happy).

export type Scope = "SUMMARY" | "REPORTS" | "SUBMIT"
export type Kind = "OBSERVATION" | "RECOMMENDATION" | "INFORMATION_REQUEST"
export type Area = "OBLIGATIONS" | "RISKS" | "CONTROLS" | "POLICIES" | "PRIVACY" | "INCIDENTS" | "GOVERNANCE" | "OTHER"
export type Priority = "LOW" | "NORMAL" | "HIGH"
export type SubStatus = "SUBMITTED" | "ACCEPTED" | "DECLINED" | "COMPLETED" | "WITHDRAWN"

export const KINDS: Kind[] = ["OBSERVATION", "RECOMMENDATION", "INFORMATION_REQUEST"]
export const AREAS: Area[] = ["OBLIGATIONS", "RISKS", "CONTROLS", "POLICIES", "PRIVACY", "INCIDENTS", "GOVERNANCE", "OTHER"]
export const PRIORITIES: Priority[] = ["LOW", "NORMAL", "HIGH"]

export const MIN_TITLE = 5, MIN_BODY = 20, MAX_BODY = 4000, MIN_REPLY = 10

export const label = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")

export const STATUS_TONE: Record<SubStatus, { color: string; bg: string }> = {
  SUBMITTED: { color: "#92400E", bg: "#FEF3C7" }, ACCEPTED: { color: "#3730A3", bg: "#E0E7FF" }, DECLINED: { color: "#475569", bg: "#E2E8F0" },
  COMPLETED: { color: "#166534", bg: "#DCFCE7" }, WITHDRAWN: { color: "#475569", bg: "#F1F5F9" },
}

/** Sending is possible only while access is active and the client granted SUBMIT. */
export function canSend(status: string, granted: Scope[]): boolean { return status === "ACTIVE" && granted.includes("SUBMIT") }

/** What the submission form still needs, or null when it can be sent. Mirrors the server's limits so the person is told before the round trip. */
export function formProblem(f: { title: string; body: string; respondBy: string }, today: string): string | null {
  if (f.title.trim().length < MIN_TITLE) return `Give it a title of at least ${MIN_TITLE} characters`
  if (f.body.trim().length < MIN_BODY) return `Describe it in at least ${MIN_BODY} characters`
  if (f.body.trim().length > MAX_BODY) return `The description is too long (${MAX_BODY} characters at most)`
  if (f.respondBy && f.respondBy <= today) return "The date you want a reply by must be after today"
  return null
}

/** Accepting needs no note; declining and completing need a real reply. */
export function replyValid(action: "accept" | "decline" | "complete", note: string): boolean {
  return action === "accept" || note.trim().length >= MIN_REPLY
}

/** The grants the client may choose from are exactly what the provider asked for. Toggling off the last one is not allowed (end the access instead). */
export function toggleGrant(current: Scope[], scope: Scope, on: boolean): Scope[] {
  const next = on ? Array.from(new Set([...current, scope])) : current.filter(s => s !== scope)
  return next
}

export function grantsChanged(a: Scope[], b: Scope[]): boolean {
  return [...a].sort().join() !== [...b].sort().join()
}


export function dueText(respondBy: string | null, overdue: boolean): string | null {
  if (!respondBy) return null
  return overdue ? `Reply was wanted by ${respondBy}` : `Reply wanted by ${respondBy}`
}
