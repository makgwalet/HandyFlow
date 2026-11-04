// src/api/legalPortal.api.ts
import { legalPortalApiClient as c } from './legalPortal.client'

export interface LpPortalAuth { token: string; portalUserId: string; email: string; fullName: string }
export interface LpPortalClient { id: string; name: string; email: string | null; phone: string | null; trustBalance: number }
export interface LpPortalMatter { id: string; matterNumber: string; matterName: string; matterType: string; status: string; openedDate: string | null; closedDate: string | null }
export interface LpPortalInvoice { id: string; matterId: string | null; invoiceNumber: string; description: string | null; issueDate: string | null; dueDate: string | null; subtotal: number; vatAmount: number; totalAmount: number; amountPaid: number; outstandingBalance: number; status: string }
export interface Paged<T> { content: T[]; totalElements: number; totalPages: number; number: number }

const D = '/api/v1/legal-practice/portal/data/clients'
export const legalPortalApi = {
  login: (b: { email: string; password: string }) => c.post<LpPortalAuth>('/api/v1/legal-practice/portal/auth/login', b).then(r => r.data),
  register: (b: { inviteToken: string; password: string; fullName: string }) => c.post<LpPortalAuth>('/api/v1/legal-practice/portal/auth/register-via-invite', b).then(r => r.data),
  clients: () => c.get<LpPortalClient[]>(D).then(r => r.data),
  matters: (clientId: string, page: number) => c.get<Paged<LpPortalMatter>>(`${D}/${clientId}/matters`, { params: { page, size: 20 } }).then(r => r.data),
  invoices: (clientId: string, page: number) => c.get<Paged<LpPortalInvoice>>(`${D}/${clientId}/invoices`, { params: { page, size: 20 } }).then(r => r.data),
}
