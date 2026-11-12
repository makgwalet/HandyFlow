// B04: supplier bank details. A change is proposed by one person and verified by another; only a verified account counts.
export interface BankAccount {
  id: string; supplierId: string; accountHolder: string; bankName: string; branchCode: string | null; accountNumberMasked: string
  accountType: string; status: string; reason: string | null; proposedBy: string; proposedAt: string
  decidedBy: string | null; decidedAt: string | null; decisionNote: string | null
}
export interface BankForm { accountHolder: string; bankName: string; branchCode: string; accountNumber: string; accountType: string; reason: string }
export const emptyBank = (): BankForm => ({ accountHolder: "", bankName: "", branchCode: "", accountNumber: "", accountType: "CURRENT", reason: "" })

export const STATUS_LABEL: Record<string, string> = {
  PENDING_VERIFICATION: "Waiting to be verified", VERIFIED: "Verified", REJECTED: "Rejected", WITHDRAWN: "Withdrawn", SUPERSEDED: "Replaced",
}
export const ACCOUNT_TYPES = [{ code: "CURRENT", label: "Current" }, { code: "SAVINGS", label: "Savings" }, { code: "TRANSMISSION", label: "Transmission" }, { code: "OTHER", label: "Other" }]

export const verifiedOf = (l: BankAccount[]) => l.find(a => a.status === "VERIFIED") ?? null
export const pendingOf = (l: BankAccount[]) => l.find(a => a.status === "PENDING_VERIFICATION") ?? null
/** Someone other than the proposer may decide a waiting proposal. */
export const canDecide = (a: BankAccount, me: string | undefined) => a.status === "PENDING_VERIFICATION" && !!me && a.proposedBy !== me
export const canWithdraw = (a: BankAccount, me: string | undefined) => a.status === "PENDING_VERIFICATION" && !!me && a.proposedBy === me

/** Null when the details are fine to send. A reason is required once there is a verified account to replace. */
export function validateBank(f: BankForm, hasVerified: boolean): string | null {
  if (!f.accountHolder.trim()) return "Enter the account holder's name."
  if (!f.bankName.trim()) return "Enter the bank name."
  if (!/^[0-9]{5,20}$/.test(f.accountNumber.replace(/[\s-]/g, ""))) return "The account number must be 5 to 20 digits."
  if (f.branchCode.trim() && !/^[A-Za-z0-9-]{1,20}$/.test(f.branchCode.replace(/\s/g, ""))) return "The branch code can only hold letters, digits and dashes."
  if (hasVerified && f.reason.trim().length < 5) return "Say why the bank details are changing (at least 5 characters)."
  return null
}
export const toProposal = (f: BankForm) => ({
  accountHolder: f.accountHolder.trim(), bankName: f.bankName.trim(), branchCode: f.branchCode.trim() || null,
  accountNumber: f.accountNumber.replace(/[\s-]/g, ""), accountType: f.accountType, reason: f.reason.trim() || null,
})
