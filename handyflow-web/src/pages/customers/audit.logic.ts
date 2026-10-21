// The CRM audit log: who changed what. Words, filters and paging; the server keeps and searches the log.
export type AuditEntity = "CUSTOMER" | "OPPORTUNITY" | "COMPANY_GROUP" | "SCORING_RULE" | "ROUTING_RULE" | "WORKFLOW" | "REPORT_SCHEDULE" | "INTEGRATION" | "CAMPAIGN" | "JOURNEY" | "CUSTOM_FIELD" | "SEGMENT"
export const ENTITY_LABEL: Record<AuditEntity, string> = {
  CUSTOMER: "Customer", OPPORTUNITY: "Opportunity", COMPANY_GROUP: "Company group", SCORING_RULE: "Scoring rule", ROUTING_RULE: "Routing rule", WORKFLOW: "Workflow",
  REPORT_SCHEDULE: "Scheduled report", INTEGRATION: "Integration", CAMPAIGN: "Campaign", JOURNEY: "Journey", CUSTOM_FIELD: "Custom field", SEGMENT: "Segment",
}
export const ENTITY_CHOICES = (Object.keys(ENTITY_LABEL) as AuditEntity[]).map(id => ({ id, label: ENTITY_LABEL[id] }))
export type AuditAction = "CREATED" | "UPDATED" | "DELETED" | "STAGE_CHANGED" | "CONVERTED" | "CONVERSION_UNDONE" | "MERGED" | "PARENT_CHANGED" | "REORDERED" | "RUN" | "SENT" | "KEY_CREATED" | "KEY_REVOKED" | "STARTED" | "STOPPED"
export const ACTION_LABEL: Record<AuditAction, string> = {
  CREATED: "created", UPDATED: "changed", DELETED: "deleted", STAGE_CHANGED: "moved", CONVERTED: "converted", CONVERSION_UNDONE: "undid the conversion of", MERGED: "merged",
  PARENT_CHANGED: "regrouped", REORDERED: "reordered", RUN: "ran", SENT: "sent", KEY_CREATED: "created a key for", KEY_REVOKED: "revoked a key of", STARTED: "started", STOPPED: "stopped",
}
export interface AuditEvent { id: string; entityType: AuditEntity; entityId: string | null; entityName: string | null; action: AuditAction; detail: string | null; actorId: string | null; createdAt: string }
export interface AuditPage { content: AuditEvent[]; totalPages: number; totalElements: number; number: number }
export interface AuditFilter { entityType: AuditEntity | ""; actorId: string; from: string; to: string }
export const NO_AUDIT_FILTER: AuditFilter = { entityType: "", actorId: "", from: "", to: "" }
export const AUDIT_PAGE_SIZE = 50

export function filterProblem(f: AuditFilter): string | null {
  if (f.from && f.to && f.from > f.to) return "The start date cannot be after the end date."
  return null
}
/** Only the filters that are set are sent. */
export function auditParams(f: AuditFilter, page: number) {
  const p: Record<string, string | number> = { page, size: AUDIT_PAGE_SIZE }
  if (f.entityType) p.entityType = f.entityType
  if (f.actorId) p.actorId = f.actorId
  if (f.from) p.from = f.from
  if (f.to) p.to = f.to
  return p
}
/** "Sam Seller created Scoring rule "Came from website"". The system does the things automation does. */
export function auditSentence(e: AuditEvent, actor: string | null): string {
  const who = e.actorId == null ? "The system" : actor ?? "Someone"
  const what = `${ENTITY_LABEL[e.entityType] ?? e.entityType}${e.entityName ? ` "${e.entityName}"` : ""}`
  return `${who} ${ACTION_LABEL[e.action] ?? e.action.toLowerCase()} ${what}`
}
export const pageText = (p: AuditPage) => (p.totalElements === 0 ? "No events" : `Page ${p.number + 1} of ${Math.max(p.totalPages, 1)} (${p.totalElements} ${p.totalElements === 1 ? "event" : "events"})`)
