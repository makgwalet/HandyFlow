// Finance F28: profitability by cost centre, and the rules that tag journal lines automatically.

export interface Row {
  centreId: string | null; code: string | null; name: string | null; kind: string | null
  income: number; expenses: Record<string, number>; expenseTotal: number; profit: number; marginPercent: number | null
}
export interface View { from: string; to: string; rollUp: boolean; kind: string | null; rows: Row[]; unallocated: Row; total: Row; subtypes: string[] }
export interface Month { month: string; income: number; expenses: Record<string, number>; expenseTotal: number; profit: number }
export interface Trend { centreId: string; code: string; name: string; months: Month[] }
export interface TagRule { id: string; name: string; priority: number; accountCode: string | null; textContains: string | null; entryType: string | null; costCentreId: string; costCentreName: string; active: boolean }
export interface ApplyResult { applied: boolean; matched: number; limitReached: boolean; byRule: Record<string, number>; samples: { date: string; entryNumber: string; accountCode: string; description: string; ruleName: string; costCentre: string }[] }

export const SUBTYPE_LABEL: Record<string, string> = {
  COS: "Cost of sales", PAYROLL: "Wages and salaries", TRAVEL: "Fuel and travel", VEHICLE: "Vehicle costs", RENT: "Rent", UTILITIES: "Utilities",
  COMMS: "Telephone and internet", OFFICE: "Office supplies", MARKETING: "Marketing", PROFESSIONAL: "Professional fees", INSURANCE: "Insurance",
  BANK_CHARGES: "Bank charges", DEPRECIATION: "Depreciation", BAD_DEBTS: "Bad debts", FINANCE: "Finance costs", INTEREST: "Interest",
  STAFF_EXPENSES: "Staff expenses", OPERATING: "Other operating", OTHER: "Other",
}
export const subtypeLabel = (s: string) => SUBTYPE_LABEL[s] ?? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")

/** Costs whose subtype is ticked count as direct; the rest are overheads. */
export function direct(r: Pick<Row, "expenses">, picked: Set<string>): number {
  return Object.entries(r.expenses).reduce((t, [k, v]) => (picked.has(k) ? t + v : t), 0)
}

export interface Split { income: number; direct: number; contribution: number; overhead: number; profit: number; margin: number | null }

export function split(r: Row, picked: Set<string>): Split {
  const d = direct(r, picked)
  const overhead = r.expenseTotal - d
  return { income: r.income, direct: d, contribution: r.income - d, overhead, profit: r.profit, margin: r.income > 0 ? Math.round((r.profit / r.income) * 1000) / 10 : null }
}

export const money = (n: number | null | undefined) =>
  n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const cell = (n: number) => (Math.abs(n) < 0.005 ? "—" : money(n))
export const marginText = (m: number | null) => (m == null ? "—" : `${m.toFixed(1)}%`)

/** Rows that lose money, worst first. */
export const lossMakers = (rows: Row[]) => rows.filter(r => r.profit < -0.004).sort((a, b) => a.profit - b.profit)

export function monthLabel(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("en-ZA", { month: "short", year: "numeric" })
}

export const ENTRY_TYPES: { value: string; label: string }[] = [
  { value: "", label: "Any journal type" }, { value: "MANUAL", label: "Manual" }, { value: "INVOICE", label: "Invoice" }, { value: "PAYMENT", label: "Payment" },
  { value: "BANK", label: "Bank" }, { value: "DEPRECIATION", label: "Depreciation" }, { value: "ADJUSTMENT", label: "Adjustment" }, { value: "VAT", label: "VAT" },
]

export function ruleProblem(name: string, account: string, text: string, entryType: string, centre: string): string | null {
  if (!name.trim()) return "Name the rule."
  if (!account.trim() && !text.trim() && !entryType) return "Give the rule at least one condition: an account, some words, or a journal type."
  if (text.trim() && text.trim().length < 3) return "Words to look for need at least 3 characters."
  if (!centre) return "Choose the cost centre to tag with."
  return null
}

export function ruleSummary(r: Pick<TagRule, "accountCode" | "textContains" | "entryType">): string {
  const parts: string[] = []
  if (r.accountCode) parts.push(`account ${r.accountCode}`)
  if (r.textContains) parts.push(`text contains “${r.textContains}”`)
  if (r.entryType) parts.push(`${r.entryType.toLowerCase()} journals`)
  return parts.join(" and ")
}

export function yearStart(d: Date, startMonth = 3): string {
  const y = d.getMonth() + 1 >= startMonth ? d.getFullYear() : d.getFullYear() - 1
  return `${y}-${String(startMonth).padStart(2, "0")}-01`
}
