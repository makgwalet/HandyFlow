// src/pages/security/performance.logic.ts
//
// Pure rules for the Performance tab and the Risk rules screen: band labels and tones, the rating form check and
// the threshold check (the same rules the server applies). The score itself is computed on the server.
import type { Tone } from "./guard360.logic"

export interface ScoreComponent { key: string; label: string; weight: number; hasData: boolean; percent: number; points: number; detail: string }
export interface Recommendation { code: string; level: string; title: string; reason: string }
export interface RatingItem {
  id: string; source: string; raterName: string | null; siteName: string | null; ratedOn: string
  punctuality: number; professionalism: number; appearance: number; communication: number; alertness: number; incidentHandling: number
  average: number; comment: string | null; createdByName: string | null
}
export interface RiskSettings {
  reviewAt: number; warningAt: number; investigationAt: number; windowDays: number; misconductAt: number; misconductWindowDays: number
  suspensionReviewOnCritical: boolean; customised: boolean; updatedByName: string | null; updatedAt: string | null
}
export interface Performance {
  score: number | null; band: string; coverage: number; components: ScoreComponent[]; recommendations: Recommendation[]
  basis: { days: number; complaintsCounted: number; substantiatedMisconduct: number; criticalIncidents: number; openUrgentComplaints: number }
  settings: RiskSettings; ratingAverage: number | null; ratingCount: number; ratings: RatingItem[]
}

export const BAND_LABEL: Record<string, string> = { EXCELLENT: "Excellent", GOOD: "Good", NEEDS_ATTENTION: "Needs attention", AT_RISK: "At risk", NOT_ENOUGH_DATA: "Not enough data" }
export const BAND_TONE: Record<string, Tone> = { EXCELLENT: "ok", GOOD: "ok", NEEDS_ATTENTION: "warn", AT_RISK: "bad", NOT_ENOUGH_DATA: "neutral" }
export const LEVEL_TONE: Record<string, Tone> = { INFO: "info", WARN: "warn", URGENT: "bad" }

export const DIMENSIONS: { key: "punctuality" | "professionalism" | "appearance" | "communication" | "alertness" | "incidentHandling"; label: string }[] = [
  { key: "punctuality", label: "Punctuality" }, { key: "professionalism", label: "Professionalism" }, { key: "appearance", label: "Appearance" },
  { key: "communication", label: "Communication" }, { key: "alertness", label: "Alertness" }, { key: "incidentHandling", label: "Incident handling" },
]
export const RATING_SOURCES = [{ value: "CLIENT", label: "Client" }, { value: "SUPERVISOR", label: "Supervisor" }]
export const sourceLabel = (v: string) => RATING_SOURCES.find(s => s.value === v)?.label ?? v.toLowerCase()

/** Bar colour for a component: how much of its weight was earned. */
export function percentTone(percent: number): Tone { return percent >= 80 ? "ok" : percent >= 50 ? "warn" : "bad" }

export function ratingFormError(f: { source: string; ratedOn: string; scores: Record<string, number> }, today: string): string | null {
  if (!f.source) return "Say who gave the rating"
  if (!f.ratedOn) return "Enter the date of the rating"
  if (f.ratedOn > today) return "The date cannot be in the future"
  for (const d of DIMENSIONS) { const v = f.scores[d.key]; if (!v) return `Score ${d.label.toLowerCase()} from 1 to 5` }
  return null
}

export function riskSettingsError(s: { reviewAt: number; warningAt: number; investigationAt: number; windowDays: number; misconductAt: number; misconductWindowDays: number }): string | null {
  if ([s.reviewAt, s.warningAt, s.investigationAt, s.misconductAt].some(n => !Number.isInteger(n) || n < 1)) return "Thresholds must be whole numbers, at least 1"
  if (s.reviewAt > s.warningAt || s.warningAt > s.investigationAt) return "Thresholds must rise: supervisor review, then warning review, then formal investigation"
  if (!Number.isInteger(s.windowDays) || s.windowDays < 7 || s.windowDays > 365) return "The complaint window must be between 7 and 365 days"
  if (!Number.isInteger(s.misconductWindowDays) || s.misconductWindowDays < 30 || s.misconductWindowDays > 730) return "The misconduct window must be between 30 and 730 days"
  return null
}

// ── Score trend ────────────────────────────────────────────────────────────────────────────────────────────────
export interface HistoryPoint { date: string; score: number | null; band: string | null; coverage: number; recommendations: number }

/** The snapshots that carry a score (days without enough data are left as gaps, not drawn as zero). */
export const scoredPoints = (points: HistoryPoint[]) => points.filter(p => p.score !== null)

/** Change between the first and last scored day, or null when there are fewer than two. */
export function trendSummary(points: HistoryPoint[]): { delta: number; from: string; to: string; direction: "up" | "down" | "flat" } | null {
  const s = scoredPoints(points)
  if (s.length < 2) return null
  const delta = (s[s.length - 1].score as number) - (s[0].score as number)
  return { delta, from: s[0].date, to: s[s.length - 1].date, direction: delta > 2 ? "up" : delta < -2 ? "down" : "flat" }
}

/** Plain-words version of the summary, for the line under the chart. */
export function trendText(t: ReturnType<typeof trendSummary>, fmtDate: (d: string) => string): string {
  if (!t) return "The trend appears after two days with a score."
  if (t.direction === "flat") return `Steady since ${fmtDate(t.from)} (${t.delta >= 0 ? "+" : ""}${t.delta}).`
  const n = Math.abs(t.delta)
  return `${t.direction === "up" ? "Up" : "Down"} ${n} point${n === 1 ? "" : "s"} since ${fmtDate(t.from)}.`
}

/** Positions for an SVG line chart over the scored days: x by date, y from 0 to 100 (higher is better). */
export function chartGeometry(points: HistoryPoint[], width: number, height: number, pad = 8) {
  const s = scoredPoints(points)
  if (s.length === 0) return { line: "", dots: [] as { x: number; y: number; date: string; score: number }[] }
  const t = (d: string) => new Date(d + "T00:00:00Z").getTime()
  const t0 = t(s[0].date), span = Math.max(1, t(s[s.length - 1].date) - t0)
  const dots = s.map(p => ({
    x: s.length === 1 ? width / 2 : pad + ((t(p.date) - t0) / span) * (width - 2 * pad),
    y: pad + (1 - (p.score as number) / 100) * (height - 2 * pad),
    date: p.date, score: p.score as number,
  }))
  return { line: dots.map((d, i) => `${i === 0 ? "M" : "L"}${d.x.toFixed(1)} ${d.y.toFixed(1)}`).join(" "), dots }
}

/** The tenant's deployment-readiness requirements, as the server returns them. */
export interface ReadinessOption { value: string; label: string }
export interface ReadinessSettings {
  requiredScreening: string[]; requiredDocuments: string[]
  screeningOptions: ReadinessOption[]; documentOptions: ReadinessOption[]
  customised: boolean; updatedByName?: string | null; updatedAt?: string | null
}

/** Adds the value if it is missing, removes it if present. Never mutates the list. */
export function toggleValue(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter(v => v !== value) : [...list, value]
}

/** Stops an accidental save that would leave readiness resting on the PSiRA registration alone. */
export function readinessSettingsError(screening: string[], documents: string[]): string | null {
  if (screening.length === 0 && documents.length === 0)
    return "Choose at least one screening or document. A guard would otherwise be ready on a PSiRA registration alone."
  return null
}
