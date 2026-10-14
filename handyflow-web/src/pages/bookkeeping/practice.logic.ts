// Bookkeeping front end, part 2: chart of accounts, journals, time, service agreements, invoices, portal users.
import { money } from "./bookkeeping.logic"

export interface Journal { id: string; entryNumber: string; entryDate: string; description: string | null; reference: string | null; entryType: string; status: string; totalDebit: number; totalCredit: number; lines: { id: string; accountId: string; description: string | null; debitAmount: number; creditAmount: number }[] }
export interface TimeEntry { id: string; practitionerName: string | null; entryDate: string; activityType: string; description: string | null; hours: number; hourlyRate: number; lineTotal: number; billable: boolean; status: string; invoiceId: string | null }
export interface Agreement { id: string; billingType: string; monthlyFee: number | null; hourlyRate: number | null; startDate: string; endDate: string | null; status: string }
export interface BkInvoice { id: string; invoiceNumber: string; periodStart: string; periodEnd: string; issueDate: string; dueDate: string; total: number; amountPaid: number; balance: number; status: string }
export interface Grant { id: string; inviteEmail: string; status: string; acceptedAt: string | null; invitedAt: string }

export const ACCOUNT_TYPES = [{ value: "ASSET", label: "Asset" }, { value: "LIABILITY", label: "Liability" }, { value: "EQUITY", label: "Equity" }, { value: "INCOME", label: "Income" }, { value: "EXPENSE", label: "Expense" }]
export const typeLabel = (t: string) => ACCOUNT_TYPES.find(x => x.value === t)?.label ?? t

export function accountProblem(code: string, name: string, type: string): string | null {
  if (!code.trim()) return "Give the account a code."
  if (!name.trim()) return "Give the account a name."
  if (!ACCOUNT_TYPES.some(t => t.value === type)) return "Choose the account type."
  return null
}

// ── journals ────────────────────────────────────────────────────────────
export interface JournalLineForm { accountId: string; description: string; debit: string; credit: string }
export const emptyLine = (): JournalLineForm => ({ accountId: "", description: "", debit: "", credit: "" })
const amt = (s: string) => (s.trim() === "" ? 0 : Number(s))
const cents = (n: number) => Math.round(n * 100)

export function journalTotals(lines: JournalLineForm[]) {
  const debit = lines.reduce((t, l) => t + (Number.isFinite(amt(l.debit)) ? amt(l.debit) : 0), 0)
  const credit = lines.reduce((t, l) => t + (Number.isFinite(amt(l.credit)) ? amt(l.credit) : 0), 0)
  return { debit, credit, difference: (cents(debit) - cents(credit)) / 100 }
}

export function journalProblem(date: string, lines: JournalLineForm[]): string | null {
  if (!date) return "Choose the date."
  const used = lines.filter(l => l.accountId || l.debit.trim() || l.credit.trim())
  if (used.length < 2) return "A journal needs at least two lines."
  for (const l of used) {
    if (!l.accountId) return "Choose an account on every line."
    const d = amt(l.debit), c = amt(l.credit)
    if (!Number.isFinite(d) || !Number.isFinite(c) || d < 0 || c < 0) return "Amounts must be numbers, zero or more."
    if (d > 0 && c > 0) return "A line is either a debit or a credit, not both."
    if (d === 0 && c === 0) return "Every line needs an amount."
  }
  const { difference } = journalTotals(used)
  if (difference !== 0) return `Debits and credits differ by ${money(Math.abs(difference))}.`
  return null
}

export const journalRequest = (clientId: string, date: string, description: string, reference: string, lines: JournalLineForm[]) => ({
  clientId, entryDate: date, description: description.trim() || null, reference: reference.trim() || null, entryType: "MANUAL",
  lines: lines.filter(l => l.accountId).map(l => ({ accountId: l.accountId, description: l.description.trim() || null, debitAmount: amt(l.debit) || 0, creditAmount: amt(l.credit) || 0 })),
})

// ── time ────────────────────────────────────────────────────────────────
export const ACTIVITIES = [{ value: "BOOKKEEPING", label: "Bookkeeping" }, { value: "VAT", label: "VAT" }, { value: "PAYROLL", label: "Payroll" }, { value: "REPORTING", label: "Reporting" }, { value: "ADVISORY", label: "Advisory" }, { value: "ADMIN", label: "Admin" }]
export const activityLabel = (a: string) => ACTIVITIES.find(x => x.value === a)?.label ?? a

export interface TimeForm { date: string; activity: string; description: string; hours: string; rate: string; billable: boolean; who: string }
export function timeProblem(f: TimeForm, today: string): string | null {
  if (!f.date) return "Choose the date."
  if (f.date > today) return "Time cannot be logged in the future."
  const h = Number(f.hours), r = Number(f.rate)
  if (!(h > 0) || h > 24) return "Hours must be more than 0 and at most 24."
  if (!Number.isFinite(r) || r < 0) return "Enter the hourly rate."
  return null
}
export const timeValue = (f: Pick<TimeForm, "hours" | "rate">) => Math.round((Number(f.hours) || 0) * (Number(f.rate) || 0) * 100) / 100
export const unbilledTotal = (rows: TimeEntry[]) => Math.round(rows.filter(r => r.billable).reduce((t, r) => t + r.lineTotal, 0) * 100) / 100

// ── agreements ──────────────────────────────────────────────────────────
export const BILLING_TYPES = [{ value: "RETAINER", label: "Monthly retainer" }, { value: "TIME_AND_MATERIALS", label: "Time and materials (hourly)" }]
export const billingLabel = (b: string) => BILLING_TYPES.find(x => x.value === b)?.label ?? b
export function agreementProblem(type: string, fee: string, rate: string, start: string, end: string): string | null {
  if (!start) return "Choose the start date."
  if (end && end <= start) return "The end date must be after the start date."
  if (type === "RETAINER" && !(Number(fee) > 0)) return "Enter the monthly fee."
  if (type === "TIME_AND_MATERIALS" && !(Number(rate) > 0)) return "Enter the hourly rate."
  return null
}
export const agreementTerms = (a: Agreement) => a.billingType === "RETAINER" ? `${money(a.monthlyFee)} a month` : `${money(a.hourlyRate)} an hour`

// ── invoices ────────────────────────────────────────────────────────────
export function periodProblem(start: string, end: string): string | null {
  if (!start || !end) return "Choose both dates."
  return end < start ? "The end date is before the start date." : null
}
export function paymentProblem(amount: string, balance: number): string | null {
  const n = Number(amount)
  if (!(n > 0)) return "Enter the amount received."
  if (Math.round(n * 100) > Math.round(balance * 100)) return `That is more than the ${money(balance)} still owing.`
  return null
}
export const invoiceStatus = (s: string) => ({ DRAFT: "Draft", SENT: "Sent", PAID: "Paid", PARTIAL: "Part paid", OVERDUE: "Overdue" } as Record<string, string>)[s] ?? s
export const monthRange = (today: Date): [string, string] => {
  const y = today.getMonth() === 0 ? today.getFullYear() - 1 : today.getFullYear(), m = today.getMonth() === 0 ? 12 : today.getMonth()
  const last = new Date(y, m, 0).getDate(), p = (n: number) => String(n).padStart(2, "0")
  return [`${y}-${p(m)}-01`, `${y}-${p(m)}-${p(last)}`]
}
