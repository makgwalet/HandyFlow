// Shared helpers and response types for the Contracting screens. Kept out of the component files so fast refresh works.
import type { LucideIcon } from 'lucide-react'

/** Unwraps ApiResponse<Page<T>> or ApiResponse<List<T>> to a plain list. */
export const unwrap = <T = Record<string, unknown>>(r: unknown): T[] => {
  const res = r as { data?: { data?: unknown } & Record<string, unknown> }
  const payload = (res.data?.data ?? res.data) as T[] | { content?: T[] } | undefined
  return Array.isArray(payload) ? payload : payload?.content ?? []
}

/** Unwraps ApiResponse<T> where T is a single object (not a list or page). */
export const unwrapOne = <T>(r: unknown): T => {
  const res = r as { data?: { data?: unknown } & Record<string, unknown> }
  return (res.data?.data ?? res.data) as T
}

/** An amount in its own currency (for example R 1 000,00 or US$ 50,00). Falls back to the code if the currency is not recognised. */
export const fmtMoney = (n: number | string | null | undefined, currency: string | null | undefined): string => {
  if (n == null || n === '') return '—'
  try { return new Intl.NumberFormat('en-ZA', { style: 'currency', currency: currency || 'ZAR' }).format(Number(n)) }
  catch { return `${currency ?? ''} ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`.trim() }
}

/** Amounts by currency on one line, for example "R 500,00 · US$ 20,00"; a dash when there are none. */
export const fmtByCurrency = (byCurrency: Record<string, number | string> | null | undefined): string => {
  const parts = Object.entries(byCurrency ?? {}).map(([cur, v]) => fmtMoney(v, cur))
  return parts.length ? parts.join(' · ') : '—'
}

export const fmtR = (n: number | string | null | undefined): string =>
  n != null && n !== ''
    ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '—'

/** The server's message from a failed request, or the fallback. */
export const apiError = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

export interface ContractParty {
  id: string; partyType: string; partyRole: string; fullName: string; email?: string | null; phone?: string | null
  companyName?: string | null; signingOrder: number; signingStatus: string; signedAt?: string | null; otpSentAt?: string | null
  linkIssuedAt?: string | null; linkDeliveryStatus?: string | null; linkIssuedBy?: string | null
}

export interface ContractComment {
  id: string; comment: string; clauseRef?: string | null; isAmendmentRequest?: boolean; resolved?: boolean
  createdAt: string; authorName?: string | null; authorRole?: string | null
}

export interface ContractRow {
  id: string; contractNumber: string; title: string; contractType: string; status: string; body?: string | null; templateId?: string | null
  valueAmount?: number | null; startDate?: string | null; endDate?: string | null; autoRenew?: boolean; renewalNoticeDays?: number | null
  notes?: string | null; sentAt?: string | null; signedAt?: string | null; terminatedAt?: string | null; terminationReason?: string | null
  signedPartyCount?: number | null; totalPartyCount?: number | null; parties?: ContractParty[]; comments?: ContractComment[]
}

export interface ContractTemplateRow {
  id: string; name: string; contractType: string; variables?: Record<string, string> | null
  status?: string; legalStatus?: string; legalArea?: string
}

export type SignIcon = LucideIcon

export interface ContractDashboard {
  statusCounts: Record<string, number>; typeCounts: Record<string, number>; totalContracts: number
  activeValue: number | string | null; pendingValue: number | string | null
  expiringSoon: ContractRow[]; awaitingSignature: ContractRow[]
}
