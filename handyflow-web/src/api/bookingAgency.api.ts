// src/api/bookingAgency.api.ts — staff-facing API client
// Same caveat as every *.api.ts addition this session: written against
// the confirmed backend contract, not against a verified real
// apiClient/axios setup — check actual call syntax against your other
// staff api files before assuming this compiles byte-for-byte.
import { apiClient } from "./client"
import type {
  BookAgencyClient, BookAgencyResource, BookAgencyOffering, BookAgencyBooking, PortalAccessGrant,
} from "../types/bookingAgency.types"
import type { BookAgencyInvoice } from "../types/bookingAgency.types";

interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export const bookingAgencyApi = {
  // Profile
  getProfile: () => apiClient.get("/api/v1/booking-agency/profile").then(r => r.data.data),
  upsertProfile: (body: any) => apiClient.put("/api/v1/booking-agency/profile", body).then(r => r.data.data),

  // Clients
  getClients: (page = 0, size = 50) =>
    apiClient.get(`/api/v1/booking-agency/clients?page=${page}&size=${size}`).then(r => r.data.data as PageResponse<BookAgencyClient>),
  createClient: (body: Partial<BookAgencyClient>) =>
    apiClient.post("/api/v1/booking-agency/clients", body).then(r => r.data.data as BookAgencyClient),
  updateClient: (id: string, body: Partial<BookAgencyClient>) =>
    apiClient.put(`/api/v1/booking-agency/clients/${id}`, body).then(r => r.data.data as BookAgencyClient),
  deactivateClient: (id: string) => apiClient.post(`/api/v1/booking-agency/clients/${id}/deactivate`).then(r => r.data.data),
  reactivateClient: (id: string) => apiClient.post(`/api/v1/booking-agency/clients/${id}/reactivate`).then(r => r.data.data),

  // Resources
  getResources: (clientId: string) =>
    apiClient.get(`/api/v1/booking-agency/clients/${clientId}/resources`).then(r => r.data.data as BookAgencyResource[]),
  createResource: (body: Partial<BookAgencyResource>) =>
    apiClient.post("/api/v1/booking-agency/resources", body).then(r => r.data.data as BookAgencyResource),
  updateResource: (id: string, body: Partial<BookAgencyResource>) =>
    apiClient.put(`/api/v1/booking-agency/resources/${id}`, body).then(r => r.data.data as BookAgencyResource),
  deactivateResource: (id: string) => apiClient.post(`/api/v1/booking-agency/resources/${id}/deactivate`).then(r => r.data.data),
  reactivateResource: (id: string) => apiClient.post(`/api/v1/booking-agency/resources/${id}/reactivate`).then(r => r.data.data),

  // Offerings
  getOfferings: (clientId: string) =>
    apiClient.get(`/api/v1/booking-agency/clients/${clientId}/offerings`).then(r => r.data.data as BookAgencyOffering[]),
  createOffering: (body: Partial<BookAgencyOffering>) =>
    apiClient.post("/api/v1/booking-agency/offerings", body).then(r => r.data.data as BookAgencyOffering),
  updateOffering: (id: string, body: Partial<BookAgencyOffering>) =>
    apiClient.put(`/api/v1/booking-agency/offerings/${id}`, body).then(r => r.data.data as BookAgencyOffering),
  deactivateOffering: (id: string) => apiClient.post(`/api/v1/booking-agency/offerings/${id}/deactivate`).then(r => r.data.data),

  // Bookings
  getBookings: (clientId: string, page = 0) =>
    apiClient.get(`/api/v1/booking-agency/clients/${clientId}/bookings?page=${page}`).then(r => r.data.data as PageResponse<BookAgencyBooking>),
  createBooking: (clientId: string, body: Partial<BookAgencyBooking> & { resourceId: string; offeringId: string; startDatetime: string }) =>
    apiClient.post(`/api/v1/booking-agency/clients/${clientId}/bookings`, body).then(r => r.data.data as BookAgencyBooking),
  cancelBooking: (id: string) => apiClient.post(`/api/v1/booking-agency/bookings/${id}/cancel`).then(r => r.data.data as BookAgencyBooking),
  completeBooking: (id: string) => apiClient.post(`/api/v1/booking-agency/bookings/${id}/complete`).then(r => r.data.data as BookAgencyBooking),
  markNoShow: (id: string) => apiClient.post(`/api/v1/booking-agency/bookings/${id}/no-show`).then(r => r.data.data as BookAgencyBooking),

  // Portal invites
  getPortalAccessGrants: (clientId: string) =>
    apiClient.get(`/api/v1/booking-agency/clients/${clientId}/portal-invites`).then(r => r.data.data as PortalAccessGrant[]),
  invitePortalUser: (clientId: string, email: string) =>
    apiClient.post(`/api/v1/booking-agency/clients/${clientId}/portal-invites`, { email }).then(r => r.data.data as PortalAccessGrant),
  revokePortalAccess: (clientId: string, grantId: string) =>
    apiClient.post(`/api/v1/booking-agency/clients/${clientId}/portal-invites/${grantId}/revoke`).then(r => r.data.data as PortalAccessGrant),

 generateInvoice: (clientId: string, body: { periodStart: string; periodEnd: string; invoiceDate: string; dueDate: string; includeVat: boolean }) =>
      apiClient.post(`/api/v1/booking-agency/clients/${clientId}/invoices`, body).then(r => r.data.data as BookAgencyInvoice),
  getInvoices: (clientId: string, page = 0) =>
      apiClient.get(`/api/v1/booking-agency/clients/${clientId}/invoices?page=${page}`).then(r => r.data.data as PageResponse<BookAgencyInvoice>),
  sendInvoice: (id: string) => apiClient.post(`/api/v1/booking-agency/invoices/${id}/send`).then(r => r.data.data as BookAgencyInvoice),
  recordPayment: (id: string, body: { amount: number; paidDate: string; method?: string; reference?: string }) =>
      apiClient.post(`/api/v1/booking-agency/invoices/${id}/payments`, body).then(r => r.data.data as BookAgencyInvoice),
}
