// src/api/projectClientPortal.client.ts
// The Projects client portal (named client logins on the shared portal login). Same pattern as the other portal clients:
// its own axios instance carrying the PORTAL token from usePortalAuthStore, never the staff token. A dead session
// (401, or 403 "no project access") logs out and returns to this portal's login; a 404 on one project does not.
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'
import { filenameFrom, saveBlob } from './recipientAccess.client'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'
export const PM_PORTAL = '/api/v1/project-client/portal'
export const PM_PORTAL_HOME = '/projects/client-portal'

export const pmPortalClient = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })

pmPortalClient.interceptors.request.use(config => {
  const token = usePortalAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

pmPortalClient.interceptors.response.use(
  response => (response.data && typeof response.data === 'object' && 'data' in response.data && !(response.data instanceof Blob))
    ? { ...response, data: response.data.data } : response,
  error => {
    const status = error.response?.status
    const url = error.config?.url as string | undefined
    if ((status === 401 || status === 403) && !url?.includes('/portal/auth/')) {
      usePortalAuthStore.getState().logout()
      window.location.href = `${PM_PORTAL_HOME}/login`
    }
    return Promise.reject(error)
  })

export async function portalDownload(path: string, fallbackName: string) {
  const r = await pmPortalClient.get(path, { responseType: 'blob' })
  saveBlob(r.data as Blob, filenameFrom(r.headers?.['content-disposition']) || fallbackName)
}

// ── Types (mirror ClientPortalDtos) ──────────────────────────────────────────
export interface AuthResult { token: string; portalUserId: string; email: string; fullName: string }
export interface InviteInfo { clientName: string; email: string; contactName: string | null; invitedByName: string | null; existingAccount: boolean; expiresAt: string }
export interface Me { fullName: string; email: string; accounts: { grantId: string; clientName: string }[] }
export interface MyProject { id: string; projectNumber: string; name: string; clientName: string | null; status: string; health: string
  startDate: string | null; endDate: string | null; completionPct: number; openSnags: number; changeOrdersAwaiting: number; openRequests: number; answeredRequests: number }
export interface ClientChangeOrder { id: string; changeNumber: string; title: string; description: string | null; reason: string | null
  costImpact: number; scheduleImpactDays: number; approvedAt: string | null; clientState: 'AWAITING' | 'APPROVED' | 'DISPUTED'
  clientApprovedAt: string | null; disputedAt: string | null; disputeReason: string | null; disputeResolvedAt: string | null; disputeResolution: string | null }
export interface ClientRequest { id: string; requestNumber: string; kind: 'QUESTION' | 'CHANGE' | 'NEW_PROJECT'; projectId: string | null; title: string
  description: string | null; neededBy: string | null; status: 'OPEN' | 'ACCEPTED' | 'ANSWERED' | 'DECLINED'; response: string | null
  raisedByName: string; raisedByEmail: string; decidedByName: string | null; decidedAt: string | null; linkedType: string | null; linkedId: string | null; createdAt: string }
export interface ClientUpload { id: string; projectId: string; fileName: string; sizeBytes: number; note: string | null; status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  reason: string | null; documentId: string | null; uploadedByName: string; uploadedByEmail: string; decidedByName: string | null; decidedAt: string | null; createdAt: string }
export interface ProjectView { project: MyProject; siteAddress: string | null
  milestones: { taskNumber: string; title: string; status: string; plannedStart: string | null; plannedEnd: string | null; actualStart: string | null; actualEnd: string | null; progressPct: number }[]
  openSnags: { snagNumber: string; title: string; location: string | null; severity: string; status: string; dueDate: string | null }[]
  redRisks: { title: string; category: string | null; rating: string; status: string }[]
  documents: { id: string; documentNumber: string; title: string; revision: string | null; documentType: string; approvedAt: string | null; downloadable: boolean }[]
  changeOrders: ClientChangeOrder[]; requests: ClientRequest[]; uploads: ClientUpload[] }

export const KIND_LABEL: Record<ClientRequest['kind'], string> = { QUESTION: 'Question', CHANGE: 'Change request', NEW_PROJECT: 'New project' }
export const REQUEST_STATUS: Record<ClientRequest['status'], { label: string; tone: 'warning' | 'success' | 'info' | 'neutral' }> = {
  OPEN: { label: 'Waiting for reply', tone: 'warning' }, ACCEPTED: { label: 'Taken on', tone: 'success' },
  ANSWERED: { label: 'Answered', tone: 'info' }, DECLINED: { label: 'Declined', tone: 'neutral' } }
export const UPLOAD_STATUS: Record<ClientUpload['status'], { label: string; tone: 'warning' | 'success' | 'danger' }> = {
  PENDING: { label: 'Being reviewed', tone: 'warning' }, ACCEPTED: { label: 'Accepted', tone: 'success' }, REJECTED: { label: 'Not accepted', tone: 'danger' } }
