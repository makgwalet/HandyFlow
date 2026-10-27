// src/pages/hr/payRunPayments.logic.ts
//
// Bank payments for a finalised pay run. The server decides what is allowed; these helpers mirror its rules so the screen can explain
// what to do next instead of waiting for a refusal.

export interface RunPayment {
  id: string; employeeId: string; employeeNumber: string | null; beneficiaryName: string; bankName: string | null; branchCode: string
  accountMasked: string; amount: number; reference: string; status: "PENDING" | "PAID" | "RETURNED"; downloaded: boolean
  paidAt: string | null; returnedAt: string | null; returnReason: string | null; canReturn: boolean
}
export interface SkippedPayment { employeeId: string; employeeNumber: string | null; employeeName: string | null; amount: number; reason: string }
export interface PaymentsSummary {
  payRunId: string; payRunNumber: string; runStatus: string; count: number; total: number; pending: number; paid: number; returned: number
  returnedTotal: number; downloaded: boolean; canMakeUp: boolean; skipped: SkippedPayment[]; payments: RunPayment[]
}

export const MAX_REASON = 300

export function returnProblem(reason: string): string {
  if (!reason.trim()) return "Say why the bank returned it."
  if (reason.trim().length > MAX_REASON) return "The reason is too long."
  return ""
}

/** Run statuses whose payslips are issued, so a bank file can be made. */
export const canPayRun = (status: string) => status === "FINALISED" || status === "COMPLETED"

export const STATUS_LABEL: Record<RunPayment["status"], string> = { PENDING: "Not yet paid", PAID: "Paid", RETURNED: "Returned by bank" }

/** One line telling the person where they are and what comes next. */
export function nextStep(s: PaymentsSummary): string {
  if (s.count === 0) return "No payments have been made up for this run. Make them up to get the bank file."
  if (s.pending > 0 && !s.downloaded) return "Download the bank file and load it on your bank's website."
  if (s.pending > 0) return "Once the bank has accepted the file, confirm that it was paid."
  if (s.returned > 0) return `${s.returned} payment${s.returned === 1 ? " was" : "s were"} returned. Pay ${s.returned === 1 ? "it" : "them"} again outside this file.`
  return "All payments are paid."
}

export const rand = (n: number) => `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
