// Scheduled reports: which report, how often, who gets it. Mirrors the server's checks so mistakes show up before saving.
export type ReportType = "PIPELINE" | "SOURCES" | "FUNNEL"
export type Frequency = "WEEKLY" | "MONTHLY"
export const REPORT_TYPES: { id: ReportType; label: string }[] = [{ id: "PIPELINE", label: "Pipeline summary" }, { id: "SOURCES", label: "Lead sources" }, { id: "FUNNEL", label: "Sales funnel" }]
export const FREQUENCIES: { id: Frequency; label: string }[] = [{ id: "WEEKLY", label: "Every Monday" }, { id: "MONTHLY", label: "On the 1st of each month" }]
export const MAX_SCHEDULES = 20, MAX_RECIPIENTS = 20

export interface ReportSchedule { id: string; name: string; reportType: ReportType; frequency: Frequency; recipientIds: string[]; active: boolean; lastSentAt: string | null; description: string }
export interface ReportForm { name: string; reportType: ReportType | ""; frequency: Frequency | ""; recipientIds: string[]; active: boolean }
export const EMPTY_REPORT: ReportForm = { name: "", reportType: "", frequency: "WEEKLY", recipientIds: [], active: true }
export const toReportForm = (s: ReportSchedule): ReportForm => ({ name: s.name, reportType: s.reportType, frequency: s.frequency, recipientIds: s.recipientIds, active: s.active })

export function reportProblem(f: ReportForm): string | null {
  if (!f.name.trim()) return "Name the report."
  if (f.name.trim().length > 120) return "The name can be at most 120 characters."
  if (!f.reportType) return "Choose which report to send."
  if (!f.frequency) return "Choose how often to send it."
  if (f.recipientIds.length > MAX_RECIPIENTS) return `A report can go to at most ${MAX_RECIPIENTS} people.`
  return null
}
export const reportBody = (f: ReportForm) => ({ name: f.name.trim(), reportType: f.reportType, frequency: f.frequency, recipientIds: f.recipientIds, active: f.active })
/** Add the person if missing, remove them if present. */
export const toggledId = (ids: string[], id: string) => (ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id])
export const recipientText = (ids: string[], names: Map<string, string>) =>
  ids.length === 0 ? "the account's admins" : ids.map(i => names.get(i) ?? "Someone").join(", ")
export const lastSentText = (iso: string | null) => {
  if (!iso) return "Not sent yet"
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? "Not sent yet" : `Last sent ${d.toLocaleDateString("en-ZA", { dateStyle: "medium" })}`
}
export const sentToText = (n: number) => (n === 1 ? "Sent to 1 person." : `Sent to ${n} people.`)
