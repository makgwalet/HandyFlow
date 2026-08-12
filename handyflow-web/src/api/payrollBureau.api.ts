// src/api/payrollBureau.api.ts — staff-facing API client
// Same caveat as every *.api.ts addition this session: written against
// the confirmed backend contract (endpoints, request/response shapes),
// not against a verified real apiClient/axios setup — check the actual
// call syntax against your other staff api files (e.g. accountant.api.ts)
// before assuming this compiles byte-for-byte.
import { apiClient } from "./client"
import type {
  PayClient, PayEmployee, PayRun, Payslip, PayDeadline, PayFeeNote, PortalAccessGrant,
} from "../types/payrollBureau.types"

interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export const payrollBureauApi = {
  // Profile
  getProfile: () => apiClient.get("/api/v1/payroll-bureau/profile").then(r => r.data),
  upsertProfile: (body: any) => apiClient.put("/api/v1/payroll-bureau/profile", body).then(r => r.data),

  // Clients
  getClients: (page = 0, size = 50) =>
    apiClient.get(`/api/v1/payroll-bureau/clients?page=${page}&size=${size}`).then(r => r.data as PageResponse<PayClient>),
  getClient: (id: string) => apiClient.get(`/api/v1/payroll-bureau/clients/${id}`).then(r => r.data as PayClient),
    createClient: (body: Partial<PayClient>) =>
    apiClient.post("/api/v1/payroll-bureau/clients", body).then(r => r.data as PayClient),
   updateClient: (id: string, body: Partial<PayClient>) =>
    apiClient.put(`/api/v1/payroll-bureau/clients/${id}`, body).then(r => r.data as PayClient),
  offboardClient: (id: string) => apiClient.post(`/api/v1/payroll-bureau/clients/${id}/offboard`).then(r => r.data),
  reactivateClient: (id: string) => apiClient.post(`/api/v1/payroll-bureau/clients/${id}/reactivate`).then(r => r.data),

  // Employees
  getEmployees: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/clients/${clientId}/employees`).then(r => r.data as PayEmployee[]),
   createEmployee: (clientId: string, body: Partial<PayEmployee>) =>
    apiClient.post(`/api/v1/payroll-bureau/clients/${clientId}/employees`, body).then(r => r.data as PayEmployee),

  // Pay runs
  getPayRuns: (clientId: string, page = 0) =>
    apiClient.get(`/api/v1/payroll-bureau/clients/${clientId}/pay-runs?page=${page}`).then(r => r.data as PageResponse<PayRun>),
   createPayRun: (clientId: string, body: { periodStart: string; periodEnd: string; payDate: string }) =>
    apiClient.post(`/api/v1/payroll-bureau/clients/${clientId}/pay-runs`, body).then(r => r.data as PayRun),
   processPayRun: (payRunId: string) =>
    apiClient.post(`/api/v1/payroll-bureau/pay-runs/${payRunId}/process`).then(r => r.data as PayRun),
   getPayslips: (payRunId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/pay-runs/${payRunId}/payslips`).then(r => r.data as Payslip[]),
  // Deadlines
  getDeadlines: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/clients/${clientId}/deadlines`).then(r => r.data as PayDeadline[]),
   generateDeadlines: (clientId: string, year: number) =>
    apiClient.post(`/api/v1/payroll-bureau/clients/${clientId}/deadlines/generate?year=${year}`).then(r => r.data as PayDeadline[]),
   markDeadlineFiled: (deadlineId: string) =>
    apiClient.post(`/api/v1/payroll-bureau/deadlines/${deadlineId}/mark-filed`).then(r => r.data as PayDeadline),

  // Billing
  getFeeNotes: (clientId: string, page = 0) =>
    apiClient.get(`/api/v1/payroll-bureau/clients/${clientId}/fee-notes?page=${page}`).then(r => r.data as PageResponse<PayFeeNote>),
   generateFeeNote: (clientId: string, body: { payRunId: string; invoiceDate: string; dueDate: string; includeVat: boolean }) =>
    apiClient.post(`/api/v1/payroll-bureau/clients/${clientId}/fee-notes`, body).then(r => r.data as PayFeeNote),
   sendFeeNote: (feeNoteId: string) =>
    apiClient.post(`/api/v1/payroll-bureau/fee-notes/${feeNoteId}/send`).then(r => r.data as PayFeeNote),
   recordPayment: (feeNoteId: string, body: { amount: number; paidDate: string; method?: string; reference?: string }) =>
    apiClient.post(`/api/v1/payroll-bureau/fee-notes/${feeNoteId}/payments`, body).then(r => r.data as PayFeeNote),

  // Portal invites
  getPortalAccessGrants: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/clients/${clientId}/portal-invites`).then(r => r.data as PortalAccessGrant[]),
   invitePortalUser: (clientId: string, email: string) =>
    apiClient.post(`/api/v1/payroll-bureau/clients/${clientId}/portal-invites`, { email }).then(r => r.data as PortalAccessGrant),
   revokePortalAccess: (clientId: string, grantId: string) =>
    apiClient.post(`/api/v1/payroll-bureau/clients/${clientId}/portal-invites/${grantId}/revoke`).then(r => r.data as PortalAccessGrant),

  // Documents
  getEmployeeDocuments: (employeeId: string) =>
      apiClient.get(`/api/v1/payroll-bureau/employees/${employeeId}/documents`).then(r => r.data),
  downloadDocument: (documentId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/documents/${documentId}/download`, { responseType: "blob" }).then(r => r.data as Blob),
}
