// src/api/recruitmentAgencyPortal.api.ts
import { apiClient } from "./client"
import type { PortalClientSummary, Requisition, Placement, AgencyInvoice } from "../types/recruitmentAgencyPortal.types"

interface PortalAuthResponse { token: string; portalUserId: string; email: string; fullName: string }
interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export const recruitmentAgencyPortalApi = {
  login: (body: { email: string; password: string }) =>
    apiClient.post("/api/v1/recruitment-agency/portal/auth/login", body).then(res => res.data as PortalAuthResponse),
  
  register: (body: { inviteToken: string; password: string; fullName: string }) =>
    apiClient.post("/api/v1/recruitment-agency/portal/auth/register", body).then(res => res.data as PortalAuthResponse),
  
  getMyClients: () =>
    apiClient.get("/api/v1/recruitment-agency/portal/clients").then(res => res.data as PortalClientSummary[]),
  
  getMyRequisitions: (clientId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/portal/clients/${clientId}/requisitions`).then(res => res.data as Requisition[]),
  
  getMyPlacements: (clientId: string, requisitionId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/portal/clients/${clientId}/requisitions/${requisitionId}/placements`).then(res => res.data as Placement[]),
  
  getMyInvoices: (clientId: string) =>
    apiClient.get(`/api/v1/recruitment-agency/portal/clients/${clientId}/invoices`).then(res => res.data as PageResponse<AgencyInvoice>),
}
