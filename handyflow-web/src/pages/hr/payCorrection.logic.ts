// src/pages/hr/payCorrection.logic.ts
//
// Correcting an issued payslip. The payslip itself is never changed: an underpayment becomes back pay and an overpayment a recovery,
// both taken by the next pay run.
import { canPayRun } from "./payRunPayments.logic"

export type Direction = "UNDERPAID" | "OVERPAID"
export type CorrectionForm = { direction: Direction | ""; amount: string; reason: string }
export const EMPTY_CORRECTION: CorrectionForm = { direction: "", amount: "", reason: "" }
export const MAX_CORRECTION_REASON = 300

export const DIRECTIONS: { value: Direction; label: string; hint: string }[] = [
  { value: "UNDERPAID", label: "Underpaid", hint: "Enter the gross amount owed. It is paid as back pay in the next run and taxed then." },
  { value: "OVERPAID", label: "Overpaid", hint: "Enter the net amount paid by mistake. It is taken from the next run's net pay. Tax already paid on it is not reclaimed." },
]

/** Only an issued run's payslip can be corrected, and only by someone who prepares pay. */
export const canCorrect = (runStatus: string, canPrepare: boolean, employeeLeft = false) => canPayRun(runStatus) && canPrepare && !employeeLeft

export function correctionProblem(f: CorrectionForm, net: number): string | null {
  if (!f.direction) return "Choose whether the employee was underpaid or overpaid."
  const amount = Number(f.amount)
  if (f.amount.trim() === "" || !Number.isFinite(amount) || amount <= 0) return "Enter the amount."
  if (Math.round(amount * 100) !== amount * 100 && Math.abs(Math.round(amount * 100) - amount * 100) > 1e-6) return "Use rands and cents only."
  if (f.direction === "OVERPAID" && amount > net) return "An overpayment cannot be more than the net pay on the payslip."
  if (f.reason.trim().length === 0) return "Say what went wrong."
  if (f.reason.trim().length > MAX_CORRECTION_REASON) return `Keep the reason under ${MAX_CORRECTION_REASON} characters.`
  return null
}

export const correctionBody = (f: CorrectionForm) => ({ direction: f.direction, amount: Number(f.amount), reason: f.reason.trim() })
