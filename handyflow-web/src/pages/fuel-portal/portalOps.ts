// Pure helpers for the Fuel customer portal.
export const PORTAL_FUEL_TYPES = ["DIESEL", "PETROL", "PARAFFIN", "GAS"]
export const MAX_LITRES = 500000

export type PortalOrder = {
  id: string; orderNumber: string; siteName: string | null; fuelType: string; litresOrdered: number; pricePerLitre: number | null; value: number | null
  status: string; requestedDate: string | null; customerReference: string | null; litresDelivered: number | null; litresRemaining: number | null; createdAt: string
}
export type PortalDelivery = {
  id: string; deliveredAt: string; orderNumber: string | null; siteName: string | null; fuelType: string; litres: number; pricePerLitre: number | null; total: number | null; receiptNumber: string | null
}
export type PortalAccount = { accountId: string; customerName: string; paymentTermsDays: number }
export type PortalSite = { id: string; name: string }

export const fmtL = (n: number | null | undefined) => n == null || Number.isNaN(Number(n)) ? "–" : `${Number(n).toLocaleString("en-ZA", { maximumFractionDigits: 2 })} L`
export const fmtRand = (n: number | null | undefined) => n == null || Number.isNaN(Number(n)) ? "–" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** A QUOTE is a customer request we have not yet confirmed. */
export function statusLabel(s: string): string {
  switch (s) {
    case "QUOTE": return "Requested"
    case "CONFIRMED": return "Confirmed"
    case "PART_DELIVERED": case "PARTIALLY_DELIVERED": return "Part delivered"
    case "DELIVERED": case "COMPLETED": return "Delivered"
    case "CANCELLED": return "Cancelled"
    default: return s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
  }
}

export function validateRequest(fuelType: string, litres: string, requestedDate: string, today = new Date()): string | null {
  if (!fuelType) return "Choose the fuel type."
  const l = litres.trim() === "" ? NaN : Number(litres)
  if (Number.isNaN(l) || l <= 0) return "Litres must be greater than zero."
  if (l > MAX_LITRES) return "For more than 500,000 litres please contact us."
  if (requestedDate) {
    const d = new Date(requestedDate + "T23:59:59")
    if (d < today) return "The requested date is in the past."
  }
  return null
}

export function validateRegistration(fullName: string, password: string, confirm: string, token: string): string | null {
  if (!token) return "This invite link is missing its token. Please use the link from your invite email."
  if (!fullName.trim()) return "Enter your full name."
  if (password.length < 8) return "Password must be at least 8 characters."
  if (password !== confirm) return "Passwords don't match."
  return null
}
