// B06: customer enquiries. An enquiry is raised, then quoted (an ordinary quotation is created) or closed with a reason.
export interface Enquiry {
  id: string; enquiryNumber: string; accountId: string | null; accountName: string | null; contactName: string | null; contactPhone: string | null; contactEmail: string | null
  fuelType: string; litres: number; requestedDate: string | null; source: string; status: "NEW" | "QUOTED" | "LOST" | "CANCELLED"
  quoteOrderId: string | null; quoteOrderNumber: string | null; decisionReason: string | null; notes: string | null; createdAt: string; decidedAt: string | null
}
export const SOURCES: Record<string, string> = { PHONE: "Phone", EMAIL: "Email", WALK_IN: "Walk-in", PORTAL: "Customer portal", OTHER: "Other" }
export const STATUS_LABEL: Record<string, string> = { NEW: "New", QUOTED: "Quoted", LOST: "Lost", CANCELLED: "Cancelled" }
export const isNew = (e: Enquiry) => e.status === "NEW"

export function who(e: Enquiry): string { return e.accountName ?? e.contactName ?? "Unknown" }

export function validateEnquiry(accountId: string, contactName: string, fuelType: string, litres: string): string | null {
  if (!accountId && !contactName.trim()) return "Choose the customer account, or enter who is asking."
  if (!fuelType) return "Choose the fuel type."
  const n = Number(litres)
  if (litres.trim() === "" || !isFinite(n) || n <= 0) return "Enter the litres asked for, above zero."
  return null
}

/** Lost and cancelled both need a reason of at least 5 characters, matching the server. */
export function validateClose(reason: string): string | null {
  return reason.trim().length < 5 ? "Give a reason of at least 5 characters." : null
}

/** A quotation needs a customer account; an enquiry from someone not yet an account must be linked first. */
export const canQuote = (e: Enquiry) => isNew(e) && !!e.accountId
