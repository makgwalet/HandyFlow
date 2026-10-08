// Scheme money on a claim (CLINIC-DEC-001 to 003, 006). Mirrors the server's ClaimLedgerRules so a button is not offered
// when the click would be refused; the server remains the check.
import { parseAmount } from "./claimPayment"

export interface MoneyClaim {
  status: string
  schemePortion?: number
  schemePaid?: number
  writtenOff?: number
  credited?: number
  schemeOutstanding?: number
}

export type MoneyAction = "paid" | "partial" | "writeOff" | "creditNote" | "void"

export const REASON_MIN = 10
export const REASON_MAX = 500

export function owed(c: MoneyClaim): number {
  if (typeof c.schemeOutstanding === "number") return c.schemeOutstanding
  return Math.max(0, (c.schemePortion ?? 0) - (c.schemePaid ?? 0) - (c.writtenOff ?? 0) - (c.credited ?? 0))
}

export function moneyHasMoved(c: MoneyClaim): boolean {
  return (c.schemePaid ?? 0) > 0 || (c.writtenOff ?? 0) > 0 || (c.credited ?? 0) > 0
}

/** Which money actions make sense for this claim, ignoring who is asking. */
export function moneyActions(c: MoneyClaim): MoneyAction[] {
  const out: MoneyAction[] = []
  const left = owed(c)
  if ((c.status === "ACCEPTED" || c.status === "PARTIAL") && left > 0) out.push("paid", "partial", "writeOff")
  if ((c.status === "ACCEPTED" || c.status === "PARTIAL" || c.status === "REJECTED") && left > 0) out.push("creditNote")
  if (["DRAFT", "SUBMITTED", "ACCEPTED", "REJECTED"].includes(c.status) && !moneyHasMoved(c)) out.push("void")
  return out
}

export function reasonProblem(reason: string): string | null {
  const t = reason.trim()
  if (t.length < REASON_MIN) return `Give a reason of at least ${REASON_MIN} characters`
  if (t.length > REASON_MAX) return `Keep the reason under ${REASON_MAX} characters`
  return null
}

/** Why this write-off or credit note amount cannot be sent, or null. */
export function adjustmentProblem(amountText: string, outstanding: number, reason: string): string | null {
  if (!amountText.trim()) return "Enter the amount"
  const n = parseAmount(amountText)
  if (n === null) return "Enter an amount like 300 or 300.50"
  if (n <= 0) return "The amount must be more than zero"
  if (n > outstanding) return `That is more than the ${rand(outstanding)} still owed`
  return reasonProblem(reason)
}

export const rand = (v: number) => `R ${(v || 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** Sum of typed per-claim amounts for a manual split; null if any typed amount is not a number. */
export function manualTotal(amounts: Record<string, string>): number | null {
  let total = 0
  for (const v of Object.values(amounts)) {
    if (!v.trim()) continue
    const n = parseAmount(v)
    if (n === null) return null
    total += n
  }
  return Math.round(total * 100) / 100
}

/** Why a scheme payment cannot be previewed or recorded yet, or null. */
export function allocationProblem(scheme: string, amountText: string, manual: boolean, amounts: Record<string, string>,
                                  owedById: Record<string, number>, reason: string): string | null {
  if (!scheme.trim()) return "Choose the scheme"
  const n = parseAmount(amountText)
  if (!amountText.trim()) return "Enter the amount received"
  if (n === null || n <= 0) return "Enter an amount like 5000 or 5000.50"
  if (!manual) return null
  const total = manualTotal(amounts)
  if (total === null) return "Every chosen amount must be a number"
  for (const [id, v] of Object.entries(amounts)) {
    const x = v.trim() ? parseAmount(v) : 0
    if (x !== null && x > (owedById[id] ?? 0)) return "A chosen amount is more than that claim still owes"
  }
  if (Math.abs(total - n) > 0.004) return `The chosen amounts add up to ${rand(total)} but the payment is ${rand(n)}`
  return reasonProblem(reason)
}
