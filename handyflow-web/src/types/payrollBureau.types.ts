// src/types/payrollBureau.types.ts — staff-facing types

export interface PayClient {
  id: string
  tradingName: string
  registrationNumber: string | null
  payeReference: string | null
  uifReference: string | null
  sdlReference: string | null
  payFrequency: string
  payDay: number | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  perEmployeeFee: number
  onboardedAt: string
  status: "ACTIVE" | "OFFBOARDED"
  notes: string | null
  createdAt: string
}

export interface EmailResult {
  sent: number; skippedNoEmail: number; skippedEmployeeNames: string[]
  failed?: number; failedEmployeeNames?: string[]; alreadySent?: number
}

export interface PayEmployeeChange {
  id: string
  payEmployeeId: string
  employeeName: string
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED"
  prevGrossSalary: number | null; newGrossSalary: number | null
  prevTravelAllowance: number | null; newTravelAllowance: number | null
  prevPension: number | null; newPension: number | null
  prevMedicalAid: number | null; newMedicalAid: number | null
  prevBankName: string | null; newBankName: string | null
  prevBankAccount: string | null; newBankAccount: string | null
  prevBankBranch: string | null; newBankBranch: string | null
  requestedBy: string | null; requestedAt: string
  decidedBy: string | null; decidedAt: string | null; decisionNote: string | null
}

export interface PayEmployee {
  changePending?: boolean
  id: string
  employeeNumber: string
  firstName: string
  lastName: string
  fullName: string
  idNumber: string | null
  dateOfBirth: string | null
  grossSalary: number
  travelAllowance: number
  pensionContribution: number
  medicalAidContribution: number
  bankName: string | null
  bankAccountNumber: string | null
  bankBranchCode: string | null
  startDate: string
  endDate: string | null
  status: "ACTIVE" | "TERMINATED"
  createdAt: string
}

export interface PayRun {
  id: string
  payRunNumber: string
  periodStart: string
  periodEnd: string
  payDate: string
  taxYear: number
  status: "DRAFT" | "PROCESSED"
  totalGross: number | null
  totalPaye: number | null
  totalUif: number | null
  totalSdl: number | null
  totalNet: number | null
  employeeCount: number | null
  processedAt: string | null
}

export interface Payslip {
  id: string
  payEmployeeId: string
  employeeName: string
  employeeNumber: string
  grossSalary: number
  travelAllowance: number
  totalEarnings: number
  payeAmount: number
  uifEmployee: number
  uifEmployer: number
  sdlAmount: number
  medicalAid: number
  pension: number
  totalDeductions: number
  netPay: number
  taxableIncome: number
  taxYear: number
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

export interface PortalAccessGrant {
  id: string
  inviteEmail: string
  status: "PENDING" | "ACTIVE" | "REVOKED"
  invitedAt: string
  acceptedAt: string | null
  revokedAt: string | null
}
