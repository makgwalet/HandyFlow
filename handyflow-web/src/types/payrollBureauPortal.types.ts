// src/types/payrollBureauPortal.types.ts
//
// FIELD-NAME CAVEAT: PortalFeeNoteResponse and PortalDeadlineResponse
// below are inferred from the CONFIRMED sibling shapes in accountant's
// FeeNoteResponse/TaxDeadlineResponse (verified via direct search this
// session) — Payroll Bureau's own PayFeeNoteResponse/PayDeadlineResponse
// were explicitly built to mirror those (Sections 49-50), but I could
// not directly re-confirm their exact field names this session; two
// searches for them specifically came back empty. Worth a quick check
// against the real DTOs before trusting this compiles byte-for-byte —
// same caveat discipline as every other inferred-not-confirmed file
// this session.

export interface PortalClientSummary {
  clientId: string
  tradingName: string
}

export interface PortalFeeNote {
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
  daysOverdue: number
}

export interface PortalDeadline {
  id: string
  deadlineType: string
  periodYear: number
  periodMonth: number | null
  adjustedDueDate: string
  status: "PENDING" | "FILED" | "OVERDUE"
  daysUntilDue: number
}
