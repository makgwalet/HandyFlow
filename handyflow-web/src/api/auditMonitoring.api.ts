import { apiClient } from "./client"

export interface MonAlert { id: string; rule: string; severity: "HIGH" | "MEDIUM" | "LOW"; subjectType: string; subjectIds: string[]; summary: string; detail?: string | null; amount?: number | null; occurredOn?: string | null; status: string; firstSeen: string; lastSeen: string; reviewedByName?: string | null; reviewedAt?: string | null; disposition?: string | null; findingId?: string | null }
export interface MonCounts { open: number; high: number; medium: number; low: number; inReview: number; dismissed: number; escalated: number }
export interface MonAlerts { counts: MonCounts; alerts: MonAlert[]; notice: string }
export interface MonScan { found: number; newAlerts: number; seenAgain: number; byRule: Record<string, number>; journalsChecked: number; bankPaymentsChecked: number; peopleChecked: number }

const base = "/api/v1/internal-audit/monitoring"
const rows = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])
const alerts = (d: any): MonAlerts => (d && "alerts" in d ? d : d?.data) as MonAlerts

export const auditMonitoringApi = {
  list: () => apiClient.get(`${base}/alerts`).then(r => alerts(r.data)),
  scan: () => apiClient.post(`${base}/scan`, {}).then(r => ((r.data && "newAlerts" in r.data ? r.data : r.data?.data) as MonScan)),
  review: (id: string, status: string, note: string, findingId: string | null) => apiClient.post(`${base}/alerts/${id}/review`, { status, note: note || null, findingId }).then(r => alerts(r.data)),
  findings: async () => {
    const eng = rows<{ id: string; name: string }>((await apiClient.get("/api/v1/internal-audit/engagements")).data)
    const lists = await Promise.all(eng.map(async e => rows<{ id: string; title: string }>((await apiClient.get(`/api/v1/internal-audit/engagements/${e.id}/findings`)).data).map(f => ({ id: f.id, title: `${e.name}: ${f.title}` }))))
    return lists.flat()
  },
}
