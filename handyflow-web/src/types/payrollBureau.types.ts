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

export interface PayEmployee {
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
