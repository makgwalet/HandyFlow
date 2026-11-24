// Allowed values and checks for the practice's price book and the price calculator. The server enforces every
// rule; this keeps obviously wrong input from being sent and says what a version still needs before review.
// Prices are ex VAT. Government fees are never in the book: they are added to a quote as disbursements at cost.

export const TAX_TREATMENTS: [string, string][] = [["STANDARD", "Standard rated"], ["ZERO_RATED", "Zero rated"], ["EXEMPT", "Exempt"]]
export const UNIT_MODELS = ["PER_DOCUMENT", "PER_REGISTRATION", "PER_SITE", "PER_EMPLOYEE"]
export const UNIT_NAMES: Record<string, string> = { PER_DOCUMENT: "documents", PER_REGISTRATION: "registrations", PER_SITE: "sites", PER_EMPLOYEE: "employees" }
export const MAX_PERCENT = 200
export const MAX_MONEY = 100_000_000

/** Who may agree a discount of this size. These are proposed starting thresholds, not approved policy. */
export const APPROVER_LABELS: Record<string, string> = {
  CONSULTANT: "A consultant can agree this",
  COMPLIANCE_MANAGER: "Needs a compliance manager",
  PRACTICE_OWNER: "Needs the practice owner",
  PRACTICE_OWNER_WITH_EXCEPTION: "Needs the practice owner and a recorded exception",
}

export type LineDraft = {
  serviceCode: string; pricingModel: string; baseFee: string; unitRate: string; hourlyGrade: string; minFee: string
  urgentSurchargePct: string; taxTreatment: string; notes: string
}
export type RateDraft = { grade: string; hourlyRate: string; costRate: string }
export type BookDraft = { label: string; changeSummary: string; lines: LineDraft[]; rates: RateDraft[] }

export const emptyLine = (): LineDraft => ({ serviceCode: "", pricingModel: "FIXED", baseFee: "", unitRate: "", hourlyGrade: "", minFee: "", urgentSurchargePct: "", taxTreatment: "STANDARD", notes: "" })

export const num = (s: string) => (s.trim() === "" ? null : Number(s))

export function formatMoney(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n)) return "—"
  const [whole, cents] = Math.abs(n).toFixed(2).split(".")
  return `${n < 0 ? "-" : ""}R ${whole.replace(/\B(?=(\d{3})+(?!\d))/g, " ")}.${cents}`
}

export function normaliseGrade(raw: string): string {
  return raw.trim().toUpperCase().replace(/[\s-]+/g, "_")
}

export function moneyProblem(raw: string, what: string): string | null {
  const t = raw.trim()
  if (!t) return null
  const n = Number(t)
  if (!Number.isFinite(n) || n < 0 || n > MAX_MONEY) return `${what} must be between 0 and 100 000 000`
  return null
}

export function percentProblem(raw: string, what: string, max = MAX_PERCENT): string | null {
  const t = raw.trim()
  if (!t) return null
  const n = Number(t)
  if (!Number.isFinite(n) || n < 0 || n > max) return `${what} must be between 0 and ${max} percent`
  return null
}

export function gradeProblem(raw: string): string | null {
  return /^[A-Z0-9_]{2,30}$/.test(normaliseGrade(raw)) ? null : "A grade uses letters, digits and underscores, 2 to 30 characters"
}

/** What a line still needs for its model. Mirrors the server. */
export function lineGaps(l: LineDraft): string[] {
  const hasBase = l.baseFee.trim() !== "", hasUnit = l.unitRate.trim() !== "", hasGrade = l.hourlyGrade.trim() !== ""
  switch (l.pricingModel) {
    case "FIXED": case "PROJECT": case "RETAINER": return hasBase ? [] : ["a base fee"]
    case "HOURLY": return hasGrade ? [] : ["an hourly grade"]
    case "HYBRID": return hasBase || hasUnit || hasGrade ? [] : ["a base fee, a unit rate or an hourly grade"]
    default: return UNIT_MODELS.includes(l.pricingModel) && !hasUnit ? ["a rate per unit"] : []
  }
}

export function lineProblem(l: LineDraft, grades: string[]): string | null {
  if (!l.serviceCode) return "Choose the service"
  const amount = moneyProblem(l.baseFee, "Base fee") ?? moneyProblem(l.unitRate, "Rate per unit") ?? moneyProblem(l.minFee, "Minimum fee")
    ?? percentProblem(l.urgentSurchargePct, "Urgent surcharge")
  if (amount) return amount
  if (l.hourlyGrade.trim() && !grades.includes(normaliseGrade(l.hourlyGrade))) return `Grade ${normaliseGrade(l.hourlyGrade)} has no hourly rate below`
  return null
}

export function rateProblem(r: RateDraft): string | null {
  if (!r.grade.trim()) return "Name the grade"
  const g = gradeProblem(r.grade)
  if (g) return g
  if (!r.hourlyRate.trim()) return "An hourly rate is required"
  return moneyProblem(r.hourlyRate, "Hourly rate") ?? moneyProblem(r.costRate, "Cost rate")
}

/** Anything that stops the draft being saved at all. */
export function saveProblem(d: BookDraft): string | null {
  for (const r of d.rates) { const p = rateProblem(r); if (p) return p }
  const grades = d.rates.map(r => normaliseGrade(r.grade))
  const dupGrade = grades.find((g, i) => grades.indexOf(g) !== i)
  if (dupGrade) return `Grade ${dupGrade} is listed twice`
  for (const l of d.lines) { const p = lineProblem(l, grades); if (p) return p }
  const codes = d.lines.map(l => l.serviceCode)
  const dup = codes.find((c, i) => codes.indexOf(c) !== i)
  if (dup) return `${dup} is priced twice`
  return null
}

/** What a version still needs before it can go for review. Mirrors the server. */
export function missingForReview(d: BookDraft, versionNumber: number): string[] {
  const m: string[] = []
  if (d.lines.length === 0) m.push("at least one priced service")
  const incomplete = d.lines.filter(l => lineGaps(l).length > 0).length
  if (incomplete > 0) m.push(`${incomplete} line(s) with the amounts their model uses`)
  if (versionNumber > 1 && !d.changeSummary.trim()) m.push("what changed")
  return m
}

/** The cost rate is sent only when the person may see it, so an editor never overwrites it with blank. */
export function toBody(d: BookDraft, admin: boolean) {
  return {
    label: d.label, changeSummary: d.changeSummary,
    lines: d.lines.map(l => ({
      serviceCode: l.serviceCode, pricingModel: l.pricingModel, baseFee: num(l.baseFee), unitRate: num(l.unitRate), hourlyGrade: l.hourlyGrade.trim() ? normaliseGrade(l.hourlyGrade) : null,
      minFee: num(l.minFee), urgentSurchargePct: num(l.urgentSurchargePct), taxTreatment: l.taxTreatment, notes: l.notes.trim() || null,
    })),
    rates: d.rates.map(r => ({ grade: normaliseGrade(r.grade), hourlyRate: num(r.hourlyRate), costRate: admin ? num(r.costRate) : null })),
  }
}

/** What the calculator needs from the person for a pricing model, so the form can ask for it. */
export function inputsNeeded(model: string, hasGrade: boolean): { units: boolean; hours: boolean } {
  return { units: UNIT_MODELS.includes(model) || model === "HYBRID", hours: model === "HOURLY" || (model === "HYBRID" && hasGrade) }
}

export type Disb = { description: string; amount: string; vatable: boolean }
export type Extra = { description: string; quantity: string; unitPrice: string }

export function calcProblem(p: { units: string; hours: string; complexityPct: string; discountPct: string; extras: Extra[]; disbursements: Disb[] }, needs: { units: boolean; hours: boolean }, model: string): string | null {
  if (needs.units && model !== "HYBRID" && !p.units.trim()) return "Say how many units"
  if (needs.hours && !p.hours.trim()) return "Say how many hours"
  const n = (s: string) => (s.trim() === "" ? 0 : Number(s))
  if ([p.units, p.hours].some(s => s.trim() !== "" && (!Number.isFinite(Number(s)) || Number(s) < 0))) return "Units and hours must be 0 or more"
  const c = percentProblem(p.complexityPct, "Complexity")
  if (c) return c
  const d = percentProblem(p.discountPct, "Discount", 100)
  if (d) return d
  for (const e of p.extras) {
    if (!e.description.trim()) return "Describe each extra"
    if (!(n(e.quantity) > 0) || moneyProblem(e.unitPrice, "Extra price") || e.unitPrice.trim() === "") return "Each extra needs a quantity and a price"
  }
  for (const x of p.disbursements) {
    if (!x.description.trim()) return "Describe each disbursement"
    if (x.amount.trim() === "" || moneyProblem(x.amount, "Disbursement")) return "Each disbursement needs an amount"
  }
  return null
}
