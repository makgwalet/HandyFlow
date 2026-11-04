// src/api/bookkeepingPortal.api.ts
import { bookkeepingPortalApiClient as c } from './bookkeepingPortal.client'

export interface BkPortalAuth { token: string; portalUserId: string; email: string; fullName: string }
export interface BkPortalInvoice {
  id: string; clientId: string; invoiceNumber: string; periodStart: string | null; periodEnd: string | null; issueDate: string | null
  dueDate: string | null; subtotal: number; vatAmount: number; total: number; amountPaid: number; balance: number; status: string
}
export interface BkPortalTime {
  id: string; clientId: string; practitionerName: string | null; entryDate: string; activityType: string; description: string | null
  hours: number; hourlyRate: number | null; lineTotal: number | null; billable: boolean; status: string; invoiceId: string | null
}
export interface Paged<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export const bookkeepingPortalApi = {
  login: (b: { email: string; password: string }) => c.post<BkPortalAuth>('/api/v1/bookkeeping/portal/auth/login', b).then(r => r.data),
  register: (b: { inviteToken: string; password: string; fullName: string }) => c.post<BkPortalAuth>('/api/v1/bookkeeping/portal/auth/register', b).then(r => r.data),
  invoices: () => c.get<BkPortalInvoice[]>('/api/v1/bookkeeping/portal/me/invoices').then(r => r.data),
  timeEntries: (page: number) => c.get<Paged<BkPortalTime>>('/api/v1/bookkeeping/portal/me/time-entries', { params: { page, size: 20 } }).then(r => r.data),
}
