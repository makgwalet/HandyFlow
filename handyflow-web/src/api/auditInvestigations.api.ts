import { apiClient } from "./client"

export interface InvSummary { id: string; number: string; title: string; status: string; myRole?: string | null; openedAt: string; members: number }
export interface InvMember { userId: string; name: string; role: string; addedAt: string }
export interface InvEvidence { id: string; ref: string; description: string; source?: string | null; collectedAt: string; holderId: string; holderName: string }
export interface InvLog { seq: number; at: string; actorName: string; action: string; detail?: string | null; hash: string }
export interface InvView { summary: InvSummary; summaryText?: string | null; closeReason?: string | null; closedAt?: string | null; members: InvMember[]; evidence: InvEvidence[]; log: InvLog[] }
export interface InvVerification { ok: boolean; entries: number; brokenAtSeq?: number | null }

const base = "/api/v1/internal-audit/investigations"
const rows = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])
// A call that removes the caller from the case returns no body; treat that as "no longer on it".
const view = (d: any): InvView | null => (d && typeof d === "object" && "summary" in d ? d : d?.data ?? null)

export const auditInvestigationsApi = {
  list: () => apiClient.get(base).then(r => rows<InvSummary>(r.data)),
  open: (title: string, summary: string) => apiClient.post(base, { title, summary: summary || null }).then(r => view(r.data) as InvView),
  get: (id: string) => apiClient.get(`${base}/${id}`).then(r => view(r.data) as InvView),
  verify: (id: string) => apiClient.get(`${base}/${id}/verify`).then(r => ((r.data && "ok" in r.data ? r.data : r.data?.data) as InvVerification)),
  addMember: (id: string, userId: string, role: string) => apiClient.post(`${base}/${id}/members`, { userId, role }).then(r => view(r.data) as InvView),
  removeMember: (id: string, userId: string) => apiClient.delete(`${base}/${id}/members/${userId}`).then(r => view(r.data)),
  note: (id: string, text: string) => apiClient.post(`${base}/${id}/notes`, { text }).then(r => view(r.data) as InvView),
  collect: (id: string, description: string, source: string) => apiClient.post(`${base}/${id}/evidence`, { description, source: source || null }).then(r => view(r.data) as InvView),
  transfer: (id: string, evidenceId: string, toUserId: string, reason: string) => apiClient.post(`${base}/${id}/evidence/${evidenceId}/transfer`, { toUserId, reason }).then(r => view(r.data) as InvView),
  setReview: (id: string, underReview: boolean) => apiClient.put(`${base}/${id}/status`, { underReview }).then(r => view(r.data) as InvView),
  close: (id: string, reason: string) => apiClient.post(`${base}/${id}/close`, { reason }).then(r => view(r.data) as InvView),
  users: () => apiClient.get("/api/v1/tasks/assignable-users").then(r => rows<{ id: string; name: string }>(r.data)),
}
