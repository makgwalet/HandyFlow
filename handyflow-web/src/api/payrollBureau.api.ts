// src/api/payrollBureau.api.ts — staff-facing API client
// ADDITION: uploadDocument() was missing entirely — getEmployeeDocuments
// and downloadDocument existed (added when fixing the envelope-unwrap
// bug earlier), but nothing could ever add a document in the first
// place. Mirrors recruitmentAgencyApi.uploadCv()'s multipart pattern.
// UNVERIFIED: the exact field name for the document-type param
// ("docType" below) is a guess based on PayEmployeeDocumentResponse
// having a docType field — if the backend expects a different param
// name or a JSON body alongside the file part, this will 400 and need
// adjusting against the real CreatePayEmployeeDocumentRequest.
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
  updateEmployee: (clientId: string, employeeId: string, body: Partial<PayEmployee>) =>
    apiClient.put(`/api/v1/payroll-bureau/clients/${clientId}/employees/${employeeId}`, body).then(r => r.data as PayEmployee),
  emailPayslips: (payRunId: string) =>
    apiClient.post(`/api/v1/payroll-bureau/pay-runs/${payRunId}/payslips/email`).then(r => r.data as { sent: number; skippedNoEmail: number; skippedEmployeeNames: string[] }),
  downloadPayslipPdf: (payRunId: string, payslipId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/pay-runs/${payRunId}/payslips/${payslipId}/pdf`, { responseType: "blob" }).then(r => r.data as Blob),

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

  attachProfileLogo: (file: File) => {
   const form = new FormData(); form.append("file", file)
    return apiClient.post(`/api/v1/{module}/profile/logo`, form,
      { headers: { "Content-Type": "multipart/form-data" } }).then(r => r.data)
  },
  downloadProfileLogo: () =>
    apiClient.get(`/api/v1/{module}/profile/logo`, { responseType: "blob" }).then(r => r.data as Blob),


  attachLogo: (clientId: string, file: File) => {
    const form = new FormData(); form.append("file", file)
    return apiClient.post(`/api/v1/payroll-bureau/clients/${clientId}/logo`, form,
      { headers: { "Content-Type": "multipart/form-data" } }).then(r => r.data as PayClient)
  },
  downloadLogo: (clientId: string) =>
    apiClient.get(`/api/v1/payroll-bureau/clients/${clientId}/logo`, { responseType: "blob" }).then(r => r.data as Blob),
  
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
  // NEW — see file header caveat on field naming.
  uploadDocument: (employeeId: string, file: File, docType: string) => {
    const form = new FormData()
    form.append("file", file)
    form.append("docType", docType)
    return apiClient.post(`/api/v1/payroll-bureau/employees/${employeeId}/documents`, form,
      { headers: { "Content-Type": "multipart/form-data" } }).then(r => r.data)
  },
}
