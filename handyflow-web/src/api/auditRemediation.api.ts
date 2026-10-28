import { apiClient } from "./client"

export interface RemediationFinding {
  id: string; engagementId: string; engagement: string | null; area: string | null; title: string; description: string | null
  recommendation: string | null; managementResponse: string | null; severity: string | null; status: string; owner: string | null
  dueDate: string | null; pastDue: boolean; repeat: boolean
  actions: number; actionsDone: number; actionsAwaiting: number; actionsOverdue: number; remediationPct: number; createdAt: string | null
}
export interface RemediationAction {
  id: string; findingId: string; finding: string; severity: string | null; engagement: string | null; description: string
  owner: string | null; dueDate: string | null; status: string; overdue: boolean; daysOverdue: number
}
export interface RemediationSummary {
  findings: number; open: number; inProgress: number; resolved: number; closed: number
  openCritical: number; openHigh: number; openMedium: number; openLow: number; pastDue: number; openWithoutAction: number
  actions: number; actionsOpen: number; actionsAwaiting: number; actionsValidated: number; actionsOverdue: number; remediationPct: number
}
export interface RemediationBoard { summary: RemediationSummary; findings: RemediationFinding[]; actions: RemediationAction[] }

export const auditRemediationApi = {
  board: () => apiClient.get("/api/v1/internal-audit/remediation").then(r => (r.data?.data ?? r.data) as RemediationBoard),
}
