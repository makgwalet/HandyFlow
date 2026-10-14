// Finance F22: helpers for engagement letters, recurring services, workload and WIP.
export const fmtR = (n: number | null | undefined) =>
  `${(n ?? 0) < 0 ? "-" : ""}R ${Math.abs(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export const LETTER_STATUS: Record<string, string> = { DRAFT: "Draft", SENT: "Sent, waiting for the client", ACCEPTED: "Accepted", DECLINED: "Declined", SUPERSEDED: "Replaced by a newer letter", ENDED: "Ended" }
export const FEE_BASIS: Record<string, string> = { FIXED: "Fixed fee", RETAINER: "Monthly retainer", HOURLY: "Hourly", MIXED: "Retainer plus hourly" }
export const SERVICE_LABEL: Record<string, string> = {
  BOOKKEEPING: "Bookkeeping", VAT_RETURNS: "VAT returns", PAYROLL: "Payroll", ANNUAL_FINANCIAL_STATEMENTS: "Annual financial statements",
  TAX_RETURNS: "Tax returns", ADVISORY: "Advisory", OTHER: "Other",
}
export const FREQ_LABEL: Record<string, string> = { MONTHLY: "Every month", QUARTERLY: "Every quarter", ANNUALLY: "Every year" }

export type LetterAction = "edit" | "send" | "accept" | "decline" | "end" | "view"
/** What can be done to a letter in its status. */
export function letterActions(status: string): LetterAction[] {
  switch (status) {
    case "DRAFT": return ["edit", "send", "view"]
    case "SENT": return ["accept", "decline", "view"]
    case "ACCEPTED": return ["end", "view"]
    default: return ["view"]
  }
}

export interface LetterForm { title: string; scope: string; exclusions: string; feeBasis: string; feeAmount: string; feeNotes: string; startDate: string; endDate: string }
export const emptyLetter = (today: string): LetterForm => ({ title: "Accounting and tax services", scope: "", exclusions: "", feeBasis: "RETAINER", feeAmount: "", feeNotes: "", startDate: today, endDate: "" })

export function letterProblem(f: LetterForm): string | null {
  if (!f.title.trim()) return "Give the letter a title."
  if (!f.scope.trim()) return "Say what services are covered."
  if (!f.startDate) return "Choose the start date."
  if (f.endDate && f.endDate < f.startDate) return "The end date is before the start date."
  const needsAmount = f.feeBasis === "FIXED" || f.feeBasis === "RETAINER"
  const amount = Number(f.feeAmount.replace(",", "."))
  if (needsAmount && !(amount > 0)) return "A fixed or retainer fee needs an amount."
  if (f.feeAmount && !(amount >= 0)) return "The fee must be a number."
  return null
}
export const letterRequest = (f: LetterForm) => ({
  title: f.title.trim(), scope: f.scope.trim(), exclusions: f.exclusions.trim() || null, feeBasis: f.feeBasis,
  feeAmount: f.feeAmount.trim() ? Number(f.feeAmount.replace(",", ".")) : null, feeNotes: f.feeNotes.trim() || null,
  startDate: f.startDate, endDate: f.endDate || null,
})
export const formFromLetter = (l: any): LetterForm => ({
  title: l.title, scope: l.scope, exclusions: l.exclusions ?? "", feeBasis: l.feeBasis, feeAmount: l.feeAmount == null ? "" : String(l.feeAmount),
  feeNotes: l.feeNotes ?? "", startDate: l.startDate, endDate: l.endDate ?? "",
})

export interface ScheduleForm { clientId: string; serviceType: string; description: string; frequency: string; firstDate: string; feeAmount: string; includeVat: boolean; budgetHours: string; ownerUserId: string }
export const emptySchedule = (today: string): ScheduleForm => ({ clientId: "", serviceType: "BOOKKEEPING", description: "", frequency: "MONTHLY", firstDate: today, feeAmount: "", includeVat: true, budgetHours: "", ownerUserId: "" })
export function scheduleProblem(f: ScheduleForm): string | null {
  if (!f.clientId) return "Choose the client."
  if (!f.firstDate) return "Choose the first date."
  if (f.feeAmount && !(Number(f.feeAmount.replace(",", ".")) >= 0)) return "The fee must be a number."
  if (f.budgetHours && !(Number(f.budgetHours.replace(",", ".")) >= 0)) return "Budget hours must be a number."
  return null
}
export const scheduleRequest = (f: ScheduleForm) => ({
  serviceType: f.serviceType, description: f.description.trim() || null, frequency: f.frequency, firstDate: f.firstDate, endDate: null,
  feeAmount: f.feeAmount.trim() ? Number(f.feeAmount.replace(",", ".")) : null, includeVat: f.includeVat, dueDays: 30,
  budgetHours: f.budgetHours.trim() ? Number(f.budgetHours.replace(",", ".")) : null, ownerUserId: f.ownerUserId || null,
})

export const overBudget = (usedPercent: number | null | undefined) => usedPercent != null && usedPercent > 100
export const realisationText = (p: number | null | undefined) => p == null ? "no time recorded" : `${p}% of standard value`
