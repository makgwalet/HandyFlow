// Pure rules for the Service levels screen. The server checks everything again; these save a round trip and word the answers.

export const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const
export type Severity = (typeof SEVERITIES)[number]
export type SlaState = "MET" | "BREACHED" | "AT_RISK" | "PENDING" | "UNTRACKED"
export const MAX_MINUTES = 43200

export interface TargetRow { siteId: string | null; siteName: string | null; severity: Severity; acknowledgeMinutes: number; resolveMinutes: number }
export interface TargetsView { companyDefaults: TargetRow[]; sites: TargetRow[] }
export interface LiveItem {
  incidentId: string; siteId: string; siteName: string; severity: Severity; title: string | null; status: string; recordedAt: string
  state: SlaState; stage: "ACKNOWLEDGE" | "RESOLVE"; overdueMinutes: number | null; remainingMinutes: number | null
}
export interface ReportRow {
  siteId: string | null; siteName: string | null; severity: Severity | null; total: number; tracked: number; met: number; breached: number
  atRisk: number; pending: number; compliancePct: number | null; averageAcknowledgeMinutes: number | null; averageResolveMinutes: number | null
}
export interface Report { from: string; to: string; rows: ReportRow[]; total: ReportRow }

/** 5 → "5 min", 90 → "1 h 30 min", 120 → "2 h", 1500 → "1 d 1 h", 2880 → "2 d". */
export function minutesText(m: number | null | undefined): string {
  if (m == null || !Number.isFinite(m)) return "—"
  const total = Math.max(0, Math.round(m))
  if (total < 60) return `${total} min`
  const d = Math.floor(total / 1440), h = Math.floor((total % 1440) / 60), min = total % 60
  return [d ? `${d} d` : "", h ? `${h} h` : "", min ? `${min} min` : ""].filter(Boolean).join(" ")
}

export function parseMinutes(text: string): number | null {
  const t = text.trim()
  if (!/^\d+$/.test(t)) return null
  const n = Number(t)
  return n >= 1 && n <= MAX_MINUTES ? n : null
}

export function targetError(ackText: string, resolveText: string): string | null {
  const a = parseMinutes(ackText), r = parseMinutes(resolveText)
  if (a === null || r === null) return `Enter whole minutes from 1 to ${MAX_MINUTES} (30 days).`
  if (r < a) return "The time to resolve cannot be shorter than the time to acknowledge."
  return null
}

export function stateLabel(s: SlaState): string {
  return s === "MET" ? "Met" : s === "BREACHED" ? "Breached" : s === "AT_RISK" ? "At risk" : s === "PENDING" ? "On track" : "No target"
}

export function stateTone(s: SlaState): "good" | "bad" | "warn" | "neutral" {
  return s === "MET" || s === "PENDING" ? "good" : s === "BREACHED" ? "bad" : s === "AT_RISK" ? "warn" : "neutral"
}

/** "20 min overdue to acknowledge" / "10 min left to resolve". */
export function liveLine(i: Pick<LiveItem, "state" | "stage" | "overdueMinutes" | "remainingMinutes">): string {
  const what = i.stage === "ACKNOWLEDGE" ? "acknowledge" : "resolve"
  if (i.state === "BREACHED") return `${minutesText(i.overdueMinutes ?? 0)} overdue to ${what}`
  return `${minutesText(i.remainingMinutes ?? 0)} left to ${what}`
}

export function percentText(p: number | null | undefined): string { return p == null ? "—" : `${Number.isInteger(p) ? p : p.toFixed(1)}%` }

/** The last 30 days up to and including today, as the dates the report asks for. Built from the date parts so the viewer's time zone cannot move a day. */
export function defaultRange(now: Date): { from: string; to: string } {
  const pad = (n: number) => String(n).padStart(2, "0")
  const fmt = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29)
  return { from: fmt(start), to: fmt(now) }
}

export function rangeError(from: string, to: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) return "Choose a start date and an end date."
  if (to < from) return "The end date cannot be before the start date."
  const days = (Date.parse(to + "T00:00:00Z") - Date.parse(from + "T00:00:00Z")) / 86400000 + 1
  return days > 366 ? "Choose at most 366 days." : null
}

export function targetLabel(r: TargetRow): string { return `${r.severity}: acknowledge in ${minutesText(r.acknowledgeMinutes)}, resolve in ${minutesText(r.resolveMinutes)}` }

export const SAVE_FAILED = "Could not save the target. Try again."
export const LOAD_FAILED = "Could not load this. Try again."
export const LIMITED_NOTE = "You see the service levels of your sites. Setting targets is for the whole company."

export function failureText(e: unknown, fallback: string): string {
  const m = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  return typeof m === "string" && m.trim() !== "" ? m : fallback
}
