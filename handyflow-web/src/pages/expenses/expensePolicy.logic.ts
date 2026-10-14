// Pure helpers for expense policies and reimbursement batches (finance F18).

export const CATEGORY_OPTIONS = ["TRAVEL", "MEALS", "ACCOMMODATION", "FUEL", "EQUIPMENT", "OFFICE_SUPPLIES", "MARKETING", "ENTERTAINMENT", "TELEPHONE", "OTHER"]

export interface PolicyForm { category: string; maxPerClaim: string; limitAction: string; receiptAbove: string; maxAgeDays: string }
export const emptyPolicy = (): PolicyForm => ({ category: "*", maxPerClaim: "", limitAction: "WARN", receiptAbove: "", maxAgeDays: "" })

/** Why the policy cannot be saved yet, or null. The server checks again. */
export function policyProblem(f: PolicyForm): string | null {
  if (!f.category) return "Choose a category."
  if (f.maxPerClaim !== "" && !(Number(f.maxPerClaim) > 0)) return "The limit must be more than zero."
  if (f.receiptAbove !== "" && Number(f.receiptAbove) < 0) return "The receipt threshold cannot be negative."
  if (f.maxAgeDays !== "" && !(Number(f.maxAgeDays) >= 1)) return "The age limit must be at least 1 day."
  if (f.maxPerClaim === "" && f.receiptAbove === "" && f.maxAgeDays === "") return "Set at least one rule."
  return null
}

export function policyBody(f: PolicyForm) {
  return {
    category: f.category,
    maxPerClaim: f.maxPerClaim === "" ? null : Number(f.maxPerClaim),
    limitAction: f.maxPerClaim === "" ? null : f.limitAction,
    receiptAbove: f.receiptAbove === "" ? null : Number(f.receiptAbove),
    maxAgeDays: f.maxAgeDays === "" ? null : Number(f.maxAgeDays),
  }
}

const money = (n: number) => `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

/** The rules of one policy in plain words. */
export function describePolicy(p: { maxPerClaim: number | null; limitAction: string | null; receiptAbove: number | null; maxAgeDays: number | null }): string[] {
  const out: string[] = []
  if (p.maxPerClaim != null) out.push(`Up to ${money(p.maxPerClaim)} per claim (${p.limitAction === "BLOCK" ? "more is refused" : "more is flagged to the approver"})`)
  if (p.receiptAbove != null) out.push(p.receiptAbove === 0 ? "Receipt required on every claim" : `Receipt required above ${money(p.receiptAbove)}`)
  if (p.maxAgeDays != null) out.push(`Expenses older than ${p.maxAgeDays} days are flagged`)
  return out
}

/** Claims the person has ticked, minus any already in an unpaid batch. */
export function selectable(claims: { inBatch: boolean }[]): number {
  return claims.filter(c => !c.inBatch).length
}

export function batchTotal(claims: { claimId: string; amount: number }[], chosen: Set<string>): number {
  return Math.round(claims.filter(c => chosen.has(c.claimId)).reduce((a, c) => a + Number(c.amount), 0) * 100) / 100
}
