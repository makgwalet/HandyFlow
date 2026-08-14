// src/api/portal.api.ts
import { portalApiClient } from './portal.client'
import type {
  PortalRegisterRequest, PortalLoginRequest, PortalAuthResponse,
  PortalClientSummary, FeeNote, FicaDocument, UploadFicaDocumentRequest,
  DocumentRequest,
  TaxDeadline,
} from '../types/portal.types'

export const portalApi = {
  register: async (data: PortalRegisterRequest): Promise<PortalAuthResponse> => {
    const res = await portalApiClient.post('/api/v1/accountant/portal/auth/register', data)
    return res.data
  },

  login: async (data: PortalLoginRequest): Promise<PortalAuthResponse> => {
    const res = await portalApiClient.post('/api/v1/accountant/portal/auth/login', data)
    return res.data
  },

  getMyClients: async (): Promise<PortalClientSummary[]> => {
    const res = await portalApiClient.get('/api/v1/accountant/portal/me/clients')
    return res.data
  },

  getMyFeeNotes: async (clientId: string): Promise<FeeNote[]> => {
    const res = await portalApiClient.get(`/api/v1/accountant/portal/clients/${clientId}/fee-notes`)
    return res.data
  },

  downloadFeeNotePdf: async (clientId: string, feeNoteId: string): Promise<Blob> => {
    const res = await portalApiClient.get(
      `/api/v1/accountant/portal/clients/${clientId}/fee-notes/${feeNoteId}/pdf`,
      { responseType: 'blob' }
    )
    return res.data
  },

  getMyFicaDocuments: async (clientId: string): Promise<FicaDocument[]> => {
    const res = await portalApiClient.get(`/api/v1/accountant/portal/clients/${clientId}/fica-documents`)
    return res.data
  },

  uploadFicaDocument: async (clientId: string, data: UploadFicaDocumentRequest): Promise<FicaDocument> => {
    const res = await portalApiClient.post(`/api/v1/accountant/portal/clients/${clientId}/fica-documents`, data)
    return res.data
  },

  downloadFicaDocument: async (clientId: string, docId: string): Promise<Blob> => {
    const res = await portalApiClient.get(
      `/api/v1/accountant/portal/clients/${clientId}/fica-documents/${docId}`,
      { responseType: 'blob' }
    )
    return res.data
  },

  // FIX (both bugs on all three methods below):
  // 1. Missing /api/v1 prefix — every other method in this file
  //    correctly calls /api/v1/accountant/portal/..., these three
  //    called /accountant/portal/... (no /api/v1). portalApiClient's
  //    baseURL has no /api/v1 baked in, so this was a guaranteed 404.
  // 2. Double-unwrap — .then(res => res.data.data as X). portal.client.ts's
  //    own response interceptor already unwraps {success, message, data}
  //    down to just `data` (confirmed directly in that file, same as
  //    every other method here reading res.data once). Reading
  //    res.data.data a second time on top of that is the identical
  //    envelope bug found and fixed in payrollBureau.api.ts,
  //    bookingAgency.api.ts, and recruitmentAgency.api.ts earlier this
  //    session — same root cause, just a fourth, previously-unseen file.
  getMyDocumentRequests: (clientId: string) =>
      portalApiClient.get(`/api/v1/accountant/portal/clients/${clientId}/document-requests`)
        .then(res => res.data as DocumentRequest[]),

  submitDocumentRequest: (clientId: string, requestId: string) =>
      portalApiClient.post(`/api/v1/accountant/portal/clients/${clientId}/document-requests/${requestId}/submit`)
        .then(res => res.data as DocumentRequest),

  getMyTaxDeadlines: (clientId: string) =>
      portalApiClient.get(`/api/v1/accountant/portal/clients/${clientId}/tax-deadlines`)
        .then(res => res.data as TaxDeadline[]),
}