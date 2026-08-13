// src/api/recruitmentAgency.api.ts — staff-facing API client
import { apiClient } from "./client"
import type {
  AgencyClient, Requisition, Candidate, Placement, StageHistory, AgencyInvoice, PortalAccessGrant,
} from "../types/recruitmentAgency.types"

interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export const recruitmentAgencyApi = {
  // Profile
  getProfile: () => apiClient.get("/api/v1/recruitment-agency/profile").then(r => r.data.data),
  upsertProfile: (body: any) => apiClient.put("/api/v1/recruitment-agency/profile", body).then(r => r.data.data),

  // Clients
  getClients: (page = 0, size = 50) =>
    apiClient.get(`/api/v1/recruitment-agency/clients?page=${page}&size=${size}`).then(r => r.data.data as PageResponse<AgencyClient>),
  createClient: (body: Partial<AgencyClient>) =>
    apiClient.post("/api/v1/recruitment-agency/clients", body).then(r => r.data.data as AgencyClient),
  updateClient: (id: string, body: Partial<AgencyClient>) =>
    apiClient.put(`/api/v1/recruitment-agency/clients/${id}`, body).then(r => r.data.data as AgencyClient),
  deactivateClient: (id: string) => apiClient.post(`/api/v1/recruitment-agency/clients/${id}/deactivate`).then(r => r.data.data),
  reactivateClient: (id: string) => apiClient.post(`/api/v1/recruitment-agency/clients/${id}/reactivate`).then(r => r.data.data),

  // Requisitions
  getRequisitionsForClient: (clientId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/clients/${clientId}/requisitions`).then(r => r.data.data as Requisition[]),
  getRequisition: (id: string) =>
    apiClient.get(`/api/v1/recruitment-agency/requisitions/${id}`).then(r => r.data.data as Requisition),
  createRequisition: (body: Partial<Requisition> & { clientId: string; title: string }) =>
    apiClient.post("/api/v1/recruitment-agency/requisitions", body).then(r => r.data.data as Requisition),
  cancelRequisition: (id: string) => apiClient.post(`/api/v1/recruitment-agency/requisitions/${id}/cancel`).then(r => r.data.data),

  // Candidates — agency-wide pool, NOT client-scoped
  searchCandidates: (search: string, page = 0) =>
    apiClient.get(`/api/v1/recruitment-agency/candidates?search=${encodeURIComponent(search)}&page=${page}`).then(r => r.data.data as PageResponse<Candidate>),
  createCandidate: (body: Partial<Candidate>) =>
    apiClient.post("/api/v1/recruitment-agency/candidates", body).then(r => r.data.data as Candidate),
  uploadCv: (candidateId: string, file: File) => {
    const form = new FormData()
    form.append("file", file)
    return apiClient.post(`/api/v1/recruitment-agency/candidates/${candidateId}/cv`, form,
      { headers: { "Content-Type": "multipart/form-data" } }).then(r => r.data.data as Candidate)
  },
  downloadCv: (candidateId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/candidates/${candidateId}/cv`, { responseType: "blob" }).then(r => r.data as Blob),

  // Placements / pipeline
  submitCandidate: (requisitionId: string, candidateId: string) =>
    apiClient.post(`/api/v1/recruitment-agency/requisitions/${requisitionId}/submit-candidate`, { candidateId }).then(r => r.data.data as Placement),
  getPlacements: (requisitionId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/requisitions/${requisitionId}/placements`).then(r => r.data.data as Placement[]),
  advanceStage: (placementId: string, toStage: string, notes?: string) =>
    apiClient.post(`/api/v1/recruitment-agency/placements/${placementId}/advance-stage`, { toStage, notes }).then(r => r.data.data as Placement),
  markPlaced: (placementId: string, offeredSalary: number) =>
    apiClient.post(`/api/v1/recruitment-agency/placements/${placementId}/mark-placed`, { offeredSalary }).then(r => r.data.data as Placement),
  failGuarantee: (placementId: string, reason: string) =>
    apiClient.post(`/api/v1/recruitment-agency/placements/${placementId}/fail-guarantee`, { reason }).then(r => r.data.data as Placement),
  getStageHistory: (placementId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/placements/${placementId}/stage-history`).then(r => r.data.data as StageHistory[]),

  // Billing
  generateInvoice: (placementId: string, body: { invoiceDate: string; dueDate: string; includeVat: boolean }) =>
    apiClient.post(`/api/v1/recruitment-agency/placements/${placementId}/invoice`, body).then(r => r.data.data as AgencyInvoice),
  getInvoices: (clientId: string, page = 0) =>
    apiClient.get(`/api/v1/recruitment-agency/clients/${clientId}/invoices?page=${page}`).then(r => r.data.data as PageResponse<AgencyInvoice>),
  sendInvoice: (id: string) => apiClient.post(`/api/v1/recruitment-agency/invoices/${id}/send`).then(r => r.data.data as AgencyInvoice),
  recordPayment: (id: string, body: { amount: number; paidDate: string; method?: string; reference?: string }) =>
    apiClient.post(`/api/v1/recruitment-agency/invoices/${id}/payments`, body).then(r => r.data.data as AgencyInvoice),

  // Portal invites
  getPortalAccessGrants: (clientId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/clients/${clientId}/portal-invites`).then(r => r.data.data as PortalAccessGrant[]),
  invitePortalUser: (clientId: string, email: string) =>
    apiClient.post(`/api/v1/recruitment-agency/clients/${clientId}/portal-invites`, { email }).then(r => r.data.data as PortalAccessGrant),
  revokePortalAccess: (clientId: string, grantId: string) =>
    apiClient.post(`/api/v1/recruitment-agency/clients/${clientId}/portal-invites/${grantId}/revoke`).then(r => r.data.data as PortalAccessGrant),
}
