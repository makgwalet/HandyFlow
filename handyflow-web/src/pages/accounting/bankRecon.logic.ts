// Finance patch F12: small rules behind the bank reconciliation screen.

export interface RuleLike {
  direction: string; matchText: string | null; matchField: string | null
  minAmount: number | null; maxAmount: number | null
}

const FIELD: Record<string, string> = { DESCRIPTION: "description", REFERENCE: "reference", EITHER: "description or reference" }
const WHO: Record<string, string> = { IN: "Money in", OUT: "Money out", ANY: "Any line" }

const money = (n: number) => `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** A rule in plain words, e.g. "Money out, description contains “BANK FEE”, up to R 200.00". */
export function describeRule(r: RuleLike): string {
  const parts = [WHO[r.direction] ?? r.direction]
  if (r.matchText) parts.push(`${FIELD[r.matchField ?? "EITHER"] ?? "description"} contains “${r.matchText}”`)
  if (r.minAmount != null && r.maxAmount != null) parts.push(`between ${money(r.minAmount)} and ${money(r.maxAmount)}`)
  else if (r.minAmount != null) parts.push(`from ${money(r.minAmount)}`)
  else if (r.maxAmount != null) parts.push(`up to ${money(r.maxAmount)}`)
  return parts.join(", ")
}

/** Why a rule form cannot be saved yet, or null. Mirrors the server (a condition is needed; min cannot exceed max). */
export function ruleFormProblem(f: { name: string; matchText: string; minAmount: string; maxAmount: string; otherAccountId: string }): string | null {
  if (!f.name.trim()) return "Give the rule a name."
  if (!f.otherAccountId) return "Choose the account these lines are booked to."
  const min = f.minAmount.trim() === "" ? null : Number(f.minAmount)
  const max = f.maxAmount.trim() === "" ? null : Number(f.maxAmount)
  if ((min != null && Number.isNaN(min)) || (max != null && Number.isNaN(max))) return "Amounts must be numbers."
  if (!f.matchText.trim() && min == null && max == null) return "Add some text to look for, or an amount range."
  if (min != null && max != null && min > max) return "The lowest amount is more than the highest."
  return null
}

/** Whole days to words: "today", "1 day", "12 days". */
export function ageText(days: number): string {
  if (days <= 0) return "today"
  return days === 1 ? "1 day" : `${days} days`
}

/** Statement items older than this many days are shown as stale. */
export const STALE_DAYS = 30
