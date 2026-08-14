// src/types/bookingAgencyPortal.types.ts
//
// UNVERIFIED against a real backend response — mirrors PayrollBureau's
// portal type shape (PortalFeeNote) plus the periodStart/periodEnd
// fields already confirmed in BookingAgencyPage.tsx's own BookAgencyInvoice
// usage. Adjust field names here first if the portal endpoints 400/return
// unexpected shapes — this is the single place that would need fixing.

export interface PortalClientSummary {
  clientId: string
  tradingName: string
}

export interface PortalInvoice {
  id: string
  invoiceNumber: string
  periodStart: string
  periodEnd: string
  total: number
  balance: number
  status: string
  dueDate: string
  daysOverdue: number
}
