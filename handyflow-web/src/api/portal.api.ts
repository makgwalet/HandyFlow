// src/api/portal.api.ts
import { portalApiClient } from './portal.client'
import type {
  PortalRegisterRequest, PortalLoginRequest, PortalAuthResponse,
  PortalClientSummary, FeeNote, FicaDocument, UploadFicaDocumentRequest,
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
}
