// src/pages/agriculture/agDashboard.logic.ts
// Pure helpers for the dashboard: labels, due-date wording and bar widths.
export const SEVERITY_LABEL: Record<string, string> = {
  CRITICAL: "Critical", OVERDUE: "Overdue", DUE_TODAY: "Due today", UPCOMING: "Upcoming", MEDIUM: "Watch",
}
export const TYPE_LABEL: Record<string, string> = {
  HEALTH_EVENT_DUE: "Animal health", SCOUTING_FOLLOWUP_DUE: "Scouting", SCOUTING_HIGH_SEVERITY: "Scouting",
  LOW_STOCK: "Stock", HARVEST_DUE: "Harvest",
}
/** An unknown severity or type from a newer server still renders, as a tidied version of its own name. */
export const tidy = (code: string) => code.toLowerCase().replace(/_/g, " ").replace(/^\w/, c => c.toUpperCase())
export const severityLabel = (s: string) => SEVERITY_LABEL[s] ?? tidy(s)
export const typeLabel = (t: string) => TYPE_LABEL[t] ?? tidy(t)

const utcDays = (iso: string) => { const [y, m, d] = iso.split("-").map(Number); return Date.UTC(y, m - 1, d) / 86_400_000 }
export const daysBetween = (from: string, to: string) => Math.round(utcDays(to) - utcDays(from))

/** "Overdue by 3 days", "Due today", "In 5 days"; empty when there is no due date. */
export function dueLabel(due: string | null, today: string): string {
  if (!due) return ""
  const n = daysBetween(today, due)
  if (n < 0) return `Overdue by ${-n} day${n === -1 ? "" : "s"}`
  if (n === 0) return "Due today"
  return `In ${n} day${n === 1 ? "" : "s"}`
}

/** Percentage of the largest value, for bar widths; a non-zero value always shows at least a sliver. */
export function barPercent(value: number, max: number): number {
  if (max <= 0 || value <= 0) return 0
  return Math.max(3, Math.round((value / max) * 100))
}

export function percent(part: number, whole: number): number | null {
  return whole > 0 ? Math.round((part / whole) * 100) : null
}

export const fmtNum = (n: number | null | undefined, dp = 1) =>
  n == null ? "—" : n.toLocaleString("en-ZA", { maximumFractionDigits: dp })
