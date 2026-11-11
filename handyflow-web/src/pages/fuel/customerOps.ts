// Stage 1B helpers: customer account types and validation mirroring the server rules.
export interface CustomerSite { id: string; accountId: string; name: string; address: string | null; contactName: string | null; contactPhone: string | null; deliveryInstructions: string | null; operatingHours: string | null }
export interface CustomerAccount {
  id: string; crmCustomerId: string; customerName: string; customerType: string; creditLimit: number
  paymentTermsDays: number; creditHold: boolean; creditHoldReason: string | null; licenceNumber: string | null
  notes: string | null; siteCount: number; sites: CustomerSite[] | null
}

export const CUSTOMER_TYPES: [string, string][] = [
  ["MINING_COMPANY", "Mining company"], ["MINE_CONTRACTOR", "Mine contractor"], ["AGRICULTURAL_BUSINESS", "Agricultural business"],
  ["COMMERCIAL_FARM", "Commercial farm"], ["CONSTRUCTION_COMPANY", "Construction company"], ["CIVIL_CONTRACTOR", "Civil contractor"],
  ["TRANSPORT_LOGISTICS", "Transport & logistics"], ["FLEET_OPERATOR", "Fleet operator"], ["FUEL_RETAILER", "Fuel retailer"],
  ["INDUSTRIAL", "Industrial"], ["GOVERNMENT", "Government"], ["RESELLER", "Reseller"], ["OTHER", "Other"],
]
export const typeLabel = (t: string) => CUSTOMER_TYPES.find(([k]) => k === t)?.[1] ?? t

export const fmtR = (n: number | null | undefined) =>
  n == null || Number.isNaN(Number(n)) ? "–" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export function validateAccount(customerId: string, type: string, limit: string, terms: string, isNew: boolean): string | null {
  if (isNew && !customerId) return "Choose the customer this account is for."
  if (!type) return "Choose a customer type."
  const l = limit.trim() === "" ? NaN : Number(limit)
  if (Number.isNaN(l) || l < 0) return "Credit limit must be zero or more."
  const t = terms.trim() === "" ? NaN : Number(terms)
  if (!Number.isInteger(t) || t < 0 || t > 365) return "Payment terms must be a whole number of days from 0 to 365."
  return null
}

export const validateHold = (reason: string): string | null =>
  reason.trim().length < 5 ? "Give a reason of at least 5 characters for the credit hold." : null

export const validateSite = (name: string): string | null => (name.trim() === "" ? "Give the site a name." : null)

/** Accounts a delivery can be scheduled for: those not on credit hold. */
export const deliverable = (accounts: CustomerAccount[]) => accounts.filter(a => !a.creditHold)
