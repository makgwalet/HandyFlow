// Phase 6b helpers: reorder rules. The server is the authority; this mirrors its limits so mistakes are caught before sending.
export interface Rule {
  id: string; tankId: string; tankName: string; fuelType: string | null; currentLitres: number | null; supplierId: string | null; supplierName: string | null
  reorderBelowLitres: number; orderLitres: number; estimatedPrice: number | null; enabled: boolean; belowLevel: boolean; lastCheckedAt: string | null; lastDraftedAt: string | null
}
export interface RuleForm { tankId: string; supplierId: string; below: string; qty: string; price: string; enabled: boolean }
export interface LogRow { id: string; ruleId: string; tankName: string | null; outcome: "DRAFTED" | "FAILED"; detail: string | null; requisitionId: string | null; ranAt: string }
export interface RunResult { checked: number; drafted: number; alreadyOpen: number; failed: number; requisitionNumbers: string[] }

export const emptyRule = (): RuleForm => ({ tankId: "", supplierId: "", below: "", qty: "", price: "", enabled: true })
export const fromRule = (r: Rule): RuleForm => ({ tankId: r.tankId, supplierId: r.supplierId ?? "", below: String(r.reorderBelowLitres), qty: String(r.orderLitres), price: r.estimatedPrice == null ? "" : String(r.estimatedPrice), enabled: r.enabled })

export function validateRule(f: RuleForm, adding: boolean): string | null {
  if (adding && !f.tankId) return "Choose the tank."
  const below = Number(f.below), qty = Number(f.qty)
  if (!f.below.trim() || Number.isNaN(below) || below <= 0) return "Enter the level, in litres, below which a requisition should be drafted."
  if (!f.qty.trim() || Number.isNaN(qty) || qty <= 0) return "Enter how many litres to ask for."
  if (f.price.trim() && (Number.isNaN(Number(f.price)) || Number(f.price) < 0)) return "The estimated price cannot be negative."
  return null
}
export const toRuleRequest = (f: RuleForm, adding: boolean) => ({
  tankId: adding ? f.tankId : null, supplierId: f.supplierId || null, reorderBelowLitres: Number(f.below), orderLitres: Number(f.qty),
  estimatedPrice: f.price.trim() ? Number(f.price) : null, enabled: f.enabled,
})

/** A sentence for the result of "Check now" or a daily run, so the person knows exactly what happened. */
export function runText(r: RunResult): string {
  if (r.checked === 0) return "No rules are switched on, so nothing was checked."
  const parts: string[] = []
  parts.push(r.drafted > 0 ? `${r.drafted} requisition${r.drafted === 1 ? "" : "s"} drafted (${r.requisitionNumbers.join(", ")}). Nothing was submitted or ordered.` : "No requisition was drafted.")
  if (r.alreadyOpen > 0) parts.push(`${r.alreadyOpen} tank${r.alreadyOpen === 1 ? " is" : "s are"} below the level but already ${r.alreadyOpen === 1 ? "has" : "have"} a requisition or order on the way.`)
  if (r.failed > 0) parts.push(`${r.failed} rule${r.failed === 1 ? "" : "s"} could not run: see the log below.`)
  return parts.join(" ")
}

/** Message for the "variance open too long" days box, or null. Empty is allowed: it switches the alert off. */
export function validateReviewDays(text: string): string | null {
  if (text.trim() === "") return null
  const n = Number(text)
  if (!Number.isInteger(n) || n < 1 || n > 365) return "Days must be a whole number from 1 to 365, or empty for no alert."
  return null
}
export const reviewDaysValue = (text: string): number | null => text.trim() === "" ? null : Number(text)
