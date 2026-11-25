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

const A = '/api/v1/compliance-services/portal/auth'
const M = '/api/v1/compliance-services/portal/me'
const p = (clientId: string) => ({ params: { clientId } })

export const csPortalApi = {
  login: (b: { email: string; password: string }) => c.post<CsPortalAuth>(`${A}/login`, b).then(r => r.data),
  register: (b: { inviteToken: string; password: string; fullName: string }) => c.post<CsPortalAuth>(`${A}/register`, b).then(r => r.data),
  clients: () => c.get<CsPortalClient[]>(`${M}/clients`).then(r => r.data),
  overview: (id: string) => c.get<CsPortalOverview>(`${M}/overview`, p(id)).then(r => r.data),
}
