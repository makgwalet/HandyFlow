import { apiClient } from "./client"

export interface PgControl { code: string; domain: string; area: string; objective: string; procedure: string; reference?: string | null }
export interface PgLibrary { domains: Record<string, string>; controls: PgControl[]; notice: string }
export interface PgCounts { total: number; completed: number; notApplicable: number; inProgress: number; notStarted: number; ineffective: number; partial: number; needingFinding: number; donePct: number }
export interface PgStep { controlCode: string; domain: string; area: string; objective: string; procedure: string; reference?: string | null; status: string; conclusion?: string | null; note?: string | null; findingId?: string | null; needsFinding: boolean; updatedByName?: string | null; updatedAt?: string | null }
export interface PgProgramme { engagementId: string; overall: PgCounts; byDomain: Record<string, PgCounts>; steps: PgStep[] }

const base = "/api/v1/internal-audit/programme"
const rows = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])
const prog = (d: any): PgProgramme => (d && "steps" in d ? d : d?.data) as PgProgramme

export const auditProgrammeApi = {
  library: () => apiClient.get(`${base}/library`).then(r => ((r.data && "controls" in r.data ? r.data : r.data?.data) as PgLibrary)),
  get: (engagementId: string) => apiClient.get(`${base}/engagements/${engagementId}`).then(r => prog(r.data)),
  apply: (engagementId: string, domains: string[], codes: string[]) => apiClient.post(`${base}/engagements/${engagementId}/apply`, { domains, codes }).then(r => prog(r.data)),
  record: (engagementId: string, code: string, status: string, conclusion: string | null, note: string) =>
    apiClient.put(`${base}/engagements/${engagementId}/steps/${code}`, { status, conclusion, note: note || null }).then(r => prog(r.data)),
  link: (engagementId: string, code: string, findingId: string | null) => apiClient.put(`${base}/engagements/${engagementId}/steps/${code}/finding`, { findingId }).then(r => prog(r.data)),
  engagements: () => apiClient.get("/api/v1/internal-audit/engagements").then(r => rows<{ id: string; name: string }>(r.data)),
  findings: (engagementId: string) => apiClient.get(`/api/v1/internal-audit/engagements/${engagementId}/findings`).then(r => rows<{ id: string; title: string }>(r.data)),
}
