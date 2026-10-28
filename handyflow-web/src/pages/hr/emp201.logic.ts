// src/pages/hr/emp201.logic.ts
//
// Filing and paying the EMP201. The server decides; these mirror its rules so the form can explain what is missing.

export interface Emp201 {
  id: string; payRunId: string | null; periodStart: string; periodEnd: string; dueDate: string
  totalPaye: number; totalUif: number; totalSdl: number; totalPayable: number
  status: "DRAFT" | "SUBMITTED" | "PAID"; submittedAt: string | null; paymentRef: string | null
  paidOn: string | null; paidAmount: number | null; overdue: boolean; late: boolean; runIssued: boolean
}

export type Emp201State = "NOT_FILED" | "FILED" | "PAID"

export const stateOf = (e: Pick<Emp201, "status">): Emp201State => e.status === "PAID" ? "PAID" : e.status === "SUBMITTED" ? "FILED" : "NOT_FILED"

export const STATE_LABEL: Record<Emp201State, string> = { NOT_FILED: "Not filed", FILED: "Filed, not paid", PAID: "Paid" }

export const canRecordEmp201 = (permissions: readonly string[]) => permissions.includes("PAYROLL_FINALISE")

export const cleanReference = (r: string) => r.replace(/\s/g, "")

export function fileProblem(reference: string): string {
  const r = cleanReference(reference)
  if (!r) return "Enter the payment reference SARS gave when you filed."
  if (!/^[A-Za-z0-9]{6,30}$/.test(r)) return "The payment reference must be 6 to 30 letters or digits."
  return ""
}

export function payProblem(paidOn: string, amount: string, e: Pick<Emp201, "totalPayable" | "periodEnd">, today: string): string {
  if (!paidOn) return "Enter the date it was paid."
  if (paidOn > today) return "The payment date cannot be in the future."
  if (paidOn < e.periodEnd.slice(0, 10)) return "The payment date cannot be before the period ended."
  const n = Number(amount)
  if (!amount.trim() || !Number.isFinite(n) || n <= 0) return "Enter the amount paid."
  if (!/^\d+(\.\d{1,2})?$/.test(amount.trim())) return "The amount can have at most two decimals."
  if (Math.round(n * 100) !== Math.round(e.totalPayable * 100)) return `The amount paid must equal the total payable, ${rand(e.totalPayable)}. Part payments are not recorded here.`
  return ""
}

export const rand = (n: number) => `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
