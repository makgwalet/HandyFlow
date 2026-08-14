// src/types/bookingAgency.types.ts — staff-facing types

export interface BookAgencyClient {
  id: string
  tradingName: string
  businessType: string | null
  timezone: string
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  onboardedAt: string
  status: "ACTIVE" | "INACTIVE"
  notes: string | null
  createdAt: string
  monthlyRetainerAmount: number | null
}

export interface BookAgencyResource {
  id: string
  clientId: string
  name: string
  roleDescription: string | null
  workingHoursStart: string | null // "HH:mm:ss"
  workingHoursEnd: string | null
  active: boolean
}

export interface BookAgencyOffering {
  id: string
  clientId: string
  name: string
  durationMinutes: number
  bufferMinutes: number
  price: number | null
  active: boolean
}

export interface BookAgencyBooking {
  id: string
  bookingNumber: string
  clientId: string
  resourceId: string
  resourceName: string
  offeringId: string
  offeringName: string
  customerName: string
  customerPhone: string | null
  customerEmail: string | null
  startDatetime: string
  endDatetime: string
  status: "CONFIRMED" | "CANCELLED" | "COMPLETED" | "NO_SHOW"
  notes: string | null
  createdAt: string
}

export interface PortalAccessGrant {
  id: string
  inviteEmail: string
  status: "PENDING" | "ACTIVE" | "REVOKED"
  invitedAt: string
  acceptedAt: string | null
  revokedAt: string | null
}

export interface BookAgencyInvoice {
      id: string
      invoiceNumber: string
      description: string
      periodStart: string
      periodEnd: string
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