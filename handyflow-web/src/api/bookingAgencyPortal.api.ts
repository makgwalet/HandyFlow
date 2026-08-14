// src/api/bookingAgencyPortal.api.ts
//
// UNVERIFIED against a real BookingAgencyPortalController — no such
// controller has been confirmed to exist yet. Endpoint paths inferred
// from BookingAgencyController already being mounted at
// /api/v1/booking-agency, plus the /portal/auth/... convention confirmed
// in AccountantPortalAuthController. If these 404, the backend
// controller needs to exist first — this file alone can't fix that.
//
// Same envelope caveat as every other *.api.ts this session: apiClient's
// response interceptor already unwraps {success, message, data} down to
// just `data` — every method below reads r.data directly, NOT r.data.data
// (the bug found and fixed in payrollBureau.api.ts earlier).
import { apiClient } from "./client"
import type { PortalClientSummary, PortalInvoice } from "../types/bookingAgencyPortal.types"

interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export const bookingAgencyPortalApi = {
  login: (body: { email: string; password: string }) =>
    apiClient.post("/api/v1/booking-agency/portal/auth/login", body).then(r => r.data as {
      token: string; portalUserId: string; email: string; fullName: string
    }),
  register: (body: { inviteToken: string; password: string; fullName: string }) =>
    apiClient.post("/api/v1/booking-agency/portal/auth/register", body).then(r => r.data as {
      token: string; portalUserId: string; email: string; fullName: string
    }),
  getMyClients: () =>
    apiClient.get("/api/v1/booking-agency/portal/clients").then(r => r.data as PortalClientSummary[]),
  getMyInvoices: (clientId: string) =>
    apiClient.get(`/api/v1/booking-agency/portal/clients/${clientId}/invoices`).then(r => r.data as PageResponse<PortalInvoice>),
}
