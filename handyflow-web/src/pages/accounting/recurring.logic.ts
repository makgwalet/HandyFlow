// Pure helpers for recurring journals, accruals and prepayments (finance F17).

export const KIND_LABEL: Record<string, string> = {
  RECURRING: "Recurring journal",
  ACCRUAL: "Accrual (reversed next month)",
  PREPAYMENT: "Prepayment (spread over months)",
}
export const FREQ_LABEL: Record<string, string> = { MONTHLY: "Monthly", QUARTERLY: "Every 3 months", ANNUALLY: "Yearly" }
export const STATUS_LABEL: Record<string, string> = { ACTIVE: "Active", PAUSED: "Paused", ENDED: "Ended" }

export interface FormLine { accountId: string; description: string; debit: string; credit: string }
export interface ScheduleForm {
  name: string; kind: string; frequency: string; startDate: string; endDate: string; runs: string
  description: string; reference: string; lines: FormLine[]
  totalAmount: string; expenseAccountId: string; prepaidAccountId: string
}

export const emptyLine = (): FormLine => ({ accountId: "", description: "", debit: "", credit: "" })
export const emptyForm = (today: string): ScheduleForm => ({
  name: "", kind: "RECURRING", frequency: "MONTHLY", startDate: today, endDate: "", runs: "", description: "", reference: "",
  lines: [emptyLine(), emptyLine()], totalAmount: "", expenseAccountId: "", prepaidAccountId: "",
})

const num = (s: string) => (s === "" ? 0 : Number(s))

export function lineTotals(lines: FormLine[]) {
  const debit = lines.reduce((a, l) => a + num(l.debit), 0)
  const credit = lines.reduce((a, l) => a + num(l.credit), 0)
  return { debit: Math.round(debit * 100) / 100, credit: Math.round(credit * 100) / 100 }
}

/** Why the form cannot be saved yet, or null. The server checks again. */
export function formProblem(f: ScheduleForm): string | null {
  if (!f.name.trim()) return "Give the schedule a name."
  if (!f.description.trim()) return "Enter the journal description."
  if (!f.startDate) return "Choose the first date."
  if (f.endDate && f.endDate < f.startDate) return "The end date is before the first date."
  if (f.runs !== "" && !(Number(f.runs) >= 1)) return "The number of runs must be at least 1."
  if (f.kind === "PREPAYMENT") {
    if (!(num(f.totalAmount) > 0)) return "Enter the amount being spread."
    if (!(Number(f.runs) >= 2)) return "Spread a prepayment over at least 2 periods."
    if (!f.expenseAccountId || !f.prepaidAccountId) return "Choose the expense and prepaid accounts."
    if (f.expenseAccountId === f.prepaidAccountId) return "The expense and prepaid accounts must differ."
    return null
  }
  const used = f.lines.filter(l => l.accountId || l.debit || l.credit)
  if (used.length < 2) return "A journal needs at least two lines."
  if (used.some(l => !l.accountId)) return "Every line needs an account."
  if (used.some(l => num(l.debit) > 0 && num(l.credit) > 0)) return "A line is a debit or a credit, not both."
  if (used.some(l => !(num(l.debit) > 0) && !(num(l.credit) > 0))) return "Every line needs an amount."
  const t = lineTotals(used)
  if (t.debit !== t.credit) return `Debits (${t.debit.toFixed(2)}) do not equal credits (${t.credit.toFixed(2)}).`
  return null
}

/** Body for POST /recurring-journals. */
export function toRequest(f: ScheduleForm) {
  const prepay = f.kind === "PREPAYMENT"
  return {
    name: f.name.trim(), kind: f.kind, frequency: f.frequency, startDate: f.startDate, endDate: f.endDate || null,
    runs: f.runs === "" ? null : Number(f.runs), description: f.description.trim(), reference: f.reference.trim() || null,
    lines: prepay ? null : f.lines.filter(l => l.accountId).map(l => ({
      accountId: l.accountId, description: l.description || null, debit: num(l.debit) || 0, credit: num(l.credit) || 0 })),
    totalAmount: prepay ? num(f.totalAmount) : null,
    expenseAccountId: prepay ? f.expenseAccountId : null, prepaidAccountId: prepay ? f.prepaidAccountId : null,
  }
}

/** One line of plain words about what happens next. */
export function nextText(s: { status: string; nextRunDate: string | null; runsDone: number; runsTotal: number | null }): string {
  if (s.status === "ENDED") return `Finished after ${s.runsDone} run(s).`
  if (s.status === "PAUSED") return "Paused: nothing will be made until it is resumed."
  const left = s.runsTotal == null ? "" : `, ${s.runsTotal - s.runsDone} to go`
  return s.nextRunDate ? `Next draft on ${s.nextRunDate}${left}.` : "Nothing more to make."
}
