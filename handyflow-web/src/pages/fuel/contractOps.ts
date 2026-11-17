// Phase 7c helpers: supply contracts that group and report. A contract holds no prices; it links to the account's price entries.
export interface LinkedPrice { priceId: string; fuelType: string; pricePerLitre: number; minLitres: number; validFrom: string; validTo: string | null; approvalStatus: string; live: boolean; fromLitres?: number; inEffect?: boolean }
export interface Contract {
  id: string; accountId: string; customerName: string | null; contractNumber: string; title: string; startDate: string; endDate: string
  fuelType: string | null; committedLitres: number | null; terms: string | null; status: "ACTIVE" | "UPCOMING" | "EXPIRED" | "CANCELLED"; cancelledReason: string | null
  deliveredLitres: number; percentOfCommitment: number | null; litresRemaining: number | null; daysLeft: number | null; linkedPrices: number; prices: LinkedPrice[]; createdAt: string
}
export interface ContractForm { accountId: string; contractNumber: string; title: string; startDate: string; endDate: string; fuelType: string; committedLitres: string; terms: string }
export const emptyContract = (): ContractForm => ({ accountId: "", contractNumber: "", title: "", startDate: "", endDate: "", fuelType: "", committedLitres: "", terms: "" })
export const fromContract = (c: Contract): ContractForm => ({ accountId: c.accountId, contractNumber: c.contractNumber, title: c.title, startDate: c.startDate, endDate: c.endDate,
  fuelType: c.fuelType ?? "", committedLitres: c.committedLitres == null ? "" : String(c.committedLitres), terms: c.terms ?? "" })

/** Message for the contract form, or null when it can be sent. */
export function validateContract(f: ContractForm, adding: boolean): string | null {
  if (adding && !f.accountId) return "Choose the customer account."
  if (adding && !f.contractNumber.trim()) return "Give the contract a number."
  if (!f.title.trim()) return "Give the contract a title."
  if (!f.startDate || !f.endDate) return "Give the start and end dates."
  if (f.endDate < f.startDate) return "The end date is before the start date."
  if (f.committedLitres.trim() !== "") { const n = Number(f.committedLitres); if (!Number.isFinite(n) || n <= 0) return "Committed litres must be more than zero, or left empty." }
  if (f.terms.length > 2000) return "The terms can be at most 2000 characters."
  return null
}

export function toContractRequest(f: ContractForm, adding: boolean) {
  const common = { title: f.title.trim(), startDate: f.startDate, endDate: f.endDate, fuelType: f.fuelType.trim() || null, committedLitres: f.committedLitres.trim() === "" ? null : Number(f.committedLitres), terms: f.terms.trim() || null }
  return adding ? { accountId: f.accountId, contractNumber: f.contractNumber.trim(), ...common } : common
}

export const STATUS_LABEL: Record<Contract["status"], string> = { ACTIVE: "Active", UPCOMING: "Not started", EXPIRED: "Expired", CANCELLED: "Cancelled" }

/** "4,500 of 10,000 L (45.0%)", or just the litres delivered when no commitment was set. */
export function progressText(c: Contract): string {
  const n = (v: number) => Math.round(v).toLocaleString("en-ZA")
  return c.committedLitres == null ? `${n(c.deliveredLitres)} L delivered` : `${n(c.deliveredLitres)} of ${n(c.committedLitres)} L (${(c.percentOfCommitment ?? 0).toFixed(1)}%)`
}

export function daysLeftText(c: Contract): string | null {
  if (c.daysLeft == null) return null
  return c.daysLeft === 0 ? "Ends today" : `${c.daysLeft} ${c.daysLeft === 1 ? "day" : "days"} left`
}

/** D2: text for a volume step, or nothing for the starting price. */
export function stepText(p: { fromLitres?: number }): string {
  return p.fromLitres && p.fromLitres > 0 ? `from ${p.fromLitres.toLocaleString("en-ZA")} L delivered on this contract` : ""
}

/** The body for linking: only carries a volume when one is entered. */
export function linkBody(fromLitres: string): { fromLitres: number } | undefined {
  const n = Number(fromLitres.replace(/\s/g, "").replace(",", "."))
  return fromLitres.trim() !== "" && Number.isFinite(n) && n > 0 ? { fromLitres: n } : undefined
}
