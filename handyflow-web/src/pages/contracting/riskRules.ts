// Shared constants and types for risk rules (kept out of the component files so fast refresh keeps working).
export interface RiskRule {
  id: string; familyId: string; versionNo: number; name: string; description?: string | null; ruleType: string; paramText?: string | null; paramNumber?: number | null
  appliesToTypes?: string | null; severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'; blocksSending: boolean; message: string; remediation?: string | null
  legalArea?: string | null; jurisdiction: string; status: 'DRAFT' | 'APPROVED' | 'PUBLISHED' | 'SUPERSEDED' | 'WITHDRAWN'
  legalStatus: 'NOT_REVIEWED' | 'REVIEWED' | 'REVIEW_EXPIRED' | 'SUPERSEDED' | 'WITHDRAWN'
  reviewerName?: string | null; reviewerProfessionalDetails?: string | null; reviewDate?: string | null; nextReviewDate?: string | null
  reviewNotes?: string | null; withdrawnReason?: string | null; active: boolean
}

export interface RiskFinding { ruleId: string; ruleName: string; ruleVersion: number; severity: string; message: string; remediation?: string | null; blocksSending: boolean; reviewedBy?: string | null; reviewDate?: string | null }
export interface IntegrityFinding { clauseId: string; clauseTitle: string; clauseVersion: number; state: string; message: string }
export interface RiskAssessment {
  id: string; contractId: string; contractVersionNo?: number | null; riskLevel: 'NOT_ASSESSED' | 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  rulesEvaluated: number; rulesSkipped: number; findings: RiskFinding[]; integrityFindings: IntegrityFinding[]; assessedAt: string
}

/** [type, label, kind] where kind says which value the rule needs. */
export const RULE_TYPES: ReadonlyArray<readonly [string, string, 'clause' | 'text' | 'number' | 'none']> = [
  ['REQUIRES_CLAUSE_CATEGORY', 'Must contain a library clause of category', 'clause'],
  ['REQUIRES_PHRASE', 'Wording must contain', 'text'],
  ['FORBIDS_PHRASE', 'Wording must not contain', 'text'],
  ['VALUE_ABOVE', 'Contract value above (rand)', 'number'],
  ['TERM_LONGER_THAN_MONTHS', 'Term longer than (months)', 'number'],
  ['AUTO_RENEW_NOTICE_BELOW_DAYS', 'Auto-renews with notice shorter than (days)', 'number'],
  ['REQUIRES_END_DATE', 'Must have an end date', 'none'],
  ['REQUIRES_VALUE', 'Must have a value', 'none'],
]

export const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const

export const LEVEL_LABEL: Record<string, string> = {
  NOT_ASSESSED: 'Not assessed', NONE: 'No rule failed', LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical',
}
export const LEVEL_COLOR: Record<string, string> = {
  NOT_ASSESSED: 'var(--hf-text-muted)', NONE: 'var(--hf-success-text-strong)', LOW: 'var(--hf-text-secondary)', MEDIUM: 'var(--hf-warning-text)', HIGH: 'var(--hf-danger-text)', CRITICAL: 'var(--hf-danger-text)',
}
