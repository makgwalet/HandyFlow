// src/pages/hr/savedReports.logic.ts
//
// Saved report views and their reminders. Mirrors SavedReportRules on the server, which has the last word. A saved report holds
// settings only; the figures are always read live under the viewer's own rights.
export type SavedKind = "WORKFORCE" | "TIME" | "PAYROLL" | "COMPLIANCE"
export type SavedSchedule = "NONE" | "WEEKLY" | "MONTHLY"
export type ReportKind = "workforce" | "time" | "payroll" | "compliance"

export interface SavedReport {
  id: string; name: string; kind: SavedKind; kindLabel: string; period: string; periodLabel: string; schedule: SavedSchedule
  runDay: number | null; from: string; to: string; lastNotifiedOn: string | null; createdAt: string
}

export interface SavedForm { name: string; period: string; schedule: SavedSchedule; runDay: string }

export const EMPTY_SAVED_FORM: SavedForm = { name: "", period: "LAST_MONTH", schedule: "NONE", runDay: "" }

export const SAVED_PERIODS: { value: string; label: string }[] = [
  { value: "THIS_MONTH", label: "This month" },
  { value: "LAST_MONTH", label: "Last month" },
  { value: "THIS_QUARTER", label: "This quarter" },
  { value: "LAST_QUARTER", label: "Last quarter" },
  { value: "YEAR_TO_DATE", label: "Year to date" },
  { value: "LAST_90_DAYS", label: "Last 90 days" },
  { value: "LAST_12_MONTHS", label: "Last 12 months" },
]

export const WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

export const MAX_NAME = 80
export const MAX_SAVED = 20

export const toSavedKind = (k: ReportKind): SavedKind => k.toUpperCase() as SavedKind
export const toReportKind = (k: SavedKind): ReportKind => k.toLowerCase() as ReportKind

/** Null when the form can be saved, otherwise a sentence to show. */
export function savedProblem(f: SavedForm): string | null {
  if (!f.name.trim()) return "Give the saved report a name."
  if (f.name.trim().length > MAX_NAME) return `Keep the name to ${MAX_NAME} characters or fewer.`
  if (f.schedule === "NONE") return null
  const day = Number(f.runDay)
  if (!f.runDay || !Number.isInteger(day)) return "Choose the day of the reminder."
  if (f.schedule === "WEEKLY" && (day < 1 || day > 7)) return "Choose a weekday."
  if (f.schedule === "MONTHLY" && (day < 1 || day > 28)) return "Choose a day from 1 to 28, so every month has it."
  return null
}

export function savedBody(f: SavedForm, kind: ReportKind) {
  const reminded = f.schedule !== "NONE"
  return { name: f.name.trim(), kind: toSavedKind(kind), period: f.period, schedule: f.schedule, runDay: reminded ? Number(f.runDay) : null }
}

/** Which saved reports the person may keep: payroll ones only with the payroll right. */
export function mayKeep(kind: ReportKind, payrollRight: boolean): boolean { return kind !== "payroll" || payrollRight }

/** Saved reports the person may open: a payroll one saved before the right was taken away is hidden rather than failing. */
export const visibleSaved = (list: SavedReport[], payrollRight: boolean) => list.filter(s => s.kind !== "PAYROLL" || payrollRight)

export function reminderText(s: Pick<SavedReport, "schedule" | "runDay">): string {
  if (s.schedule === "WEEKLY" && s.runDay) return `Reminder every ${WEEKDAYS[s.runDay - 1] ?? "week"}`
  if (s.schedule === "MONTHLY" && s.runDay) return `Reminder on day ${s.runDay} of each month`
  return "No reminder"
}
