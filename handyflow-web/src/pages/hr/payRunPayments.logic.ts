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

// ── File layouts and the company's own account ────────────────────────────────────────────────

export type BankLayout = "GENERIC_CSV" | "BANKSERV_ACB" | "ABSA_BIO"
export const BANK_LAYOUTS: { value: BankLayout; label: string; hint: string }[] = [
  { value: "GENERIC_CSV", label: "Generic CSV", hint: "Name, bank, branch, account, amount and reference. Map the columns at your bank." },
  { value: "BANKSERV_ACB", label: "BankServ (ACB): FNB, Nedbank, Capitec",
    hint: "Fixed-width file for FNB Online Banking's BankServ (ACB) import, which Nedbank and Capitec also list as an accepted import. Needs the company's own account under Payroll settings. Import a small test file first: the bank reports errors before anything is paid." },
  { value: "ABSA_BIO", label: "ABSA Business Integrator (CSV)",
    hint: "Account number, name, branch code, amount and description, with no headings, as ABSA Business Integrator Online maps them. Import a small test file first." },
]

export const layoutFilename = (layout: BankLayout, runNumber: string | undefined) =>
  `salary-payments-${(runNumber ?? "run").replace(/[^A-Za-z0-9-]/g, "")}${layout === "BANKSERV_ACB" ? ".txt" : ".csv"}`

export const layoutMime = (layout: BankLayout) => (layout === "BANKSERV_ACB" ? "text/plain" : "text/csv")

const digits = (s: string) => s.replace(/[\s-]/g, "")

/** Mirrors BankFileRules.nominatedProblem. Blank means "leave as it is" and is fine. */
export function nominatedProblem(branch: string, account: string): string | null {
  if (!branch.trim() && !account.trim()) return null
  if (branch.trim() && !/^\d{6}$/.test(digits(branch))) return "The branch code must be 6 digits."
  if (account.trim() && !/^\d{1,11}$/.test(digits(account))) return "The account number must be 1 to 11 digits."
  return null
}

/** The server reads null as "leave as it is" and blank as "clear". */
export const nominatedBody = (allowSelfApproval: boolean, secondCheck: boolean, branch: string, account: string) => ({
  allowSelfApproval, bankFileSecondCheck: secondCheck,
  nominatedBranch: branch.trim() ? digits(branch) : null,
  nominatedAccount: account.trim() ? digits(account) : null,
})

/** A file download that failed comes back as a blob; its text holds the server's message. */
export async function blobMessage(e: unknown, fallback: string): Promise<string> {
  const data = (e as { response?: { data?: unknown } })?.response?.data
  try {
    const text = data instanceof Blob ? await data.text() : typeof data === "string" ? data : ""
    const msg = text ? (JSON.parse(text) as { message?: string }).message : (data as { message?: string } | undefined)?.message
    return msg || fallback
  } catch { return fallback }
}

// ── The bank's returned-items file ────────────────────────────────────────────────────────────

export type ReturnOutcome = "MATCHED" | "AMBIGUOUS" | "UNMATCHED" | "DUPLICATE" | "ALREADY_RETURNED"
export interface ReturnLine {
  line: number; accountMasked: string; amount: number | null; name: string | null; reason: string; outcome: ReturnOutcome
  paymentId: string | null; beneficiary: string | null
}
export interface ReturnPreview { rows: number; matched: number; lines: ReturnLine[] }

export const OUTCOME_TEXT: Record<ReturnOutcome, string> = {
  MATCHED: "Will be marked as returned",
  AMBIGUOUS: "Fits more than one payment: mark it by hand",
  UNMATCHED: "No payment of this run has this account and amount",
  DUPLICATE: "Already matched by an earlier row",
  ALREADY_RETURNED: "Already marked as returned",
}

/** What the confirm button says, or null when nothing in the file can be applied. */
export function applyText(p: ReturnPreview | null): string | null {
  if (!p || p.matched === 0) return null
  return p.matched === 1 ? "Mark 1 payment as returned" : `Mark ${p.matched} payments as returned`
}

/** Null when the chosen file can be read as text and is not absurdly big. */
export function returnFileProblem(file: { name: string; size: number } | null): string | null {
  if (!file) return "Choose the file the bank sent."
  if (file.size === 0) return "That file is empty."
  if (file.size > 1_000_000) return "That file is too large (1 MB at most)."
  if (!/\.(csv|txt|tsv)$/i.test(file.name)) return "Choose a CSV or text file. Export the unpaid or rejected items from your bank as CSV."
  return null
}
