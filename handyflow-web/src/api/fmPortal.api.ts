// src/api/fmPortal.api.ts
import { fmPortalApiClient as c } from './fmPortal.client'
import { filenameFrom, saveBlob } from './recipientAccess.client'

export interface FmPortalAuth { token: string; portalUserId: string; email: string; fullName: string }
export interface FmPortalClient { id: string; name: string }
export interface FmPortalSite { id: string; name: string; siteType: string; address: Record<string, string> | null; status: string }
export interface FmPortalWo { id: string; workOrderNumber: string; siteId: string; assetId: string | null; category: string; priority: string; status: string; description: string; reportedBy: string | null; scheduledDate: string | null; completedAt: string | null; createdAt: string; declineMessage: string | null; resolutionDueAt: string | null; resolutionSla: string | null }
export interface FmPortalSummary { workOrders: Record<string, number>; openWork: number; slaAtRisk: number; slaBreached: number; outstandingBalance: number | null; overdueInvoices: number; overdueBalance: number | null }
export interface FmPortalQuote { id: string; quoteNumber: string; siteId: string | null; title: string; description: string | null; status: string; canRespond: boolean; validUntil: string | null; subtotal: number; vatRate: number | null; vatAmount: number; total: number; sentAt: string | null; decidedAt: string | null; decisionNote: string | null; lines: { lineNo: number; description: string; quantity: number; unitPrice: number; lineTotal: number }[] }
export interface FmPortalInvoice { id: string; invoiceNumber: string; periodStart: string; periodEnd: string; issueDate: string | null; dueDate: string | null; subtotal: number; vatAmount: number; total: number; amountPaid: number; creditedAmount: number | null; balance: number; status: string }
export interface FmPortalCredit { id: string; creditNoteNumber: string; invoiceId: string; invoiceNumber: string; issueDate: string; reason: string; subtotal: number; vatAmount: number; total: number }
export interface FmPortalAsset { id: string; siteId: string; assetTag: string | null; name: string; assetType: string | null; location: string | null; manufacturer: string | null; model: string | null; status: string; installDate: string | null; warrantyExpiryDate: string | null }
export interface FmPortalStep { key: string; label: string; state: 'DONE' | 'CURRENT' | 'PENDING'; at: string | null; note: string | null }
export interface FmPortalProgress { status: string; steps: FmPortalStep[] }
export interface FmPortalPhoto { id: string; fileName: string; kind: 'PHOTO' | 'BEFORE_PHOTO' | 'AFTER_PHOTO'; createdAt: string }
export interface Paged<T> { content: T[]; totalElements: number; totalPages: number; number: number }
export interface RaiseBody { siteId: string; assetId: string | null; category: string; description: string; safetyImpact: boolean; contactName: string | null }

const M = '/api/v1/facilitiesmanagement/portal/me'
const p = (clientId: string, extra: Record<string, unknown> = {}) => ({ params: { clientId, ...extra } })
async function pdf(url: string, clientId: string, name: string) {
  const r = await c.get(url, { ...p(clientId), responseType: 'blob' })
  saveBlob(r.data as Blob, filenameFrom(r.headers?.['content-disposition']) || name)
}
export const fmPortalApi = {
  login: (b: { email: string; password: string }) => c.post<FmPortalAuth>('/api/v1/facilitiesmanagement/portal/auth/login', b).then(r => r.data),
  register: (b: { inviteToken: string; password: string; fullName: string }) => c.post<FmPortalAuth>('/api/v1/facilitiesmanagement/portal/auth/register', b).then(r => r.data),
  clients: () => c.get<FmPortalClient[]>(`${M}/clients`).then(r => r.data),
  summary: (id: string) => c.get<FmPortalSummary>(`${M}/summary`, p(id)).then(r => r.data),
  sites: (id: string) => c.get<Paged<FmPortalSite>>(`${M}/sites`, p(id, { size: 100 })).then(r => r.data),
  orders: (id: string, page: number) => c.get<Paged<FmPortalWo>>(`${M}/work-orders`, p(id, { page, size: 20 })).then(r => r.data),
  ordersAll: (id: string) => c.get<Paged<FmPortalWo>>(`${M}/work-orders`, p(id, { page: 0, size: 200 })).then(r => r.data),
  assets: (id: string) => c.get<FmPortalAsset[]>(`${M}/assets`, p(id)).then(r => r.data),
  progress: (id: string, wo: string) => c.get<FmPortalProgress>(`${M}/work-orders/${wo}/progress`, p(id)).then(r => r.data),
  photos: (id: string, wo: string) => c.get<FmPortalPhoto[]>(`${M}/work-orders/${wo}/photos`, p(id)).then(r => r.data),
  photoBlob: (id: string, wo: string, photo: string) => c.get(`${M}/work-orders/${wo}/photos/${photo}`, { ...p(id), responseType: 'blob' }).then(r => r.data as Blob),
  raise: (id: string, b: RaiseBody) => c.post<FmPortalWo>(`${M}/work-orders`, b, p(id)).then(r => r.data),
  withdraw: (id: string, woId: string) => c.delete(`${M}/work-orders/${woId}`, p(id)),
  quotes: (id: string) => c.get<FmPortalQuote[]>(`${M}/quotes`, p(id)).then(r => r.data),
  accept: (id: string, q: string, note: string | null) => c.post<FmPortalQuote>(`${M}/quotes/${q}/accept`, { note }, p(id)).then(r => r.data),
  decline: (id: string, q: string, note: string) => c.post<FmPortalQuote>(`${M}/quotes/${q}/decline`, { note }, p(id)).then(r => r.data),
  invoices: (id: string) => c.get<FmPortalInvoice[]>(`${M}/invoices`, p(id)).then(r => r.data),
  credits: (id: string) => c.get<FmPortalCredit[]>(`${M}/credit-notes`, p(id)).then(r => r.data),
  invoicePdf: (id: string, inv: string, name: string) => pdf(`${M}/invoices/${inv}/invoice.pdf`, id, name),
  creditPdf: (id: string, cn: string, name: string) => pdf(`${M}/credit-notes/${cn}/credit-note.pdf`, id, name),
}
