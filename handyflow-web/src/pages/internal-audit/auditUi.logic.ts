// Pure helpers behind the Internal Audit screens: what an engagement or plan entry status means for the progress figures, how the
// donut and severity bars are sized, which dates are coming up, and how the lists are filtered and paged. Nothing here reads the
// server; every figure is worked out from rows the screens already load.

import type { AnnualPlan, Engagement, PlanEntry } from "./auditShared"

export type Group = "planned" | "inProgress" | "completed" | "deferred"
export type Tone = "info" | "warning" | "success" | "muted"

export const ENGAGEMENT_STAGES: Record<string, { label: string; group: Group }> = {
  PLANNING: { label: "Planned", group: "planned" },
  FIELDWORK: { label: "Fieldwork", group: "inProgress" },
  REPORTING: { label: "Reporting", group: "inProgress" },
  CLOSED: { label: "Completed", group: "completed" },
}
export const ENTRY_STAGES: Record<string, { label: string; group: Group }> = {
  PLANNED: { label: "Planned", group: "planned" },
  IN_PROGRESS: { label: "In progress", group: "inProgress" },
  COMPLETED: { label: "Completed", group: "completed" },
  DEFERRED: { label: "Deferred", group: "deferred" },
}

export const engagementStage = (status: string) => ENGAGEMENT_STAGES[status] ?? { label: status, group: "planned" as Group }
export const entryStage = (status: string) => ENTRY_STAGES[status] ?? { label: status, group: "planned" as Group }
export const groupTone = (g: Group): Tone => (g === "completed" ? "success" : g === "inProgress" ? "warning" : g === "planned" ? "info" : "muted")

// ── Progress ─────────────────────────────────────────────────────────────────────────────────────

export interface Progress { total: number; planned: number; inProgress: number; completed: number; deferred: number; completionPct: number }

/** Counts by group. Deferred work is shown but left out of the percentage, as the server does for the plan. */
export function progressOf(groups: Group[]): Progress {
  const p: Progress = { total: groups.length, planned: 0, inProgress: 0, completed: 0, deferred: 0, completionPct: 0 }
  for (const g of groups) p[g] += 1
  const live = p.total - p.deferred
  p.completionPct = live > 0 ? Math.round((p.completed / live) * 100) : 0
  return p
}
export const engagementProgress = (engs: Pick<Engagement, "status">[]) => progressOf(engs.map(e => engagementStage(e.status).group))
export const planProgress = (plan: Pick<AnnualPlan, "entries"> | null | undefined) => progressOf((plan?.entries ?? []).map(e => entryStage(e.status).group))

/** Entries by planned quarter 1 to 4, with those not given a quarter counted apart. */
export function quarterCounts(entries: Pick<PlanEntry, "plannedQuarter" | "status">[]): { quarter: number; planned: number; completed: number }[] & { unscheduled?: number } {
  const rows = [1, 2, 3, 4].map(q => ({ quarter: q, planned: 0, completed: 0 })) as { quarter: number; planned: number; completed: number }[] & { unscheduled?: number }
  let none = 0
  for (const e of entries) {
    const q = e.plannedQuarter
    if (!q || q < 1 || q > 4) { none += 1; continue }
    rows[q - 1].planned += 1
    if (entryStage(e.status).group === "completed") rows[q - 1].completed += 1
  }
  rows.unscheduled = none
  return rows
}

// ── Donut and bars ────────────────────────────────────────────────────────────────────────────────

export interface Arc { key: string; color: string; length: number; offset: number }

/** The arcs of a donut of circumference `c`: each part's share of the ring, in order, starting at the top. Parts of nil are left out. */
export function donutArcs(parts: { key: string; value: number; color: string }[], c: number): Arc[] {
  const total = parts.reduce((s, p) => s + Math.max(0, p.value), 0)
  if (total <= 0) return []
  let used = 0
  const out: Arc[] = []
  for (const p of parts) {
    if (p.value <= 0) continue
    const length = (p.value / total) * c
    out.push({ key: p.key, color: p.color, length, offset: -used })
    used += length
  }
  return out
}

const SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]
export interface SeverityRow { severity: string; label: string; open: number; total: number; widthPct: number }

/** Critical to low, always all four, then anything else the server sends. Bar widths are open findings against the largest row. */
export function severityRows(rows: { severity: string; open: number; total: number }[]): SeverityRow[] {
  const by = new Map(rows.map(r => [r.severity.toUpperCase(), r]))
  const keys = [...SEVERITY_ORDER, ...rows.map(r => r.severity.toUpperCase()).filter(s => !SEVERITY_ORDER.includes(s))]
  const max = Math.max(1, ...keys.map(k => by.get(k)?.open ?? 0))
  return keys.map(k => {
    const r = by.get(k)
    const open = r?.open ?? 0
    return { severity: k, label: k.charAt(0) + k.slice(1).toLowerCase(), open, total: r?.total ?? 0, widthPct: Math.round((open / max) * 100) }
  })
}

// ── Dates ─────────────────────────────────────────────────────────────────────────────────────────

const day = (s: string) => { const [y, m, d] = s.slice(0, 10).split("-").map(Number); return Date.UTC(y, m - 1, d) }
export const daysBetween = (from: string, to: string) => Math.round((day(to) - day(from)) / 86400000)

export interface KeyDate { date: string; title: string; detail: string; kind: "start" | "end" | "late"; engagementId: string }

/** Engagement starts and ends coming up within `horizonDays`, and any engagement past its end date that is still open, soonest first. */
export function upcomingDates(engs: Engagement[], today: string, limit = 5, horizonDays = 90): KeyDate[] {
  const out: KeyDate[] = []
  for (const e of engs) {
    const open = engagementStage(e.status).group !== "completed"
    if (!open) continue
    if (e.status === "PLANNING" && e.startDate) {
      const d = daysBetween(today, e.startDate)
      if (d >= 0 && d <= horizonDays) out.push({ date: e.startDate.slice(0, 10), title: e.name, detail: `${e.universeEntryName} starts`, kind: "start", engagementId: e.id })
    }
    if (e.endDate) {
      const d = daysBetween(today, e.endDate)
      if (d < 0) out.push({ date: e.endDate.slice(0, 10), title: e.name, detail: `${e.universeEntryName} ended ${-d} ${-d === 1 ? "day" : "days"} ago and is still open`, kind: "late", engagementId: e.id })
      else if (d <= horizonDays) out.push({ date: e.endDate.slice(0, 10), title: e.name, detail: `${e.universeEntryName} ends`, kind: "end", engagementId: e.id })
    }
  }
  return out.sort((a, b) => a.date.localeCompare(b.date) || a.title.localeCompare(b.title)).slice(0, limit)
}

// ── Lists ─────────────────────────────────────────────────────────────────────────────────────────

const has = (hay: (string | null | undefined)[], q: string) => { const n = q.trim().toLowerCase(); return n === "" || hay.some(h => (h ?? "").toLowerCase().includes(n)) }

export function filterEngagements(engs: Engagement[], f: { group: Group | "all"; q: string }): Engagement[] {
  return engs.filter(e => (f.group === "all" || engagementStage(e.status).group === f.group) && has([e.name, e.universeEntryName], f.q))
}

export function filterEntries(entries: PlanEntry[], f: { risk: string; group: Group | "all"; q: string }): PlanEntry[] {
  return entries.filter(e =>
    (f.risk === "all" || (e.riskLevel ?? "NONE") === f.risk) &&
    (f.group === "all" || entryStage(e.status).group === f.group) &&
    has([e.universeEntryName, e.rationale], f.q))
}

export interface Page<T> { items: T[]; page: number; pages: number; from: number; to: number; total: number }

/** One page of a list; a page number past the end is brought back to the last page. */
export function paginate<T>(items: T[], page: number, size: number): Page<T> {
  const total = items.length
  const pages = Math.max(1, Math.ceil(total / size))
  const p = Math.min(Math.max(1, Math.floor(page) || 1), pages)
  const start = (p - 1) * size
  return { items: items.slice(start, start + size), page: p, pages, from: total === 0 ? 0 : start + 1, to: Math.min(total, start + size), total }
}

/** "12 Apr 2026", or a dash for no date. */
export function shortDate(s: string | null | undefined): string {
  if (!s) return "—"
  const [y, m, d] = s.slice(0, 10).split("-").map(Number)
  return `${d} ${["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"][m - 1]} ${y}`
}
