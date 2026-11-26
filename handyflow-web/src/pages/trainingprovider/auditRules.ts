// Wording and checks for internal quality audits. The provider writes its own checklists and the auditor judges each item. The system never calls
// anyone compliant or competent. The server enforces every rule; these save a round trip.

export type Template = { id: string; templateNo: string; name: string; scopeType: string; status: string; retiredReason: string | null; latestVersionNo: number; latestVersionId: string | null; items: string[]; versionCount: number }
export type AuditSummary = { id: string; auditNo: string; title: string; scopeType: string; targetName: string | null; plannedOn: string; status: string; items: number; answered: number; issues: number; openActions: number }
export type AuditItem = { id: string; position: number; question: string; outcome: string | null; note: string | null; answeredAt: string | null }
export type AuditAction = {
  id: string; itemId: string | null; description: string; ownerName: string; dueOn: string; status: string; overdue: boolean; doneOn: string | null; doneNote: string | null
  verified: boolean; verifiedAlone: boolean; verifiedAloneReason: string | null; cancelledReason: string | null
}
export type AuditDetail = {
  id: string; auditNo: string; title: string; scopeType: string; targetName: string | null; leadUserId: string; plannedOn: string; status: string; startedOn: string | null
  completedOn: string | null; completedAlone: boolean; completedAloneReason: string | null; summary: string | null; cancelledReason: string | null; templateName: string
  templateVersionNo: number; note: string; items: AuditItem[]; actions: AuditAction[]
}

export const MIN_NOTE = 10
export const AUDIT_NOTE = "An audit records what the auditor saw, in their own judgement. It does not show that anyone or anything meets a standard."
export const SCOPES: Record<string, string> = { GENERAL: "General", SITE: "Site", COHORT: "Cohort", PROGRAMME: "Programme" }
export const OUTCOMES: Record<string, string> = { NO_ISSUE: "No issue seen", ISSUE: "Issue", NOTE: "Note", NOT_APPLICABLE: "Not applicable" }
export const AUDIT_STATUS: Record<string, string> = { PLANNED: "Planned", IN_PROGRESS: "In progress", COMPLETED: "Completed", CANCELLED: "Cancelled" }
export const auditTone = (s: string): "good" | "warn" | "neutral" => (s === "COMPLETED" ? "good" : s === "IN_PROGRESS" ? "warn" : "neutral")
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

export const parseItems = (text: string): string[] => text.split(/\r?\n/).map(l => l.trim()).filter(l => l !== "")
export function itemsProblem(text: string): string | null {
  const items = parseItems(text)
  if (items.length === 0) return "Add at least one item, one per line"
  if (items.length > 200) return "A checklist can have at most 200 items"
  if (items.some(i => i.length < 2)) return "Each item needs at least two characters"
  if (new Set(items.map(i => i.toLowerCase())).size !== items.length) return "Two items are the same"
  return null
}
export type TemplateForm = { name: string; scopeType: string; items: string }
export const emptyTemplate: TemplateForm = { name: "", scopeType: "GENERAL", items: "" }
export function templateProblem(f: TemplateForm): string | null {
  if (f.name.trim().length < 2) return "Give the checklist a name"
  if (!SCOPES[f.scopeType]) return "Choose what the checklist is for"
  return itemsProblem(f.items)
}

export type AuditForm = { templateId: string; title: string; scopeType: string; targetId: string; plannedOn: string }
export const emptyAudit = (today: string): AuditForm => ({ templateId: "", title: "", scopeType: "GENERAL", targetId: "", plannedOn: today })
export function auditProblem(f: AuditForm, templateScope: string | null): string | null {
  if (!f.templateId) return "Choose the checklist"
  if (f.title.trim().length < 2) return "Give the audit a title"
  if (f.scopeType !== "GENERAL" && !f.targetId) return `Choose the ${f.scopeType.toLowerCase()} being audited`
  if (templateScope && templateScope !== "GENERAL" && templateScope !== f.scopeType) return `That checklist is for a ${templateScope.toLowerCase()} audit`
  if (!f.plannedOn) return "Give the planned date"
  return null
}
export const auditBody = (f: AuditForm) => ({
  templateId: f.templateId, title: f.title.trim(), scopeType: f.scopeType, plannedOn: f.plannedOn,
  siteId: f.scopeType === "SITE" ? f.targetId : null, cohortId: f.scopeType === "COHORT" ? f.targetId : null, programmeId: f.scopeType === "PROGRAMME" ? f.targetId : null,
})

export function answerProblem(outcome: string, note: string): string | null {
  if (!OUTCOMES[outcome]) return "Choose an outcome"
  if (outcome !== "NO_ISSUE" && note.trim().length < MIN_NOTE) return `Say what you saw, in at least ${MIN_NOTE} characters`
  return null
}
export type ActionForm = { description: string; ownerName: string; dueOn: string }
export function actionProblem(f: ActionForm, today: string): string | null {
  if (f.description.trim().length < 5) return "Say what has to be done"
  if (f.ownerName.trim().length < 2) return "Say who owns the action"
  if (!f.dueOn) return "Give the day it is due"
  if (f.dueOn < today) return "The due date cannot be in the past"
  return null
}
export const noteProblem = (s: string): string | null => (s.trim().length >= MIN_NOTE ? null : `Give a reason or note of at least ${MIN_NOTE} characters`)
export function completeProblem(detail: Pick<AuditDetail, "items" | "actions">, summary: string): string | null {
  const unanswered = detail.items.filter(i => !i.outcome).length
  if (unanswered > 0) return `${unanswered} item(s) are not answered yet`
  const live = detail.actions.filter(a => a.status !== "CANCELLED")
  const noAction = detail.items.filter(i => i.outcome === "ISSUE" && !live.some(a => a.itemId === i.id)).length
  if (noAction > 0) return `${noAction} issue(s) have no corrective action yet`
  if (summary.trim().length < MIN_NOTE) return `Write a summary of at least ${MIN_NOTE} characters`
  return null
}
export const actionState = (a: Pick<AuditAction, "status" | "overdue" | "verified">): string =>
  a.status === "CANCELLED" ? "Cancelled" : a.status === "DONE" ? (a.verified ? "Done and verified" : "Done, not verified") : a.overdue ? "Open, past its due day" : "Open"
