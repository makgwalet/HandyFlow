// src/types/portal.types.ts

export interface PortalRegisterRequest {
  inviteToken: string
  password: string
  fullName: string
}

export interface PortalLoginRequest {
  email: string
  password: string
}

export interface PortalAuthResponse {
  token: string
  portalUserId: string
  email: string
  fullName: string
}

export interface PortalClientSummary {
  clientId: string
  tradingName: string
}

export interface FeeNoteLine {
  id: string
  description: string
  quantity: number
  unitPrice: number
  vatRate: number
  amount: number
}

export interface FeeNote {
  id: string
  clientId: string
  clientName: string | null
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  subtotal: number
  vatAmount: number
  total: number
  amountPaid: number
  balance: number
  status: string
  daysOverdue: number
  lines: FeeNoteLine[]
  createdAt: string
}

export interface FicaDocument {
  id: string
  docType: string
  fileName: string
  contentType: string
  fileSizeBytes: number
  verified: boolean
  verifiedAt: string | null
  expiryDate: string | null
  uploadedByName: string | null
  uploadedByType: string | null
  createdAt: string
}

export interface UploadFicaDocumentRequest {
  docType: string
  fileName: string
  contentType: string
  fileSizeBytes: number
  fileContentBase64: string
  expiryDate: string | null
}
