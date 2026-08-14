// src/api/recruitmentAgencyPortal.api.ts
import { recruitmentAgencyPortalApiClient as portalApiClient } from "./recruitmentAgencyPortal.client"
import type { PortalClientSummary, Requisition, Placement, AgencyInvoice } from "../types/recruitmentAgencyPortal.types"

interface PortalAuthResponse { token: string; portalUserId: string; email: string; fullName: string }
interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export const recruitmentAgencyPortalApi = {
  login: (body: { email: string; password: string }) =>
    portalApiClient.post("/api/v1/recruitment-agency/portal/auth/login", body).then(res => res.data as PortalAuthResponse),
  
  register: (body: { inviteToken: string; password: string; fullName: string }) =>
    portalApiClient.post("/api/v1/recruitment-agency/portal/auth/register", body).then(res => res.data as PortalAuthResponse),
  
  getMyClients: () =>
    portalApiClient.get("/api/v1/recruitment-agency/portal/clients").then(res => res.data as PortalClientSummary[]),
  
  getMyRequisitions: (clientId: string) =>
    portalApiClient.get(`/api/v1/recruitment-agency/portal/clients/${clientId}/requisitions`).then(res => res.data as Requisition[]),
  
  getMyPlacements: (clientId: string, requisitionId: string) =>
    portalApiClient.get(`/api/v1/recruitment-agency/portal/clients/${clientId}/requisitions/${requisitionId}/placements`).then(res => res.data as Placement[]),
  
  getMyInvoices: (clientId: string) =>
    portalApiClient.get(`/api/v1/recruitment-agency/portal/clients/${clientId}/invoices`).then(res => res.data as PageResponse<AgencyInvoice>),
}
