// src/pages/security/incident.logic.ts
//
// Pure rules for the incident page: which action buttons to show (the server decides what is allowed), the form checks
// that mirror the server, and the labels. No React, no network.
import type { Tone } from "./guard360.logic"

export interface IncidentInfo {
  id: string; siteId: string; siteName: string | null; shiftId: string | null; guardId: string | null; guardName: string | null
  title: string; description: string | null; severity: string; status: string; type: string | null
  latitude: number | null; longitude: number | null; acknowledgedAt: string | null; resolvedAt: string | null; reportedAt: string; updatedAt: string
}
export interface IncidentEvent { id: string; eventType: string; toStatus: string | null; note: string | null; byName: string | null; at: string }
export interface IncidentCase {
  incident: IncidentInfo; assigneeName: string | null; assignedAt: string | null; allowedActions: string[]
  events: IncidentEvent[]
  evidence: { id: string; fileName: string; label: string | null; sizeBytes: number; uploadedByName: string | null; createdAt: string }[]
}

export const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
export const SEVERITY_TONE: Record<string, Tone> = { LOW: "neutral", MEDIUM: "info", HIGH: "warn", CRITICAL: "bad" }
export const STATUS_TONE: Record<string, Tone> = { OPEN: "bad", ACKNOWLEDGED: "warn", RESOLVED: "ok" }
export const titleCase = (v: string | null | undefined) => v ? v.charAt(0) + v.slice(1).toLowerCase().replace(/_/g, " ") : "-"

export const ACTION_LABELS: Record<string, string> = {
  ACKNOWLEDGE: "Acknowledge", ASSIGN: "Assign", ESCALATE: "Escalate", RESOLVE: "Resolve", NOTE: "Add note", REOPEN: "Reopen",
}
/** Buttons that open a form or act at once, in the order shown. Evidence has its own panel, so it is not a button. */
const ORDER = ["ACKNOWLEDGE", "ASSIGN", "ESCALATE", "NOTE", "RESOLVE", "REOPEN"]
export function availableActions(allowed: string[]): { action: string; label: string }[] {
  return ORDER.filter(a => allowed.includes(a)).map(action => ({ action, label: ACTION_LABELS[action] }))
}

/** Severities above the current one, for the escalation choice. */
export function severitiesAbove(current: string): string[] {
  const i = SEVERITIES.indexOf(current.toUpperCase())
  return i < 0 ? [] : SEVERITIES.slice(i + 1)
}

export function actionFormError(action: string, f: { text?: string }): string | null {
  const t = (f.text ?? "").trim()
  switch (action) {
    case "ASSIGN": return t ? null : "Say who is dealing with this"
    case "ESCALATE": return t ? null : "Give a reason for escalating"
    case "NOTE": return t ? null : "Write the note"
    case "REOPEN": return t ? null : "Give a reason for reopening"
    default: return null
  }
}

export const EVENT_LABELS: Record<string, string> = {
  REPORTED: "Incident reported", ACKNOWLEDGED: "Acknowledged", RESOLVED: "Resolved", ASSIGNED: "Assigned", ESCALATED: "Escalated",
  NOTE: "Note", REOPENED: "Reopened", EVIDENCE_ADDED: "Evidence added", EVIDENCE_REMOVED: "Evidence removed",
}

/** Minutes from report to a later time, as "12 min", "3 h 5 min" or "2 d 4 h". Null when there is no later time. */
export function elapsed(from: string, to: string | null): string | null {
  if (!to) return null
  const mins = Math.max(0, Math.round((new Date(to).getTime() - new Date(from).getTime()) / 60000))
  if (mins < 60) return `${mins} min`
  if (mins < 1440) return `${Math.floor(mins / 60)} h ${mins % 60} min`
  return `${Math.floor(mins / 1440)} d ${Math.floor((mins % 1440) / 60)} h`
}
