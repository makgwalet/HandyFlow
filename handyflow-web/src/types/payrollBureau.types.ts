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
  etiClaimable?: boolean
  etiClaimableNote?: string | null
  clientApprovalRequired?: boolean
  minimumFeePerRun?: number
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
  payGroupId?: string | null
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
  taxNumber?: string | null
  etiIdKind?: string | null
  domesticWorker?: boolean
  connectedPerson?: boolean
  belowMinimumWage?: boolean
  etiMonthsPrior?: number
}

export interface PayRun {
  payGroupId?: string | null
  clientDeclineReason?: string | null
  sentToClientAt?: string | null
  clientApprovedAt?: string | null
  id: string
  payRunNumber: string
  periodStart: string
  periodEnd: string
  payDate: string
  taxYear: number
  status: "DRAFT" | "CALCULATED" | "APPROVED" | "AWAITING_CLIENT" | "CLIENT_APPROVED" | "FINALISED" | "REVERSED"
  totalGross: number | null
  totalPaye: number | null
  totalUif: number | null
  totalSdl: number | null
  totalNet: number | null
  employeeCount: number | null
  processedAt: string | null
  calculatedBy?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  finalisedAt?: string | null
  reversedAt?: string | null
  reversalReason?: string | null
}

export interface PayRunReviewItem {
  severity: "BLOCK" | "WARN"
  code: string
  employeeId: string | null
  employeeName: string | null
  message: string
}

export interface PayRunReview {
  payRunId: string
  status: PayRun["status"]
  previousRunNumber: string | null
  blocking: number
  warnings: number
  findings: PayRunReviewItem[]
}

export interface PayGroup {
  id: string
  name: string
  frequency: "MONTHLY" | "FORTNIGHTLY" | "WEEKLY"
  anchorDate: string | null
  payLagDays: number
  status: "ACTIVE" | "ARCHIVED"
  employeeCount: number
}

export interface NextPeriod {
  periodStart: string
  periodEnd: string
  payDate: string
  taxYear: number
}

export type ComponentRole = "TAXABLE_EARNING" | "BONUS" | "NON_TAXABLE_EARNING" | "DEDUCTION"

export interface PayComponent {
  id: string
  code: string
  name: string
  role: ComponentRole
  sarsCode: string | null
  standard: boolean
  status: "ACTIVE" | "ARCHIVED"
}

export interface EmployeeComponent {
  id: string
  componentId: string
  code: string
  name: string
  role: ComponentRole
  amount: number
}

export interface RunItem {
  id: string
  payEmployeeId: string
  componentId: string
  code: string
  name: string
  role: ComponentRole
  amount: number
  note: string | null
}

export interface BankFile {
  id: string
  fileNumber: number
  status: "ACTIVE" | "SUPERSEDED"
  paymentCount: number
  totalAmount: number
  excluded: string[]
  reason: string | null
  createdAt: string
  downloads: number
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
  status: "DRAFT" | "SENT" | "PARTIAL" | "PAID" | "OVERDUE" | "CANCELLED"
  sentAt: string | null
  paidAt: string | null
  payRunId?: string | null
  cancelReason?: string | null
}

export interface PortalAccessGrant {
  id: string
  inviteEmail: string
  status: "PENDING" | "ACTIVE" | "REVOKED"
  invitedAt: string
  acceptedAt: string | null
  revokedAt: string | null
}

// ── SARS filing (phase 4) ──
export interface EtiLine { employeeId: string; employeeName: string; eligible: boolean; tier: string | null; remuneration: number; incentive: number; reasons: string[] }
export interface Emp201 {
  id: string; clientId: string; taxMonth: string; status: "DRAFT" | "FILED"; employeeCount: number
  totalRemuneration: number; paye: number; uifEmployee: number; uifEmployer: number; sdl: number
  etiCalculated: number; etiBroughtForward: number; etiUsed: number; etiCarriedForward: number; etiClaimed: boolean
  totalPayable: number; dueDate: string; runNumbers: string | null; preparedAt: string; filedAt: string | null
  filingReference: string | null; etiLines: EtiLine[]
}
export interface SarsFinding { severity: "BLOCK" | "WARN"; code: string; message: string }
export interface CertLine { code: string; amount: number }
export interface TaxCertificate {
  employeeId: string; employeeName: string; type: string; income: CertLine[]; deductions: CertLine[]
  nonTaxable3696: number; grossTaxable3699: number; paye4102: number; uif4141: number; sdl4142: number; total4149: number
  blocked: boolean; findings: SarsFinding[]
}
export interface Emp501 {
  clientId: string; period: string; taxYear: number; from: string; to: string; certificateCount: number
  balanced: boolean; blocked: boolean; differences: { what: string; emp201: number; certificates: number; difference: number }[]
  etiUsed: number; etiUnusedAtEnd: number; findings: SarsFinding[]; certificates: TaxCertificate[]
}

export interface PayCharge { id: string; description: string; amount: number; billing: "PER_RUN" | "MONTHLY" | "ANNUAL" }
export interface PayPricing { perEmployeeFee: number; minimumFee: number; charges: PayCharge[] }
export interface ProfitabilityClient {
  clientId: string; clientName: string; runs: number; payslips: number; revenue: number; cost: number
  margin: number; marginPercent: number | null; unbilledRuns: number; unbilledEstimate: number
}
export interface Profitability {
  from: string; to: string; costPerPayslip: number; totalRevenue: number; totalCost: number; totalMargin: number
  totalMarginPercent: number | null; totalUnbilledRuns: number; totalUnbilledEstimate: number; clients: ProfitabilityClient[]
}
