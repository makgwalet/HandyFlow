// Pure helpers for the Recalls worklist.
export type RecallFilter = "ALL" | "OVERDUE" | "TODAY" | "NOT_CONTACTED" | "SNOOZED" | "DISMISSED"
export type ContactOutcome = "REACHED" | "NO_ANSWER" | "LEFT_MESSAGE" | "WRONG_NUMBER"

export interface Recall {
  consultationId: string; patientId: string; patientName: string; patientPhone?: string | null
  practitionerId?: string | null; practitionerName?: string | null
  consultedAt: string; followUpDays: number; dueDate: string; overdueDays: number; diagnosis?: string | null
  status: "OPEN" | "SNOOZED" | "DISMISSED"; snoozedUntil?: string | null
  contactAttempts: number; lastContactAt?: string | null; lastContactOutcome?: ContactOutcome | null
}
export interface RecallPage {
  content: Recall[]; page: number; size: number; total: number
  counts: { open: number; overdue: number; dueToday: number; notContacted: number; snoozed: number }
}

export const PAGE_SIZE = 25

export const FILTERS: { id: RecallFilter; label: string }[] = [
  { id: "ALL", label: "To do" }, { id: "OVERDUE", label: "Overdue" }, { id: "TODAY", label: "Due today" },
  { id: "NOT_CONTACTED", label: "Not contacted" }, { id: "SNOOZED", label: "Snoozed" }, { id: "DISMISSED", label: "Dismissed" },
]

export const OUTCOMES: { id: ContactOutcome; label: string }[] = [
  { id: "REACHED", label: "Spoke to patient" }, { id: "NO_ANSWER", label: "No answer" },
  { id: "LEFT_MESSAGE", label: "Left a message" }, { id: "WRONG_NUMBER", label: "Wrong number" },
]
export const outcomeLabel = (o?: string | null) => OUTCOMES.find(x => x.id === o)?.label ?? ""

export function recallsUrl(p: { q: string; filter: RecallFilter; practitionerId: string; page: number }): string {
  const qs = new URLSearchParams({ filter: p.filter, page: String(p.page), size: String(PAGE_SIZE) })
  if (p.q.trim()) qs.set("q", p.q.trim())
  if (p.practitionerId) qs.set("practitionerId", p.practitionerId)
  return `/api/v1/clinic/recalls?${qs}`
}

const pad = (n: number) => String(n).padStart(2, "0")
/** yyyy-mm-dd, `days` from `from` in local time. */
export function dateInDays(days: number, from: Date = new Date()): string {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate() + days)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function pageCount(total: number, size = PAGE_SIZE): number { return Math.max(1, Math.ceil(total / size)) }

/** "3 calls · last: No answer". Empty when never contacted. */
export function contactSummary(r: Pick<Recall, "contactAttempts" | "lastContactOutcome">): string {
  if (!r.contactAttempts) return ""
  return `${r.contactAttempts} ${r.contactAttempts === 1 ? "call" : "calls"} · last: ${outcomeLabel(r.lastContactOutcome) || "logged"}`
}

export function snoozeProblem(until: string, today: string = dateInDays(0)): string | null {
  if (!until) return "Choose a date."
  if (until <= today) return "Choose a date after today."
  if (until > dateInDays(180)) return "Snooze for 180 days at most."
  return null
}
