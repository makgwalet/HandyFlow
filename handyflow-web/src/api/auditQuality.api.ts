import { apiClient } from "./client"

export interface QrCheck { code: string; area: string; question: string; result?: "YES" | "NO" | "NA" | null; note?: string | null; answeredAt?: string | null }
export interface QrSummary { id: string; engagementId: string; engagementName: string; reviewerName: string; reviewerId: string; requestedByName: string; requestedAt: string; status: string; conclusion?: string | null; completedAt?: string | null; answered: number; total: number; noAnswers: number }
export interface QrView { summary: QrSummary; conclusionNote?: string | null; cancelReason?: string | null; checks: QrCheck[] }
export interface QualityMetrics {
  reviewsCompleted: number; reviewsLive: number; satisfactoryPct: number
  byConclusion: { conclusion: string; count: number }[]
  averageDaysToComplete: number | null
  mostFailedChecks: { code: string; question: string; count: number }[]
  issuedWithoutCompletedReview: number
  reviewNotesRaised: number; reviewNotesCleared: number; reviewNotesClearedPct: number
  findingsReopened: number; findingsTotal: number; findingsReopenedPct: number
}

const base = "/api/v1/internal-audit/quality"
const rows = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])
const one = <T,>(d: any, key: string): T => (d && typeof d === "object" && key in d ? d : d?.data ?? d) as T

export const auditQualityApi = {
  list: () => apiClient.get(`${base}/reviews`).then(r => rows<QrSummary>(r.data)),
  get: (id: string) => apiClient.get(`${base}/reviews/${id}`).then(r => one<QrView>(r.data, "summary")),
  request: (engagementId: string, reviewerId: string) => apiClient.post(`${base}/reviews`, { engagementId, reviewerId }).then(r => one<QrView>(r.data, "summary")),
  answer: (id: string, code: string, result: string, note: string) =>
    apiClient.put(`${base}/reviews/${id}/checks/${code}`, { result, note: note || null }).then(r => one<QrView>(r.data, "summary")),
  complete: (id: string, conclusion: string, note: string) => apiClient.post(`${base}/reviews/${id}/complete`, { conclusion, note }).then(r => one<QrView>(r.data, "summary")),
  cancel: (id: string, reason: string) => apiClient.post(`${base}/reviews/${id}/cancel`, { reason }).then(r => one<QrView>(r.data, "summary")),
  metrics: () => apiClient.get(`${base}/metrics`).then(r => one<QualityMetrics>(r.data, "reviewsCompleted")),
  users: () => apiClient.get("/api/v1/tasks/assignable-users").then(r => rows<{ id: string; name: string }>(r.data)),
  engagements: () => apiClient.get("/api/v1/internal-audit/engagements").then(r => rows<{ id: string; name: string }>(r.data)),
}
