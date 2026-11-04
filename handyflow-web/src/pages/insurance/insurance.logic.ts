// Insurance front end (both the internal Insurance module and Insurance Brokerage): what the forms offer, what a policy may
// do in each status, and how soon it runs out. The checks mirror the server's own so people see the reason before pressing a
// button; the server still decides.

export const LINES = [["MOTOR", "Motor"], ["PROPERTY", "Property"], ["EQUIPMENT", "Equipment"], ["LIABILITY", "Liability"], ["OTHER", "Other"]] as const
export const ASSET_TYPES = [["VEHICLE", "Vehicle"], ["PROPERTY", "Property"], ["EQUIPMENT", "Equipment"], ["OTHER", "Other"]] as const
export const FREQUENCIES = [["MONTHLY", "Monthly"], ["QUARTERLY", "Quarterly"], ["ANNUAL", "Annual"]] as const
export const STATUS_LABEL: Record<string, string> = {
  QUOTE: "Quote", BOUND: "Bound", ACTIVE: "Active", LAPSED: "Lapsed", CANCELLED: "Cancelled", EXPIRED: "Expired", RENEWED: "Renewed",
}
export type Tone = "neutral" | "info" | "success" | "warning" | "danger"
export const statusTone = (s: string): Tone =>
  ({ QUOTE: "info", BOUND: "info", ACTIVE: "success", LAPSED: "warning", CANCELLED: "danger", EXPIRED: "neutral", RENEWED: "neutral" } as Record<string, Tone>)[s] ?? "neutral"
export const labelOf = (pairs: readonly (readonly [string, string])[], v: string | null | undefined) => pairs.find(p => p[0] === v)?.[1] ?? v ?? "—"

export type Action = "edit" | "bind" | "activate" | "lapse" | "reinstate" | "cancel" | "renew"
/** What can be done to a policy in this status. `brokerage` adds the quote -> bound -> active steps. */
export function actionsFor(status: string, brokerage: boolean): Action[] {
  switch (status) {
    case "QUOTE": return brokerage ? ["edit", "bind", "cancel"] : []
    case "BOUND": return ["edit", "activate", "cancel"]
    case "ACTIVE": return ["edit", "lapse", "renew", "cancel"]
    case "LAPSED": return ["edit", "reinstate", "renew", "cancel"]
    default: return [] // CANCELLED, EXPIRED, RENEWED are finished
  }
}

const DAY = 86_400_000
const asDate = (d: string) => new Date(d.length === 10 ? d + "T00:00:00" : d)
/** Whole days from `today` to expiry (negative once past); null when there is no date. */
export function daysToExpiry(expiry: string | null | undefined, today = new Date()): number | null {
  if (!expiry) return null
  const t = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  return Math.round((asDate(expiry).getTime() - t) / DAY)
}
export const EXPIRY_WINDOW = 60
/** Only cover that is in force can be "running out"; everything else just shows its date. */
export function expiryNote(status: string, expiry: string | null | undefined, today = new Date()): { text: string; tone: Tone } | null {
  if (status !== "ACTIVE" && status !== "LAPSED") return null
  const d = daysToExpiry(expiry, today)
  if (d == null) return null
  if (d < 0) return { text: `Ran out ${-d} day${-d === 1 ? "" : "s"} ago`, tone: "danger" }
  if (d === 0) return { text: "Runs out today", tone: "danger" }
  if (d <= EXPIRY_WINDOW) return { text: `Runs out in ${d} day${d === 1 ? "" : "s"}`, tone: d <= 14 ? "danger" : "warning" }
  return null
}

export interface TermForm { policyNumber: string; premiumAmount: string; sumInsured: string; startDate: string; expiryDate: string }
/** A renewal starts the day after the old term ends and runs a year, which the person can change. */
export function renewalDefaults(expiry: string | null | undefined, sumInsured: number | null | undefined, premium: number | null | undefined): TermForm {
  const iso = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
  const start = expiry ? new Date(asDate(expiry).getTime() + DAY) : new Date()
  const end = new Date(start); end.setFullYear(end.getFullYear() + 1); end.setDate(end.getDate() - 1)
  return { policyNumber: "", premiumAmount: premium != null ? String(premium) : "", sumInsured: sumInsured != null ? String(sumInsured) : "", startDate: iso(start), expiryDate: iso(end) }
}

const num = (s: string) => (s.trim() === "" ? null : Number(s))
export function termProblem(f: TermForm): string | null {
  if (!f.policyNumber.trim()) return "Enter the new policy number"
  const p = num(f.premiumAmount)
  if (p == null || !Number.isFinite(p) || p < 0) return "Enter the premium (0 or more)"
  const s = num(f.sumInsured)
  if (s != null && (!Number.isFinite(s) || s < 0)) return "The sum insured must be 0 or more"
  if (!f.startDate || !f.expiryDate) return "Enter both dates"
  if (f.expiryDate <= f.startDate) return "The end date must be after the start date"
  return null
}
export const termRequest = (f: TermForm) => ({ policyNumber: f.policyNumber.trim(), premiumAmount: num(f.premiumAmount), sumInsured: num(f.sumInsured), startDate: f.startDate, expiryDate: f.expiryDate })

export interface PolicyForm {
  policyNumber: string; insurerName: string; lineOfBusiness: string; assetType: string; assetReference: string; sumInsured: string; premiumAmount: string
  premiumFrequency: string; excessAmount: string; brokerOrInsurerContact: string; startDate: string; expiryDate: string; notes: string
}
export const emptyPolicy = (): PolicyForm => ({ policyNumber: "", insurerName: "", lineOfBusiness: "MOTOR", assetType: "", assetReference: "", sumInsured: "", premiumAmount: "", premiumFrequency: "MONTHLY", excessAmount: "", brokerOrInsurerContact: "", startDate: "", expiryDate: "", notes: "" })
export function policyProblem(f: PolicyForm, creating: boolean): string | null {
  if (creating && !f.policyNumber.trim()) return "Enter the policy number"
  if (!f.insurerName.trim()) return "Enter the insurer"
  const p = num(f.premiumAmount)
  if (p == null || !Number.isFinite(p) || p < 0) return "Enter the premium (0 or more)"
  for (const [label, v] of [["sum insured", f.sumInsured], ["excess", f.excessAmount]] as const) { const n = num(v); if (n != null && (!Number.isFinite(n) || n < 0)) return `The ${label} must be 0 or more` }
  if (!f.startDate || !f.expiryDate) return "Enter both dates"
  if (f.expiryDate <= f.startDate) return "The end date must be after the start date"
  return null
}
export function policyRequest(f: PolicyForm, creating: boolean) {
  const t = (s: string) => s.trim() || null
  return {
    ...(creating ? { policyNumber: f.policyNumber.trim(), lineOfBusiness: f.lineOfBusiness } : {}),
    insurerName: f.insurerName.trim(), assetType: t(f.assetType), assetReference: t(f.assetReference), sumInsured: num(f.sumInsured), premiumAmount: num(f.premiumAmount),
    premiumFrequency: f.premiumFrequency, excessAmount: num(f.excessAmount), brokerOrInsurerContact: t(f.brokerOrInsurerContact), startDate: f.startDate, expiryDate: f.expiryDate, notes: t(f.notes),
  }
}

/** Brokerage quote/edit form: pick a client and an insurer; dates and money may be left open while it is still a quote. */
export interface BrokerPolicyForm {
  clientId: string; insurerId: string; quoteReference: string; lineOfBusiness: string; assetType: string; assetReference: string; sumInsured: string; premiumAmount: string
  premiumFrequency: string; excessAmount: string; commissionRatePct: string; startDate: string; expiryDate: string; notes: string
}
export const emptyBrokerPolicy = (): BrokerPolicyForm => ({ clientId: "", insurerId: "", quoteReference: "", lineOfBusiness: "MOTOR", assetType: "", assetReference: "", sumInsured: "", premiumAmount: "", premiumFrequency: "MONTHLY", excessAmount: "", commissionRatePct: "", startDate: "", expiryDate: "", notes: "" })
export function brokerPolicyProblem(f: BrokerPolicyForm, creating: boolean): string | null {
  if (creating && !f.clientId) return "Choose the client"
  if (!f.insurerId) return "Choose the insurer"
  for (const [label, v] of [["premium", f.premiumAmount], ["sum insured", f.sumInsured], ["excess", f.excessAmount]] as const) { const n = num(v); if (n != null && (!Number.isFinite(n) || n < 0)) return `The ${label} must be 0 or more` }
  const c = num(f.commissionRatePct)
  if (c != null && (!Number.isFinite(c) || c < 0 || c > 100)) return "Commission must be between 0 and 100%"
  if (f.startDate && f.expiryDate && f.expiryDate <= f.startDate) return "The end date must be after the start date"
  return null
}
export function brokerPolicyRequest(f: BrokerPolicyForm, creating: boolean) {
  const t = (s: string) => s.trim() || null
  return {
    ...(creating ? { clientId: f.clientId, quoteReference: t(f.quoteReference) } : {}),
    insurerId: f.insurerId, lineOfBusiness: f.lineOfBusiness, assetType: t(f.assetType), assetReference: t(f.assetReference), sumInsured: num(f.sumInsured), premiumAmount: num(f.premiumAmount),
    premiumFrequency: f.premiumFrequency, excessAmount: num(f.excessAmount), commissionRatePct: num(f.commissionRatePct), startDate: t(f.startDate), expiryDate: t(f.expiryDate), notes: t(f.notes),
  }
}

export const looksLikeEmail = (s: string) => !s.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim())
export interface ClientForm { clientName: string; clientType: string; registrationOrIdNumber: string; contactName: string; contactEmail: string; contactPhone: string; address: string; defaultCommissionRatePct: string; notes: string }
export const emptyClient = (): ClientForm => ({ clientName: "", clientType: "COMMERCIAL", registrationOrIdNumber: "", contactName: "", contactEmail: "", contactPhone: "", address: "", defaultCommissionRatePct: "", notes: "" })
export function clientProblem(f: ClientForm): string | null {
  if (!f.clientName.trim()) return "Enter the client's name"
  if (!looksLikeEmail(f.contactEmail)) return "That email address does not look right"
  const c = num(f.defaultCommissionRatePct)
  if (c != null && (!Number.isFinite(c) || c < 0 || c > 100)) return "Commission must be between 0 and 100%"
  return null
}
export const clientRequest = (f: ClientForm) => { const t = (s: string) => s.trim() || null; return { clientName: f.clientName.trim(), clientType: f.clientType, registrationOrIdNumber: t(f.registrationOrIdNumber), contactName: t(f.contactName), contactEmail: t(f.contactEmail), contactPhone: t(f.contactPhone), address: t(f.address), defaultCommissionRatePct: num(f.defaultCommissionRatePct), notes: t(f.notes) } }

export interface InsurerForm { name: string; contactName: string; contactEmail: string; contactPhone: string; notes: string }
export const emptyInsurer = (): InsurerForm => ({ name: "", contactName: "", contactEmail: "", contactPhone: "", notes: "" })
export function insurerProblem(f: InsurerForm): string | null {
  if (!f.name.trim()) return "Enter the insurer's name"
  if (!looksLikeEmail(f.contactEmail)) return "That email address does not look right"
  return null
}
export const insurerRequest = (f: InsurerForm) => { const t = (s: string) => s.trim() || null; return { name: f.name.trim(), contactName: t(f.contactName), contactEmail: t(f.contactEmail), contactPhone: t(f.contactPhone), notes: t(f.notes) } }

/** What is still owed on a commission invoice, and whether a payment of `amount` is allowed. */
export function paymentProblem(balance: number, amount: string): string | null {
  const n = Number(amount)
  if (amount.trim() === "" || !Number.isFinite(n) || n <= 0) return "Enter an amount above 0"
  if (n > balance + 0.005) return "That is more than the balance owing"
  return null
}
