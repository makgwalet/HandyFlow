// Wording and gating for billing events. The server decides what can be raised and checks it against Invoicing; this
// words the stages, orders the worklist and keeps a person from sending an action without what it needs.

export const MIN_NOTE = 10

export const FILTERS: [string, string][] = [["DUE", "To raise now"], ["UPCOMING", "Upcoming"], ["INVOICED", "Invoiced"], ["SKIPPED", "Skipped"], ["", "All"]]

export const STAGE_LABELS: Record<string, string> = {
  OVERDUE_TO_RAISE: "Overdue to raise", DUE: "Ready to raise", UPCOMING: "Upcoming", INVOICED: "Invoiced", SKIPPED: "Skipped",
}
export const stageLabel = (s: string) => STAGE_LABELS[s] ?? s

export type EventAction = { id: "raise" | "skip" | "restore" | "release"; label: string; noteLabel?: string }
const ALL: Record<string, EventAction> = {
  raise: { id: "raise", label: "Raise draft invoice" },
  skip: { id: "skip", label: "Skip", noteLabel: "Why it is skipped" },
  restore: { id: "restore", label: "Bring back" },
  release: { id: "release", label: "Release invoice", noteLabel: "Why the invoice is let go" },
}

/** The server lists what is possible for the bill; people who cannot manage get nothing. */
export function actionsFor(serverActions: string[], canManage: boolean): EventAction[] {
  if (!canManage) return []
  const out: EventAction[] = []
  for (const id of serverActions) { const a = ALL[id]; if (a) out.push(a) }
  return out
}

export function noteProblem(a: EventAction, note: string): string | null {
  if (a.noteLabel && note.trim().length < MIN_NOTE) return `${a.noteLabel} needs at least ${MIN_NOTE} characters`
  return null
}

/** The line shown for where the invoice stands in Invoicing. */
export function invoiceText(number: string | null, info: { status: string; total: number | null; paid: number | null; credited: number | null } | null): string {
  if (!number) return ""
  if (!info) return `${number} · not found in Invoicing`
  const status = info.status.toLowerCase().replace(/_/g, " ")
  const settled = (info.paid ?? 0) + (info.credited ?? 0)
  const owing = info.total === null ? null : Math.round((info.total - settled) * 100) / 100
  return `${number} · ${status}${owing !== null && !["CANCELLED", "DRAFT"].includes(info.status) ? ` · ${owing === 0 ? "settled" : `${owing.toFixed(2)} owing`}` : ""}`
}

/** Most urgent first: overdue to raise, then ready, then upcoming, by date within each. */
export function byUrgency<T extends { stage: string; dueDate: string }>(rows: T[]): T[] {
  const rank: Record<string, number> = { OVERDUE_TO_RAISE: 0, DUE: 1, UPCOMING: 2, INVOICED: 3, SKIPPED: 4 }
  return [...rows].sort((a, b) => (rank[a.stage] ?? 9) - (rank[b.stage] ?? 9) || a.dueDate.localeCompare(b.dueDate))
}
