// Types, status colours and form styles shared by the Internal Audit sections.

import type React from "react"

export interface UniverseEntry {
  id: string; name: string; description: string | null; processArea: string | null
  glAccountGroup: string | null; lastAuditDate: string | null; active: boolean
  createdAt: string; currentRiskLevel: string | null
}
export interface RiskAssessment {
  id: string; universeEntryId: string
  inherentRiskScore: number; controlRiskScore: number; historicalFindingsScore: number
  timeSinceLastAuditScore: number; businessRegulatoryImpactScore: number
  systemCalculatedRisk: string; finalAuditRisk: string
  overrideReason: string | null; overrideApprovedBy: string | null
  assessedBy: string | null; assessedAt: string
}
export interface PlanEntry {
  id: string; universeEntryId: string; universeEntryName: string
  riskAssessmentId: string | null; riskLevel: string | null
  plannedQuarter: number | null; rationale: string | null; status: string; createdAt: string
}
export interface AnnualPlan {
  id: string; planYear: number; status: string
  approvedBy: string | null; approvedAt: string | null; createdBy: string | null; createdAt: string
  entries: PlanEntry[]
}
export interface EngagementAssignment {
  id: string; userId: string; userName: string; role: string; assignedBy: string | null; assignedAt: string
}
export interface Engagement {
  id: string; planEntryId: string | null; universeEntryId: string; universeEntryName: string
  name: string; status: string; startDate: string | null; endDate: string | null
  createdBy: string | null; createdAt: string; assignments: EngagementAssignment[]
  objectives: string | null; scope: string | null; auditCriteria: string | null
  overallMateriality: number | null; performanceMateriality: number | null; clearlyTrivialThreshold: number | null
  specificMateriality: SpecificMaterialityResp[]
}
export interface UserOption { id: string; firstName: string; lastName: string; email: string }
export interface SpecificMaterialityResp { id: string; accountOrGlSegment: string; threshold: number }
export interface WorkpaperFolder { id: string; name: string; parentId: string | null; folderType: string | null; sortOrder: number }
export interface WorkpaperFile {
  id: string; folderId: string; fileName: string; mimeType: string | null; fileSizeBytes: number | null
  reviewStatus: string; preparedBy: string | null; preparedAt: string | null
  reviewedBy: string | null; reviewedAt: string | null; signedOffBy: string | null; signedOffAt: string | null
  versionNumber: number; supersededBy: string | null; createdAt: string
}
export interface AuditExceptionResp {
  id: string; auditTestId: string; description: string; severity: string; status: string
  raisedBy: string | null; raisedAt: string; resolutionNotes: string | null
}
export interface FindingResp {
  id: string; engagementId: string; sourceExceptionId: string | null; title: string; description: string
  rootCause: string | null; recommendation: string | null; managementResponse: string | null; severity: string
  owner: string | null; ownerName: string | null; dueDate: string | null; status: string
  createdBy: string | null; createdAt: string; resolvedAt: string | null
  criteria?: string | null; condition?: string | null; consequence?: string | null
}
export interface ReportSignOffStatus { status: string; approvalMode: string | null; locked?: boolean }
export interface AuditTestResp {
  id: string; sampleItemId: string; procedure: string; result: string; notes: string | null
  testedBy: string | null; testedAt: string | null; exceptions: AuditExceptionResp[]
}
export interface SampleItemResp {
  id: string; journalEntryId: string; entryNumberSnapshot: string | null; entryDateSnapshot: string | null
  amountSnapshot: number | null; notes: string | null; selectedAt: string; tests: AuditTestResp[]
}
export interface SamplingPlanResp {
  id: string; engagementId: string
  population: number; populationValue: number | null; samplingObjective: string | null; samplingMethod: string
  riskLevel: string | null; expectedErrorRate: number | null; tolerableErrorRate: number | null
  sampleSize: number; selectionMethod: string; samplePeriodFrom: string | null; samplePeriodTo: string | null
  exclusions: string | null; rationale: string | null; preparedBy: string | null; reviewedBy: string | null
  populationDefinition?: string | null; selectionSeed?: number | null; selectionStart?: number | null
  status: string; createdAt: string; items: SampleItemResp[]
}

// ── Config ─────────────────────────────────────────────────────────────────────

export const RISK_CFG: Record<string, { color: string; bg: string }> = {
  LOW:      { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  MEDIUM:   { color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft)" },
  HIGH:     { color: "var(--hf-orange-text-strong)", bg: "var(--hf-orange-soft)" },
  CRITICAL: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
}
export const ENGAGEMENT_ROLES = ["HEAD_OF_INTERNAL_AUDIT", "AUDIT_MANAGER", "SENIOR_AUDITOR", "AUDITOR", "AUDIT_REVIEWER"]
export const ALLOWED_WP_TYPES = ["application/pdf", "image/jpeg", "image/jpg", "image/png",
  "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"]
export const MAX_WP_BYTES = 10 * 1024 * 1024
export const WP_STATUS_CFG: Record<string, { label: string; color: string; bg: string }> = {
  DRAFT:      { label: "Draft",      color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
  PREPARED:   { label: "Prepared",   color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft)" },
  REVIEWED:   { label: "Reviewed",   color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
  SIGNED_OFF: { label: "Signed Off", color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
}


export const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
export const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "var(--hf-surface)", outline: "none" }
export const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }
export const submitBtn: React.CSSProperties = { padding: "9px 18px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: "pointer" }
export const modalOverlay: React.CSSProperties = { position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }
export const modalBox: React.CSSProperties = { background: "var(--hf-surface)", borderRadius: 14, padding: 26, width: 460, maxHeight: "85vh", overflowY: "auto" as const }
