// src/pages/legalcompliance/dashboardLogic.ts
//
// Pure helpers for the Legal & Compliance dashboard. Every figure comes from the executive overview's counts, the calendar and (administrators only) the audit trail report.
// A percentage is null (shown as "—") when there is nothing to measure, never 0% or 100%. There is deliberately no composite "compliance score": the donut is the split of obligations by status.

export type Metrics = Record<string, number>

export const metric = (m: Metrics | undefined, key: string): number => { const v = m?.[key]; return typeof v === "number" && Number.isFinite(v) && v > 0 ? v : 0 }

/** Whole-number percentage, or null when the denominator is zero. */
export const pct = (n: number, d: number): number | null => d > 0 ? Math.round((n / d) * 100) : null
export const pctText = (p: number | null): string => p === null ? "—" : `${p}%`

export const greeting = (hour: number): string => hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening"

export interface StatCard { key: string; label: string; value: number; note: string; tone: "neutral" | "good" | "warn" | "bad"; tab: string }

export function statCards(m: Metrics | undefined): StatCard[] {
  const total = metric(m, "obligations.total"), compliant = metric(m, "obligations.compliant")
  const atRisk = metric(m, "obligations.nonCompliant") + metric(m, "obligations.dueSoon"), overdue = metric(m, "obligations.overdue"), findings = metric(m, "findings.open")
  return [
    { key: "total", label: "Total obligations", value: total, note: overdue > 0 ? `${overdue} overdue` : "None overdue", tone: "neutral", tab: "obligations" },
    { key: "compliant", label: "Compliant", value: compliant, note: `${pctText(pct(compliant, total))} of obligations`, tone: "good", tab: "obligations" },
    { key: "atRisk", label: "At risk", value: atRisk, note: `${pctText(pct(atRisk, total))} of obligations`, tone: atRisk > 0 ? "warn" : "good", tab: "obligations" },
    { key: "overdue", label: "Overdue", value: overdue, note: `${pctText(pct(overdue, total))} of obligations`, tone: overdue > 0 ? "bad" : "good", tab: "obligations" },
    { key: "findings", label: "Open findings", value: findings, note: findings > 0 ? "Requires action" : "None open", tone: findings > 0 ? "warn" : "good", tab: "findings" },
  ]
}

export interface Segment { key: string; label: string; count: number; color: string }
export interface Donut { total: number; segments: Segment[]; compliantPercent: number | null }

/** Obligations split by status. "At risk" is non-compliant plus due soon; anything not in those groups (not yet assessed, for example) is "Other" so the parts always add up to the total. */
export function donut(m: Metrics | undefined): Donut {
  const total = metric(m, "obligations.total"), compliant = metric(m, "obligations.compliant")
  const atRisk = metric(m, "obligations.nonCompliant") + metric(m, "obligations.dueSoon"), overdue = metric(m, "obligations.overdue")
  const other = Math.max(0, total - compliant - atRisk - overdue)
  const segments: Segment[] = [
    { key: "compliant", label: "Compliant", count: compliant, color: "#16A34A" },
    { key: "atRisk", label: "At risk", count: atRisk, color: "#F59E0B" },
    { key: "overdue", label: "Overdue", count: overdue, color: "#DC2626" },
    { key: "other", label: "Other", count: other, color: "#CBD5E1" },
  ]
  return { total: Math.max(total, compliant + atRisk + overdue), segments, compliantPercent: pct(compliant, total) }
}

export interface Arc { key: string; dash: number; offset: number; color: string }

/** Stroke dash values for a circle drawn with pathLength 100: each arc starts where the last ended. Empty segments are skipped. */
export function arcs(d: Donut): Arc[] {
  if (d.total <= 0) return []
  let used = 0
  const out: Arc[] = []
  for (const s of d.segments) {
    if (s.count <= 0) continue
    const dash = (s.count / d.total) * 100
    out.push({ key: s.key, dash, offset: -used, color: s.color })
    used += dash
  }
  return out
}

export interface Bar { key: string; label: string; count: number; heightPercent: number; color: string }

/** Open risks by rating: critical, high, and every other open risk (the overview does not split medium from low). Bar heights are relative to the largest. */
export function riskBars(m: Metrics | undefined): Bar[] {
  const critical = metric(m, "risks.critical"), high = metric(m, "risks.high")
  const rest = Math.max(0, metric(m, "risks.open") - critical - high)
  const max = Math.max(critical, high, rest)
  const h = (n: number) => max === 0 ? 0 : Math.max(n > 0 ? 6 : 0, Math.round((n / max) * 100))
  return [
    { key: "critical", label: "Critical", count: critical, heightPercent: h(critical), color: "#DC2626" },
    { key: "high", label: "High", count: high, heightPercent: h(high), color: "#F59E0B" },
    { key: "rest", label: "Other open", count: rest, heightPercent: h(rest), color: "#16A34A" },
  ]
}

export interface CalendarEntry { date: string; sourceType: string; sourceId: string; title: string; detail: string }
export interface Deadline { key: string; title: string; detail: string; date: string; days: number; sourceType: string; label: "Overdue" | "Due today" | "Due soon" | "Upcoming"; tone: "bad" | "warn" | "info" | "neutral" }

const DAY = 86400000
const dayNumber = (iso: string): number => { const [y, m, d] = iso.slice(0, 10).split("-").map(Number); return Math.round(Date.UTC(y, m - 1, d) / DAY) }
export const daysBetween = (fromIso: string, toIso: string): number => dayNumber(toIso) - dayNumber(fromIso)
export const isoDay = (d: Date): string => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`

/** The nearest entries first (overdue ones lead). "Due soon" is within 7 days. */
export function deadlines(entries: CalendarEntry[], todayIso: string, limit: number): Deadline[] {
  return [...entries]
    .sort((a, b) => a.date.localeCompare(b.date) || a.title.localeCompare(b.title))
    .slice(0, limit)
    .map((e, i) => {
      const days = daysBetween(todayIso, e.date)
      const label: Deadline["label"] = days < 0 ? "Overdue" : days === 0 ? "Due today" : days <= 7 ? "Due soon" : "Upcoming"
      const tone: Deadline["tone"] = days < 0 ? "bad" : days <= 7 ? "warn" : "info"
      return { key: `${e.sourceType}-${e.sourceId}-${i}`, title: e.title, detail: e.detail, date: e.date.slice(0, 10), days, sourceType: e.sourceType, label, tone }
    })
}

export interface Cell { day: number; iso: string; today: boolean; types: string[] }

/** A month as weeks starting on Monday. Days outside the month are null. month is 0 to 11. */
export function monthGrid(year: number, month: number, entries: CalendarEntry[], todayIso: string): (Cell | null)[][] {
  const first = new Date(Date.UTC(year, month, 1)), count = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  const lead = (first.getUTCDay() + 6) % 7
  const byDay = new Map<string, Set<string>>()
  for (const e of entries) { const k = e.date.slice(0, 10); if (!byDay.has(k)) byDay.set(k, new Set()); byDay.get(k)!.add(e.sourceType) }
  const cells: (Cell | null)[] = Array(lead).fill(null)
  for (let d = 1; d <= count; d++) {
    const iso = `${year}-${String(month + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`
    cells.push({ day: d, iso, today: iso === todayIso, types: [...(byDay.get(iso) ?? [])].sort() })
  }
  while (cells.length % 7 !== 0) cells.push(null)
  const weeks: (Cell | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return weeks
}

export interface Activity { when: string; area: string; event: string; by: string }

/** The newest rows of the audit trail report (columns: When, Area, Event, By; newest first). Rows that are too short are skipped. */
export function activityRows(rows: string[][] | undefined, limit: number): Activity[] {
  return (rows ?? []).filter(r => r.length >= 3).slice(0, limit).map(r => ({ when: r[0], area: r[1], event: r[2], by: r[3] ?? "" }))
}

export const SOURCE_COLOURS: Record<string, { color: string; bg: string; label: string }> = {
  OBLIGATION: { color: "#4338CA", bg: "#E0E7FF", label: "Obligation" },
  LITIGATION: { color: "#BE123C", bg: "#FFE4E6", label: "Litigation" },
  CONTRACT_RENEWAL: { color: "#0D9488", bg: "#CCFBF1", label: "Contract" },
  REGULATORY_CHANGE: { color: "#B45309", bg: "#FEF3C7", label: "Reg. change" },
}
export const sourceColour = (t: string) => SOURCE_COLOURS[t] ?? { color: "#475569", bg: "#E2E8F0", label: t }
