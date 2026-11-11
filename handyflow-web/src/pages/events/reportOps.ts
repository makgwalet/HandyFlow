// src/pages/events/reportOps.ts — pure helpers for the reports hub and the close-out view.
export interface CloseOutCheck { code: string; label: string; passed: boolean; detail: string | null }
export interface CloseOut {
  eventId: string; eventNumber: string | null; title: string; status: string
  attendance: { registered: number; checkedIn: number; notCheckedIn: number; walkIns: number; cancelled: number; transferred: number; attendanceRatePercent: number | null }
  budget: { budgetStatus: string; planned: number; committed: number; invoiced: number; paid: number; forecast: number; variance: number; outstanding: number; costPerRegistered: number | null; costPerCheckedIn: number | null } | null
  suppliers: { vendorId: string; vendorName: string; commitmentCount: number; committed: number; invoiced: number; paid: number; outstanding: number }[]
  checks: CloseOutCheck[]; readyToClose: boolean; generatedAt: string
}

export interface ReportDef { id: string; file: string; name: string; blurb: string; kind: "pii" | "money" }
/** Every download the server offers. PII reports need EXPORT; money reports need budget read; the PDF holds no guest information. */
export const REPORTS: ReportDef[] = [
  { id: "guests", file: "guests.csv", name: "Guest register", blurb: "Every registration. Contains personal information; each download is logged.", kind: "pii" },
  { id: "no-shows", file: "no-shows.csv", name: "No-shows", blurb: "Registered but never checked in. Available once the event is live or finished.", kind: "pii" },
  { id: "check-ins", file: "check-ins.csv", name: "Check-in log", blurb: "Every scan, including failed and repeated ones.", kind: "pii" },
  { id: "budget", file: "budget.csv", name: "Budget vs actual", blurb: "One row per budget line plus a total.", kind: "money" },
  { id: "cost-entries", file: "cost-entries.csv", name: "Invoices and payments", blurb: "Every cost entry, voided ones included and marked.", kind: "money" },
  { id: "close-out", file: "close-out.pdf", name: "Close-out pack (PDF)", blurb: "Attendance, budget vs actual, supplier balances and the checklist. No guest personal information.", kind: "money" },
]
export const visibleReports = (canPii: boolean, canMoney: boolean) => REPORTS.filter(r => (r.kind === "pii" ? canPii : canMoney))

export const failedChecks = (c: CloseOut | undefined) => (c?.checks ?? []).filter(x => !x.passed)
export const ratePercent = (p: number | null | undefined) => (p == null ? "–" : `${Number(p).toFixed(1)}%`)
/** Over budget when the forecast final cost exceeds the plan, i.e. the server's variance is negative. */
export const overBudget = (c: CloseOut | undefined) => !!c?.budget && c.budget.budgetStatus !== "NOT_STARTED" && c.budget.variance < 0
export const closeOutHeadline = (c: CloseOut): string => {
  const f = failedChecks(c).length
  return c.readyToClose ? "Everything is in order to close this event." : `${f} thing${f === 1 ? "" : "s"} still to sort out before closing.`
}
/** Suppliers we still owe money to, biggest first. */
export const owedSuppliers = (c: CloseOut | undefined) => [...(c?.suppliers ?? [])].filter(s => s.outstanding > 0).sort((a, b) => b.outstanding - a.outstanding)
