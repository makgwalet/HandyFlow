// src/types/recruitmentAgencyPortal.types.ts

export interface PortalClientSummary {
  clientId: string
  tradingName: string
}

export interface Requisition {
  id: string
  clientId: string
  clientName: string
  requisitionNumber: string
  title: string
  description: string | null
  salaryMin: number | null
  salaryMax: number | null
  location: string | null
  employmentType: string
  status: "OPEN" | "FILLED" | "CANCELLED" | "ON_HOLD"
  targetStartDate: string | null
  notes: string | null
  candidateCount: number
  createdAt: string
}

export interface Placement {
  id: string
  requisitionId: string
  requisitionTitle: string
  candidateId: string
  candidateName: string
  clientId: string
  stage: string
  offeredSalary: number | null
  placementFeeAmount: number | null
  placedAt: string | null
  guaranteeEndsAt: string | null
  notes: string | null
  createdAt: string
}

export interface AgencyInvoice {
  id: string
  invoiceNumber: string
  description: string
  invoiceDate: string
  dueDate: string
  subtotal: number
  vatAmount: number
  total: number
  amountPaid: number
  balance: number
  status: "DRAFT" | "SENT" | "PARTIAL" | "PAID" | "OVERDUE"
  sentAt: string | null
  paidAt: string | null
}
