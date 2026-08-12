// src/types/payrollBureauPortal.types.ts

export interface PortalClientSummary {
  clientId: string
  tradingName: string
}

export interface PayFeeNote {
  id: string
  invoiceNumber: string
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

export interface PayDeadline {
  id: string
  deadlineType: string
  periodYear: number
  periodMonth: number | null
  adjustedDueDate: string
  status: "PENDING" | "FILED" | "OVERDUE"
  filedDate: string | null
  daysUntilDue: number
}

export interface PortalAuthUser {
  portalUserId: string
  email: string
  fullName: string
}
