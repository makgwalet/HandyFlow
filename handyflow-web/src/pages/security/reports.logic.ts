// src/pages/security/reports.logic.ts
//
// Pure rules for the Reports page: which inputs each report needs, the URLs, and how "last generated" reads.
// No React, no network.

export type ReportKey = "monthly-summary" | "site-coverage" | "guard-attendance" | "site-access"
export type Scope = "NONE" | "SITE" | "GUARD"

export interface Run { reportKey: string; period: string; subject: string | null; format: string; generatedBy: string | null; generatedAt: string }
export interface Card { key: ReportKey; title: string; description: string; scope: Scope; lastRun: Run | null }
export interface Catalogue { cards: Card[]; recent: Run[] }

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]

/** "2026-09" becomes "September 2026"; anything that is not a month is returned unchanged. */
export function monthLabel(period: string): string {
  const m = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(period)
  return m ? `${MONTHS[Number(m[2]) - 1]} ${m[1]}` : period
}

export function monthOf(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`
}

/** The month a report is most often wanted for: the one that just finished. January steps back into the previous year. */
export function defaultMonth(now: Date = new Date()): string {
  return monthOf(new Date(now.getFullYear(), now.getMonth() - 1, 1))
}

/** Whether the inputs the report needs are filled in. */
export function canGenerate(scope: Scope, p: { month: string; siteId: string; guardId: string }): boolean {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(p.month)) return false
  if (scope === "SITE") return !!p.siteId
  if (scope === "GUARD") return !!p.guardId
  return true
}

/** The JSON and PDF addresses of a report. All four sit under /api/v1/security/reports (site access is served by the gate controller). */
export function reportPaths(key: ReportKey, p: { month: string; siteId: string; guardId: string }): { view: string; pdf: string } {
  const base = `/api/v1/security/reports/${key}`
  const q = key === "monthly-summary" ? `month=${p.month}`
    : key === "guard-attendance" ? `guardId=${p.guardId}&month=${p.month}`
    : `siteId=${p.siteId}&month=${p.month}`
  return { view: `${base}?${q}`, pdf: `${base}/pdf?${q}` }
}

/** "just now", "5 min ago", "3 h ago", "2 d ago", then a date. A clock slightly behind the server reads "just now". */
export function ago(iso: string, now: Date = new Date()): string {
  const mins = Math.floor((now.getTime() - new Date(iso).getTime()) / 60_000)
  if (!Number.isFinite(mins)) return ""
  if (mins < 1) return "just now"
  if (mins < 60) return `${mins} min ago`
  const h = Math.floor(mins / 60)
  if (h < 24) return `${h} h ago`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} d ago`
  return new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
}

/** The card's "last generated" line. */
export function lastRunLine(run: Run | null, now: Date = new Date()): string {
  if (!run) return "Not generated yet"
  return `Generated ${ago(run.generatedAt, now)}${run.generatedBy ? ` by ${run.generatedBy}` : ""}`
}

/** What that run was for, e.g. "September 2026 · Centurion Mall · PDF". */
export function runSummary(run: Run): string {
  return [monthLabel(run.period), run.subject, run.format === "PDF" ? "PDF" : "Viewed"].filter(Boolean).join(" · ")
}

/** Cards in the order the server sent them, unknown keys dropped so a newer server cannot break an older screen. */
export const KNOWN_KEYS: ReportKey[] = ["monthly-summary", "site-coverage", "guard-attendance", "site-access"]
export function knownCards(cards: Card[]): Card[] {
  return cards.filter(c => KNOWN_KEYS.includes(c.key))
}

export function scopeNeeds(scope: Scope): string {
  return scope === "SITE" ? "Pick a site and a month" : scope === "GUARD" ? "Pick a guard and a month" : "Pick a month"
}
