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

export interface PortalPayRun {
  id: string; payRunNumber: string; periodStart: string; periodEnd: string; payDate: string
  status: "AWAITING_CLIENT" | "CLIENT_APPROVED" | "FINALISED" | "REVERSED"
  totalGross: number | null; totalPaye: number | null; totalUif: number | null; totalSdl: number | null; totalNet: number | null
  employeeCount: number | null; sentToClientAt: string | null; clientApprovedAt: string | null
  clientDeclinedAt: string | null; clientDeclineReason: string | null
}
export interface PortalPayslip {
  id: string; employeeName: string; employeeNumber: string; totalEarnings: number
  payeAmount: number; uifEmployee: number; totalDeductions: number; netPay: number
}
export interface PortalReviewItem { severity: string; code: string; employeeName: string | null; message: string }
export interface PortalPayRunDetail {
  run: PortalPayRun; payslips: PortalPayslip[]
  review: { previousRunNumber: string | null; blocking: number; warnings: number; findings: PortalReviewItem[] }
}
