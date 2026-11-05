// src/api/auditTrail.api.ts
//
// Client for the audit trail. Needs AUDIT_ADMIN. The sole-approver report is the same endpoint filtered by action.
import { apiClient } from "./client"

export interface AuditEvent {
  id: string
  actorType: string
  actorId?: string | null
  actorName?: string | null
  action: string
  itemType: string
  itemId?: string | null
  beforeJson?: string | null
  afterJson?: string | null
  at: string
}

export const SOLE_APPROVER_ACTION = "SOLE_APPROVER_SELF_APPROVAL"

export const auditTrailApi = {
  /** Steps done by the only person who could do them (no second person existed). Newest first. */
  soleApprover: (limit = 500) =>
    apiClient
      .get("/api/v1/audit-trail", { params: { action: SOLE_APPROVER_ACTION, limit } })
      .then(r => (r.data?.data ?? r.data) as AuditEvent[]),
}
