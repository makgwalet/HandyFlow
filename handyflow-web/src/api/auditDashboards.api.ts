import { apiClient } from "./client"

export interface InternalDashboard {
  plan: { year: number | null; status: string | null; total: number; completed: number; inProgress: number; deferred: number; planned: number; completionPct: number }
  coverage: { universeSize: number; planned: number; audited: number; plannedPct: number; auditedPct: number; notPlanned: string[] }
  findingsBySeverity: { severity: string; open: number; total: number }[]
  openFindings: number
  findingsPastDue: number
  overdueActionCount: number
  overdueActions: { id: string; findingId: string; description: string; dueDate: string; daysOverdue: number }[]
  repeatFindingCount: number
  repeatFindings: { id: string; title: string; severity: string | null; status: string }[]
}

export type TrendKey = "engagements" | "openFindings" | "overdueActions" | "highRiskAreas"
export interface TrendChange { key: TrendKey; now: number; before: number; delta: number }
/** `since`, `daysAgo` null and `changes` empty when nothing saved is old enough to compare with. */
/** SAVED: figures saved on that day. RECONSTRUCTED: worked out from recorded dates, so an estimate. */
export interface DashboardTrends { since: string | null; daysAgo: number | null; changes: TrendChange[]; basis?: "SAVED" | "RECONSTRUCTED" | null }
export interface ActivityItem { actor: string | null; action: string; itemType: string | null; itemId: string | null; subject: string | null; at: string }

export interface ExternalDashboard {
  requests: { awaitingBusiness: number; overdue: number; dueSoon: number; awaitingAuditor: number; rejectedBefore: number; approved: number; notAvailable: number }
  queries: { awaitingBusiness: number; overdue: number; awaitingAuditor: number; reopenedBefore: number; resolved: number }
  reviewNotes: { unresolved: number; overdue: number }
  overdueTotal: number
  engagements: { engagementId: string | null; name: string; status: string | null; openRequests: number; overdueRequests: number; openQueries: number; unresolvedNotes: number }[]
  overdueItems: { type: string; reference: string; title: string; engagement: string; dueDate: string; daysOverdue: number }[]
}

export interface CommitteeReport {
  asOf: string
  plan: { year: number | null; status: string | null; committed: number; completed: number; deferred: number; completionPct: number; quarters: { quarter: number; planned: number; completed: number }[] }
  reports: { approved: number; awaitingSignOff: number; closedWithoutApproval: number; recentlyApproved: { engagement: string; approvedAt: string | null }[] }
  risk: { byLevel: { level: string; count: number }[]; highRiskNotYetAudited: string[] }
  findings: { openHighOrCritical: number; pastDue: number }
  overdueHighRiskActionCount: number
  overdueHighRiskActions: { id: string; description: string; finding: string; severity: string; owner: string | null; dueDate: string; daysOverdue: number }[]
  controls: { open: number; unrated: number; highOrCritical: number; overdue: number; followUpsDue: number; reopenedEver: number }
}

const unwrap = <T,>(d: any): T => (d && typeof d === "object" && "data" in d && !("plan" in d) && !("requests" in d) && !("asOf" in d) ? d.data : d) as T

export const auditDashboardsApi = {
  internal: () => apiClient.get("/api/v1/internal-audit/dashboard").then(r => unwrap<InternalDashboard>(r.data)),
  trends: () => apiClient.get("/api/v1/internal-audit/dashboard/trends").then(r => (r.data?.data ?? r.data) as DashboardTrends),
  activity: (limit = 8) => apiClient.get("/api/v1/internal-audit/dashboard/activity", { params: { limit } }).then(r => (r.data?.data ?? r.data) as ActivityItem[]),
  committee: () => apiClient.get("/api/v1/internal-audit/committee-report").then(r => unwrap<CommitteeReport>(r.data)),
  external: (engagementId?: string) =>
    apiClient.get("/api/v1/auditors/dashboard", { params: engagementId ? { engagementId } : {} }).then(r => unwrap<ExternalDashboard>(r.data)),
}
