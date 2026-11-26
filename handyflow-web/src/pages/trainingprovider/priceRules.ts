// Wording and checks for programme price books. The provider enters every fee and says where it comes from; nothing is seeded and no VAT rate is held.
// A line is never edited: a changed fee is a new line and the old one is withdrawn. Invoicing only shows a line as a suggestion.
// The server enforces every rule, including overlap; these save a round trip.

export type PriceLine = {
  id: string; programmeVersionId: string; funderId: string | null; funderName: string | null; feeType: string; description: string | null; amount: number; includesVat: boolean
  validFrom: string; validTo: string | null; sourceNote: string; status: string; withdrawnReason: string | null; inForceToday: boolean; createdAt: string
}
export type SuggestionRow = { priceLineId: string; feeType: string; description: string | null; amount: number; includesVat: boolean; general: boolean; funderName: string | null; sourceNote: string }
export type Suggestion = { enrolmentId: string; on: string; note: string; rows: SuggestionRow[] }

export const MIN_NOTE = 10
export const FEE_TYPES: Record<string, string> = { TUITION: "Tuition", REGISTRATION: "Registration", ASSESSMENT: "Assessment", MATERIALS: "Materials", CERTIFICATION: "Certification", OTHER: "Other" }
export const BOOK_NOTE = "These are fees the provider has entered, each with where it comes from. Nothing is seeded and nothing is billed from here. To change a fee, withdraw the old line and add a new one."
export const NONE_NOTE = "No fees are recorded for this programme version."

export type PriceForm = { feeType: string; description: string; funderId: string; amount: string; includesVat: string; validFrom: string; validTo: string; sourceNote: string }
export const emptyPrice = (today: string): PriceForm => ({ feeType: "", description: "", funderId: "", amount: "", includesVat: "", validFrom: today, validTo: "", sourceNote: "" })

export function priceProblem(f: PriceForm): string | null {
  if (!FEE_TYPES[f.feeType]) return "Choose the kind of fee"
  if (f.feeType === "OTHER" && f.description.trim().length < 2) return "Say what the fee is for"
  const a = f.amount.trim() === "" ? NaN : Number(f.amount)
  if (Number.isNaN(a) || a <= 0) return "The amount must be more than 0"
  if (!/^\d+(\.\d{1,2})?$/.test(f.amount.trim())) return "The amount can have at most two decimal places"
  if (f.includesVat !== "yes" && f.includesVat !== "no") return "Say whether the amount includes VAT"
  if (!f.validFrom) return "Give the day the fee starts"
  if (f.validTo && f.validTo < f.validFrom) return "The end date cannot be before the start date"
  if (f.sourceNote.trim().length < MIN_NOTE) return `Say where the fee comes from (at least ${MIN_NOTE} characters)`
  return null
}
export const priceBody = (versionId: string, f: PriceForm) => ({
  programmeVersionId: versionId, funderId: f.funderId || null, feeType: f.feeType, description: f.description.trim() || null, amount: Number(f.amount),
  includesVat: f.includesVat === "yes", validFrom: f.validFrom, validTo: f.validTo || null, sourceNote: f.sourceNote.trim(),
})
export const withdrawProblem = (reason: string): string | null => (reason.trim().length >= MIN_NOTE ? null : `Give a reason of at least ${MIN_NOTE} characters`)
export const money = (n: number): string => `R${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).replace(/\u00a0/g, " ")}`
export const vatText = (includes: boolean): string => (includes ? "including VAT" : "excluding VAT")
export const feeName = (p: { feeType: string; description: string | null }): string => (p.feeType === "OTHER" ? (p.description ?? "Other") : FEE_TYPES[p.feeType] ?? p.feeType)
export const lineText = (p: Pick<PriceLine, "feeType" | "description" | "amount" | "includesVat" | "funderName" | "validFrom" | "validTo">): string =>
  `${feeName(p)}: ${money(p.amount)} ${vatText(p.includesVat)} · ${p.funderName ?? "any funder"} · ${p.validTo ? `${p.validFrom} to ${p.validTo}` : `from ${p.validFrom}, no end date`}`
export const stateText = (p: Pick<PriceLine, "status" | "inForceToday" | "validFrom">, today: string): string =>
  p.status === "WITHDRAWN" ? "Withdrawn" : p.inForceToday ? "In force" : p.validFrom > today ? "Not started" : "Ended"
export const suggestionText = (r: SuggestionRow): string => `${feeName(r)}: ${money(r.amount)} ${vatText(r.includesVat)}${r.general ? "" : ` (${r.funderName ?? "funder"})`}`
