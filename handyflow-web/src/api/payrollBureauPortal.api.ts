// src/api/payrollBureauPortal.api.ts
import { payrollBureauPortalApiClient as apiClient} from "./payrollBureauPortal.client"
import type { PortalClientSummary, PortalFeeNote, PortalDeadline } from "../types/payrollBureauPortal.types"

interface PortalAuthResponse { token: string; portalUserId: string; email: string; fullName: string }

// ENDPOINT PATH CAVEAT: /clients, /fee-notes, /deadlines below follow
// the exact naming convention already confirmed working for BOTH
// recruitmentagency and bookingagency's portals this session (verified
// via Swagger for recruitmentagency specifically) — but I don't have
// direct confirmation these exact paths exist for payrollbureau's
// portal specifically, only that its portal controller exists per the
// discovery doc's own Section 53-54 summary. If these 404, check the
// real PayrollBureauPortalDataController for the actual path names.
export const payrollBureauPortalApi = {
  login: (body: { email: string; password: string }) =>
    apiClient.post("/api/v1/payroll-bureau/portal/auth/login", body).then(res => res.data as PortalAuthResponse),
  
  register: (body: { inviteToken: string; password: string; fullName: string }) =>
    apiClient.post("/api/v1/payroll-bureau/portal/auth/register", body).then(res => res.data as PortalAuthResponse),
  
  getMyClients: () =>
    apiClient.get("/api/v1/payroll-bureau/portal/clients").then(res => res.data as PortalClientSummary[]),
  
  getMyFeeNotes: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/portal/clients/${clientId}/fee-notes`).then(res => (res.data as { content: PortalFeeNote[] }).content),
  
  getMyDeadlines: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/portal/clients/${clientId}/deadlines`).then(res => res.data as PortalDeadline[]),
}
