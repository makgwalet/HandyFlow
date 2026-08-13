// src/types/recruitmentAgency.types.ts — staff-facing types

export interface AgencyClient {
  id: string
  tradingName: string
  registrationNumber: string | null
  industry: string | null
  placementFeePct: number | null
  effectivePlacementFeePct: number
  guaranteePeriodDays: number | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  onboardedAt: string
  status: "ACTIVE" | "INACTIVE"
  notes: string | null
  createdAt: string
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

export interface Candidate {
  id: string
  fullName: string
  email: string | null
  phone: string | null
  currentTitle: string | null
  currentEmployer: string | null
  skills: string | null
  source: string | null
  cvFileName: string | null
  hasCv: boolean
  notes: string | null
  status: "ACTIVE" | "PLACED" | "DO_NOT_CONTACT"
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

export interface StageHistory {
  id: string
  fromStage: string | null
  toStage: string
  notes: string | null
  changedAt: string
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

export interface PortalAccessGrant {
  id: string
  inviteEmail: string
  status: "PENDING" | "ACTIVE" | "REVOKED"
  invitedAt: string
  acceptedAt: string | null
  revokedAt: string | null
}

// Stages, in pipeline order — used for the advance-stage dropdown and badge coloring
export const PLACEMENT_STAGES = [
  "SUBMITTED", "CLIENT_REVIEW", "CLIENT_INTERVIEW", "OFFERED",
  "PLACED", "GUARANTEE_PERIOD", "COMPLETED",
] as const
export const TERMINAL_STAGES = ["REJECTED_BY_CLIENT", "WITHDRAWN", "CANDIDATE_DECLINED", "FAILED_GUARANTEE", "COMPLETED"]
