// src/api/csPortal.api.ts
import { csPortalApiClient as c } from './csPortal.client'

export interface CsPortalAuth { token: string; userId: string; email: string; fullName: string }
export interface CsPortalClient { id: string; name: string }
export interface CsPortalQuoteLine { serviceCode: string; serviceName: string | null; description: string | null; beforeDiscount: number; discount: number; professional: number; disbursements: number; vat: number; total: number }
export interface CsPortalQuote {
  id: string; quoteNumber: string; title: string | null; status: string; validUntil: string | null; sentAt: string | null; currency: string; professional: number
  discountTotal: number; disbursements: number; vat: number; vatRatePct: number; total: number; notes: string | null; terms: string | null; lines: CsPortalQuoteLine[]
}
export interface CsPortalAgreement {
  id: string; agreementNumber: string; title: string | null; status: string; startDate: string; endDate: string | null; billingFrequency: string; billingAmount: number
  autoRenew: boolean; noticeDays: number; terms: string | null; currency: string; scope: { serviceCode: string; serviceName: string | null; description: string | null }[]
  nextBills: { date: string; amount: number }[]
}
export interface CsPortalInvoice { invoiceNumber: string; status: string; billingDate: string; description: string | null; total: number; paid: number; credited: number; outstanding: number }
export interface CsPortalOverview { client: CsPortalClient; quotes: CsPortalQuote[]; agreements: CsPortalAgreement[]; invoices: CsPortalInvoice[]; outstanding: number }

export interface CsPortalQuery { id: string; subject: string; status: string; createdAt: string; lastMessageAt: string; messageCount: number }
export interface CsPortalMessage { fromClient: boolean; author: string; body: string; createdAt: string }
export interface CsPortalQueryDetail { query: CsPortalQuery; messages: CsPortalMessage[] }
export interface CsPortalRequest { id: string; title: string; description: string | null; documentType: string | null; dueDate: string | null; status: string; requestedAt: string; late: boolean }

const A = '/api/v1/compliance-services/portal/auth'
const M = '/api/v1/compliance-services/portal/me'
const p = (clientId: string) => ({ params: { clientId } })

export const csPortalApi = {
  login: (b: { email: string; password: string }) => c.post<CsPortalAuth>(`${A}/login`, b).then(r => r.data),
  register: (b: { inviteToken: string; password: string; fullName: string }) => c.post<CsPortalAuth>(`${A}/register`, b).then(r => r.data),
  clients: () => c.get<CsPortalClient[]>(`${M}/clients`).then(r => r.data),
  overview: (id: string) => c.get<CsPortalOverview>(`${M}/overview`, p(id)).then(r => r.data),
  queries: (clientId: string) => c.get<CsPortalQuery[]>(`${M}/queries`, p(clientId)).then(r => r.data),
  query: (id: string) => c.get<CsPortalQueryDetail>(`${M}/queries/${id}`).then(r => r.data),
  raise: (b: { clientId: string; subject: string; body: string }) => c.post<CsPortalQueryDetail>(`${M}/queries`, b).then(r => r.data),
  reply: (id: string, body: string) => c.post<CsPortalQueryDetail>(`${M}/queries/${id}/messages`, { body }).then(r => r.data),
  requests: (clientId: string) => c.get<CsPortalRequest[]>(`${M}/document-requests`, p(clientId)).then(r => r.data),
  upload: (id: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return c.post<CsPortalRequest>(`${M}/document-requests/${id}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data)
  },
}
