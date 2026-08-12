// src/api/payrollBureauPortal.api.ts
//
// Same shape as accountant-portal's api client — I don't have that
// file's exact real content confirmed (only what its calling pages
// imply about its contract, same caveat noted every time this session
// built a *.api.ts addition), so this is written against that same
// inferred shape, not verified byte-for-byte against a real file.
// Check against your actual apiClient/axios setup before assuming this
// compiles as-is.
import { apiClient } from "./client"
import type { PayDeadline, PayFeeNote, PortalClientSummary } from "../types/payrollBureauPortal.types"

interface PortalAuthResponse {
  token: string
  portalUserId: string
  email: string
  fullName: string
}

export const payrollBureauPortalApi = {
  login: (body: { email: string; password: string }) =>
    apiClient.post("/api/v1/payroll-bureau/portal/auth/login", body).then(res => res.data.data as PortalAuthResponse),

  register: (body: { inviteToken: string; password: string; fullName: string }) =>
    apiClient.post("/api/v1/payroll-bureau/portal/auth/register", body).then(res => res.data.data as PortalAuthResponse),

  getMyClients: () =>
    apiClient.get("/api/v1/payroll-bureau/portal/clients").then(res => res.data.data as PortalClientSummary[]),

  getMyFeeNotes: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/portal/clients/${clientId}/fee-notes`)
      .then(res => (res.data.data.content ?? res.data.data) as PayFeeNote[]), // .content if Page<T>-shaped, matching backend's Page<PayFeeNoteResponse>

  getMyDeadlines: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/portal/clients/${clientId}/deadlines`).then(res => res.data.data as PayDeadline[]),
}
