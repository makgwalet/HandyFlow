// src/pages/security/complaints.logic.ts
//
// Pure rules for guard complaints: labels, tones, the form check and the step buttons. The server decides which
// steps are allowed (allowedSteps); this file only describes them. No React, no network.
import type { Tone } from "./guard360.logic"

export interface ComplaintSummary {
  id: string; complaintNumber: string; guardId: string; guardName: string | null; siteId: string | null; siteName: string | null
  occurredOn: string; category: string; severity: string; status: string; finding: string | null; action: string | null
  open: boolean; urgent: boolean; createdAt: string
}
export interface ComplaintEvent { id: string; eventType: string; toStatus: string | null; note: string | null; byName: string | null; at: string }
export interface ComplaintDetail {
  summary: ComplaintSummary; description: string; complainantType: string; complainantName: string | null; complainantContact: string | null
  witnesses: string | null; investigatorName: string | null
  findingNote: string | null; findingByName: string | null; findingAt: string | null
  actionNote: string | null; actionByName: string | null; actionAt: string | null
  resolutionNote: string | null; closedByName: string | null; closedAt: string | null; withdrawnReason: string | null
  createdByName: string | null; editable: boolean; allowedSteps: string[]; events: ComplaintEvent[]
  evidence: { id: string; fileName: string; label: string | null; sizeBytes: number; uploadedByName: string | null; createdAt: string }[]
  hr?: import("./hrLink.logic").HrReferral | null; canReferToHr?: boolean
}
export interface ComplaintCounts { open: number; last90Days: number; substantiatedLast90Days: number }

const opts = (pairs: [string, string][]) => pairs.map(([value, label]) => ({ value, label }))

export const CATEGORIES = opts([
  ["ABSENTEEISM", "Absenteeism"], ["LATENESS", "Lateness"], ["MISCONDUCT", "Misconduct"], ["SLEEPING_ON_DUTY", "Sleeping on duty"],
  ["NEGLIGENCE", "Negligence"], ["POOR_CUSTOMER_SERVICE", "Poor customer service"], ["FAILURE_TO_PATROL", "Failure to patrol"],
  ["FAILURE_TO_FOLLOW_POST_ORDERS", "Failure to follow post orders"], ["DISHONESTY", "Dishonesty"], ["THEFT", "Theft"],
  ["EXCESSIVE_FORCE", "Excessive force"], ["HARASSMENT", "Harassment"], ["INTOXICATION", "Intoxication"],
  ["FIREARM_VIOLATION", "Firearm violation"], ["ACCESS_CONTROL_VIOLATION", "Access control violation"], ["OTHER", "Other"],
])
export const SEVERITIES = opts([["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"], ["CRITICAL", "Critical"]])
export const COMPLAINANT_TYPES = opts([["CLIENT", "Client"], ["SUPERVISOR", "Supervisor"], ["COLLEAGUE", "Colleague"], ["MEMBER_OF_PUBLIC", "Member of the public"], ["INTERNAL", "Internal review"], ["OTHER", "Other"]])
export const FINDINGS = opts([["SUBSTANTIATED", "Substantiated"], ["UNSUBSTANTIATED", "Not substantiated"], ["INCONCLUSIVE", "Inconclusive"]])
export const ACTIONS = opts([
  ["NO_ACTION", "No action"], ["COUNSELLING", "Counselling"], ["VERBAL_WARNING", "Verbal warning"], ["WRITTEN_WARNING", "Written warning"],
  ["FINAL_WARNING", "Final written warning"], ["RETRAINING", "Retraining"], ["SUSPENSION", "Suspension"], ["DISCIPLINARY_HEARING", "Disciplinary hearing"],
])
export const STATUS_FILTERS = opts([
  ["OPEN", "All open"], ["RECEIVED", "Received"], ["UNDER_INVESTIGATION", "Under investigation"], ["FINDING_MADE", "Finding made"],
  ["ACTION_TAKEN", "Action taken"], ["CLOSED", "Closed"], ["WITHDRAWN", "Withdrawn"],
])

const labelIn = (list: { value: string; label: string }[], v: string | null | undefined) => (v ? list.find(o => o.value === v)?.label ?? v.replace(/_/g, " ").toLowerCase() : "-")
export const categoryLabel = (v: string) => labelIn(CATEGORIES, v)
export const severityLabel = (v: string) => labelIn(SEVERITIES, v)
export const findingLabel = (v: string | null) => labelIn(FINDINGS, v)
export const actionLabel = (v: string | null) => labelIn(ACTIONS, v)
export const complainantLabel = (v: string) => labelIn(COMPLAINANT_TYPES, v)
export const statusLabel = (v: string) => labelIn(STATUS_FILTERS, v)

export const COMPLAINT_SEVERITY_TONE: Record<string, Tone> = { LOW: "neutral", MEDIUM: "info", HIGH: "warn", CRITICAL: "bad" }
export function statusTone(status: string, finding: string | null): Tone {
  if (status === "CLOSED") return "ok"
  if (status === "WITHDRAWN") return "neutral"
  if (status === "RECEIVED") return "warn"
  if (status === "FINDING_MADE" && finding === "SUBSTANTIATED") return "bad"
  return "info"
}

/** The next-step buttons, in the order they are shown, limited to what the server allows now. */
export const STEP_LABELS: Record<string, string> = {
  START: "Start investigation", FINDING: "Record finding", ACTION: "Record action", CLOSE: "Close complaint", WITHDRAW: "Withdraw", REOPEN: "Reopen",
}
const STEP_ORDER = ["START", "FINDING", "ACTION", "CLOSE", "WITHDRAW", "REOPEN"]
export function availableSteps(allowed: string[]): { step: string; label: string }[] {
  return STEP_ORDER.filter(s => allowed.includes(s)).map(step => ({ step, label: STEP_LABELS[step] }))
}

/** Where the complaint is on the five-step path, 0 to 4. Withdrawn complaints sit outside it (-1). */
const PATH = ["RECEIVED", "UNDER_INVESTIGATION", "FINDING_MADE", "ACTION_TAKEN", "CLOSED"]
export const PATH_LABELS = ["Received", "Investigation", "Finding", "Action", "Closed"]
export function pathPosition(status: string): number { return PATH.indexOf(status) }

export function complaintFormError(f: { guardId: string; occurredOn: string; description: string }, today: string): string | null {
  if (!f.guardId) return "Choose the guard"
  if (!f.occurredOn) return "Enter the date it happened"
  if (f.occurredOn > today) return "The date cannot be in the future"
  if (!f.description.trim()) return "Describe the complaint"
  return null
}

/** What each step form must contain before it can be sent. Mirrors the server rules. */
export function stepFormError(step: string, f: { finding?: string; action?: string; note?: string }): string | null {
  const note = (f.note ?? "").trim()
  switch (step) {
    case "FINDING": return !f.finding ? "Choose the finding" : !note ? "Record what the investigation found" : null
    case "ACTION":
      if (!f.action) return "Choose the action"
      if (f.action === "NO_ACTION" && f.finding === "SUBSTANTIATED" && !note) return "Say why no action is taken on a substantiated complaint"
      return null
    case "CLOSE": return note ? null : "Say how the complaint was resolved"
    case "WITHDRAW": return note ? null : "Give a reason for withdrawing"
    case "REOPEN": return note ? null : "Give a reason for reopening"
    default: return null
  }
}

/** Actions that fit the finding: an unsubstantiated complaint cannot lead to discipline. */
export function actionsFor(finding: string | null) {
  return finding === "UNSUBSTANTIATED" ? ACTIONS.filter(a => a.value === "NO_ACTION") : ACTIONS
}

export const EVENT_LABELS: Record<string, string> = {
  LOGGED: "Complaint logged", EDITED: "Details updated", INVESTIGATION_STARTED: "Investigation started", FINDING_RECORDED: "Finding recorded",
  ACTION_RECORDED: "Action recorded", CLOSED: "Complaint closed", WITHDRAWN: "Complaint withdrawn", REOPENED: "Complaint reopened", REFERRED_TO_HR: "Referred to HR", EVIDENCE_ADDED: "Evidence added", EVIDENCE_REMOVED: "Evidence removed",
}
