import { apiClient } from "./client"

export interface ReportSummary { id: string; engagementId: string; engagementName: string; version: number; title: string; template: string; status: string; createdAt: string; issuedAt?: string | null; openItems: number }
export interface ReportSection { position: number; title: string; body: string; autoFindings: boolean }
export interface ReportItem { id: string; sectionPosition?: number | null; comment: string; raisedByName: string; raisedAt: string; status: "OPEN" | "ACCEPTED" | "DECLINED"; response?: string | null; respondedByName?: string | null }
export interface ReportPerson { id: string; kind: "REVIEWER" | "RECIPIENT"; userId: string; name: string; addedAt: string; firstViewedAt?: string | null; lastViewedAt?: string | null; viewCount: number }
export interface ReportView { summary: ReportSummary; contentHash?: string | null; manager: boolean; canEdit: boolean; sections: ReportSection[]; items: ReportItem[]; people: ReportPerson[] }
export interface ReportTemplate { id: string; label: string; sections: string[] }

const base = "/api/v1/internal-audit/reports"
const rows = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])
const one = <T,>(d: any): T => (d && typeof d === "object" && "summary" in d ? d : d?.data ?? d) as T

export const auditReportsApi = {
  templates: () => apiClient.get(`${base}/templates`).then(r => rows<ReportTemplate>(r.data)),
  list: (engagementId?: string) => apiClient.get(base, { params: engagementId ? { engagementId } : {} }).then(r => rows<ReportSummary>(r.data)),
  get: (id: string) => apiClient.get(`${base}/${id}`).then(r => one<ReportView>(r.data)),
  create: (engagementId: string, template: string, title: string) =>
    apiClient.post(base, { engagementId, template, title: title || null }).then(r => one<ReportView>(r.data)),
  newVersion: (id: string) => apiClient.post(`${base}/${id}/new-version`).then(r => one<ReportView>(r.data)),
  editSection: (id: string, position: number, title: string, body: string) =>
    apiClient.put(`${base}/${id}/sections/${position}`, { title, body }).then(r => one<ReportView>(r.data)),
  addPerson: (id: string, kind: "REVIEWER" | "RECIPIENT", userId: string) =>
    apiClient.post(`${base}/${id}/people`, { kind, userId }).then(r => one<ReportView>(r.data)),
  removePerson: (id: string, personId: string) => apiClient.delete(`${base}/${id}/people/${personId}`).then(r => one<ReportView>(r.data)),
  startReview: (id: string) => apiClient.post(`${base}/${id}/factual-review/start`).then(r => one<ReportView>(r.data)),
  raiseItem: (id: string, sectionPosition: number | null, comment: string) =>
    apiClient.post(`${base}/${id}/factual-review/items`, { sectionPosition, comment }).then(r => one<ReportView>(r.data)),
  respond: (id: string, itemId: string, decision: "ACCEPTED" | "DECLINED", response: string) =>
    apiClient.post(`${base}/${id}/factual-review/items/${itemId}/respond`, { decision, response }).then(r => one<ReportView>(r.data)),
  closeReview: (id: string) => apiClient.post(`${base}/${id}/factual-review/close`).then(r => one<ReportView>(r.data)),
  reopen: (id: string) => apiClient.post(`${base}/${id}/reopen`).then(r => one<ReportView>(r.data)),
  issue: (id: string) => apiClient.post(`${base}/${id}/issue`).then(r => one<ReportView>(r.data)),
  users: () => apiClient.get("/api/v1/tasks/assignable-users").then(r => rows<{ id: string; name: string }>(r.data)),
  engagements: () => apiClient.get("/api/v1/internal-audit/engagements").then(r => rows<{ id: string; name: string }>(r.data)),
}
