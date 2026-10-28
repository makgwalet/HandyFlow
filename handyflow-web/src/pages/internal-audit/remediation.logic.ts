// What the Findings & Remediation screen shows and how it narrows the lists. No server calls.

import type { RemediationAction, RemediationFinding } from "../../api/auditRemediation.api"
import type { Tone } from "./auditUi.logic"

export const FINDING_STATUS: Record<string, { label: string; tone: Tone }> = {
  OPEN: { label: "Open", tone: "info" },
  IN_PROGRESS: { label: "In progress", tone: "warning" },
  RESOLVED: { label: "Resolved", tone: "success" },
  CLOSED: { label: "Closed", tone: "muted" },
}
export const ACTION_STATUS: Record<string, { label: string; tone: Tone }> = {
  OPEN: { label: "Open", tone: "info" },
  COMPLETED: { label: "Awaiting validation", tone: "warning" },
  VALIDATED: { label: "Validated", tone: "success" },
  CANCELLED: { label: "Cancelled", tone: "muted" },
}
export const findingStatus = (s: string) => FINDING_STATUS[s] ?? { label: s, tone: "muted" as Tone }
export const actionStatus = (s: string) => ACTION_STATUS[s] ?? { label: s, tone: "muted" as Tone }

export const severityLabel = (s: string | null) => (s ? s.charAt(0).toUpperCase() + s.slice(1).toLowerCase() : "Unrated")
export const severityTone = (s: string | null): Tone => {
  switch ((s ?? "").toUpperCase()) { case "CRITICAL": case "HIGH": return "warning"; case "LOW": return "success"; case "MEDIUM": return "info"; default: return "muted" }
}

export type FindingFilter = { severity: string; status: "all" | "open" | "settled" | "pastDue" | "noAction"; q: string }
export type ActionFilter = { status: "all" | "overdue" | "open" | "awaiting" | "validated"; q: string }

const has = (hay: (string | null | undefined)[], q: string) => { const n = q.trim().toLowerCase(); return n === "" || hay.some(h => (h ?? "").toLowerCase().includes(n)) }
const isOpen = (s: string) => s === "OPEN" || s === "IN_PROGRESS"

export function filterFindings(rows: RemediationFinding[], f: FindingFilter): RemediationFinding[] {
  return rows.filter(r =>
    (f.severity === "all" || (r.severity ?? "UNRATED").toUpperCase() === f.severity) &&
    (f.status === "all" ||
      (f.status === "open" && isOpen(r.status)) ||
      (f.status === "settled" && !isOpen(r.status)) ||
      (f.status === "pastDue" && r.pastDue) ||
      (f.status === "noAction" && isOpen(r.status) && r.actions === 0)) &&
    has([r.title, r.engagement, r.area, r.owner], f.q))
}

export function filterActions(rows: RemediationAction[], f: ActionFilter): RemediationAction[] {
  return rows.filter(r =>
    (f.status === "all" ||
      (f.status === "overdue" && r.overdue) ||
      (f.status === "open" && r.status === "OPEN") ||
      (f.status === "awaiting" && r.status === "COMPLETED") ||
      (f.status === "validated" && r.status === "VALIDATED")) &&
    has([r.description, r.finding, r.engagement, r.owner], f.q))
}

/** "Due in 3 days", "Due today", "5 days late", or nothing without a date. */
export function dueText(due: string | null, today: string): string {
  if (!due) return ""
  const [y1, m1, d1] = today.split("-").map(Number), [y2, m2, d2] = due.slice(0, 10).split("-").map(Number)
  const n = Math.round((Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86400000)
  if (n === 0) return "Due today"
  if (n === 1) return "Due tomorrow"
  return n > 0 ? `Due in ${n} days` : `${-n} ${-n === 1 ? "day" : "days"} late`
}
