// src/pages/hr/payAdjustments.logic.ts
//
// Bonuses, back pay and recoveries of overpayments. Mirrors PayAdjustmentRules on the server, which has the last word.
export type PayKind = "BONUS" | "BACK_PAY" | "RECOVERY"

export type PayAdjustment = {
  id: string; employeeId: string; employeeNumber: string; employeeName: string; kind: PayKind; kindLabel: string; amount: number
  payFrom: string; description: string; relatesFrom: string | null; relatesTo: string | null; reason: string
  status: "OPEN" | "CANCELLED"; payRunId: string | null; payRunNumber: string | null; canCancel: boolean; cancelReason: string | null; createdAt: string
}

export type PayForm = { kind: PayKind | ""; amount: string; payFrom: string; description: string; relatesFrom: string; relatesTo: string; reason: string }

export const EMPTY_PAY_FORM: PayForm = { kind: "", amount: "", payFrom: "", description: "", relatesFrom: "", relatesTo: "", reason: "" }

export const PAY_KINDS: { value: PayKind; label: string }[] = [
  { value: "BONUS", label: "Bonus" },
  { value: "BACK_PAY", label: "Back pay (money owed for an earlier period)" },
  { value: "RECOVERY", label: "Recovery of an overpayment (taken from net pay)" },
]

export const MAX_PAY_AMOUNT = 10_000_000

/** Prepare rights only: the server also checks. The old permissions stay open for now, as in payroll. */
export function canEnterPay(permissions: string[]): boolean {
  return ["PAYROLL_PREPARE", "PAYROLL_RUN", "USER_UPDATE"].some(p => permissions.includes(p))
}

/** `today` is a yyyy-mm-dd date; passed in so the rule can be tested. */
export function payProblem(f: PayForm, today: string, employmentStart?: string | null): string {
  if (!f.kind) return "Choose bonus, back pay or recovery of an overpayment."
  if (!/^\d+(\.\d{1,2})?$/.test(f.amount.trim()) || Number(f.amount) <= 0) return "Enter the amount, in rand, with at most two decimals."
  if (Number(f.amount) > MAX_PAY_AMOUNT) return "That amount is too large."
  if (!f.payFrom) return "Say from which pay month it should be paid."
  const limit = new Date(today); limit.setDate(limit.getDate() + 366)
  if (f.payFrom > limit.toISOString().slice(0, 10)) return "That date is more than a year away. Check the year."
  if (!f.description.trim()) return "Describe the payment (for example, \"Annual performance bonus 2026\")."
  if (f.description.trim().length > 200) return "The description is too long (200 characters at most)."
  if (!f.reason.trim()) return "Give the reason for the payment."
  if (f.reason.trim().length > 300) return "The reason is too long (300 characters at most)."
  if (f.kind === "BACK_PAY") {
    if (!f.relatesFrom || !f.relatesTo) return "Back pay must say which period it covers."
    if (f.relatesTo < f.relatesFrom) return "The period cannot end before it starts."
    if (f.relatesTo > today) return "Back pay covers a period that has already passed."
    if (employmentStart && f.relatesFrom < employmentStart) return `Back pay cannot start before the employee did (${employmentStart}).`
  } else if (f.relatesFrom || f.relatesTo) {
    return "Only back pay covers a period. Leave the dates out, or choose back pay."
  }
  return ""
}

export function payBody(f: PayForm) {
  return {
    kind: f.kind, amount: Number(f.amount), payFrom: f.payFrom, description: f.description.trim(),
    relatesFrom: f.kind === "BACK_PAY" ? f.relatesFrom : null, relatesTo: f.kind === "BACK_PAY" ? f.relatesTo : null, reason: f.reason.trim(),
  }
}

export function cancelProblem(reason: string): string {
  if (!reason.trim()) return "Give the reason for cancelling."
  if (reason.trim().length > 300) return "The reason is too long (300 characters at most)."
  return ""
}

export const money = (n: number) => n.toLocaleString("en-ZA", { style: "currency", currency: "ZAR" })

// ── Leave taken in advance, taken back from final pay ─────────────────────────────────────────

export type LeaveAdvance = { terminated: boolean; days: number; amount: number; alreadyEntered: boolean }
export type AdvanceForm = { ref: string; date: string }
export const EMPTY_ADVANCE_FORM: AdvanceForm = { ref: "", date: "" }

/** Mirrors the server: the deduction needs the employee's written agreement, named and dated. */
export function advanceProblem(f: AdvanceForm, today: string): string | null {
  if (!f.ref.trim()) return "Name the employee's written agreement to this deduction. Pay may not be deducted without one (BCEA section 34)."
  if (f.ref.trim().length > 100) return "The agreement reference is too long (100 characters at most)."
  if (!f.date) return "Give the date of the written agreement."
  if (f.date > today) return "The agreement cannot be dated in the future."
  return null
}

export const advanceBody = (f: AdvanceForm) => ({ agreementRef: f.ref.trim(), agreementDate: f.date })

/** Whether the panel has anything to show. */
export const advanceShown = (a: LeaveAdvance | undefined): a is LeaveAdvance => !!a && a.terminated && a.days > 0
