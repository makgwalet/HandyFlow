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
