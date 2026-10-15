import { apiClient } from "./client"

export interface FuCounts { total: number; assessed: number; implemented: number; partial: number; notImplemented: number; noLongerApplicable: number; implementedPct: number; complete: boolean }
export interface FuSummary { engagementId: string; engagementName: string; priorEngagementId: string; priorEngagementName: string; counts: FuCounts }
export interface FuItem { findingId: string; title: string; severity?: string | null; findingStatus?: string | null; outcome?: string | null; note?: string | null; assessedByName?: string | null; assessedAt?: string | null }
export interface FuView { summary: FuSummary; items: FuItem[] }

const base = "/api/v1/internal-audit/follow-ups"
const rows = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])
const one = <T,>(d: any): T => (d && typeof d === "object" && "summary" in d ? d : d?.data ?? d) as T

export const auditFollowUpApi = {
  list: () => apiClient.get(base).then(r => rows<FuSummary>(r.data)),
  get: (engagementId: string) => apiClient.get(`${base}/${engagementId}`).then(r => one<FuView>(r.data)),
  start: (engagementId: string, priorEngagementId: string) => apiClient.post(base, { engagementId, priorEngagementId }).then(r => one<FuView>(r.data)),
  assess: (engagementId: string, findingId: string, outcome: string, note: string) =>
    apiClient.put(`${base}/${engagementId}/findings/${findingId}`, { outcome, note }).then(r => one<FuView>(r.data)),
  engagements: () => apiClient.get("/api/v1/internal-audit/engagements").then(r => rows<{ id: string; name: string }>(r.data)),
}
