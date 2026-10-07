// src/pages/security/reviews.logic.ts
//
// Pure rules for the supervisor review form on the Performance tab. The server applies the same checks.
import type { Tone } from "./guard360.logic"

export interface ReviewItem {
  id: string; siteId: string | null; siteName: string | null; reviewDate: string; periodFrom: string; periodTo: string; reviewerName: string
  overall: string; punctuality: number; professionalism: number; appearance: number; communication: number; alertness: number; incidentHandling: number
  average: number; strengths: string | null; improvements: string | null; trainingNeeds: string | null; actionsAgreed: string | null; followUpDate: string | null; createdAt: string
}
export interface ReviewList { reviews: ReviewItem[]; lastReviewOn: string | null; daysSinceLast: number | null; dueState: "NONE" | "OVERDUE" | "FOLLOW_UP_DUE" | "OK"; intervalDays: number }

export const OVERALL = [
  { value: "EXCEEDS", label: "Exceeds expectations", tone: "ok" as Tone },
  { value: "MEETS", label: "Meets expectations", tone: "info" as Tone },
  { value: "BELOW", label: "Below expectations", tone: "warn" as Tone },
]
export const overallLabel = (v: string) => OVERALL.find(o => o.value === v)?.label ?? v.toLowerCase()
export const overallTone = (v: string): Tone => OVERALL.find(o => o.value === v)?.tone ?? "neutral"

/** The line above the list: whether a review is due, in plain words. */
export function dueLine(l: ReviewList): { tone: Tone; text: string } {
  switch (l.dueState) {
    case "NONE": return { tone: "warn", text: "This guard has not been reviewed yet." }
    case "FOLLOW_UP_DUE": return { tone: "warn", text: "The follow-up date set at the last review has arrived." }
    case "OVERDUE": return { tone: "warn", text: `Last reviewed ${l.daysSinceLast} days ago. A review is due at least every ${l.intervalDays} days.` }
    default: return { tone: "ok", text: `Last reviewed ${l.daysSinceLast} day${l.daysSinceLast === 1 ? "" : "s"} ago.` }
  }
}

export interface ReviewForm {
  periodFrom: string; periodTo: string; reviewDate: string; overall: string
  scores: Record<string, number>; strengths: string; improvements: string; trainingNeeds: string; actionsAgreed: string; followUpDate: string
}
export const SCORE_KEYS = ["punctuality", "professionalism", "appearance", "communication", "alertness", "incidentHandling"] as const

/** Null when the review can be sent, otherwise what is wrong. Mirrors the server's checks. */
export function reviewFormError(f: ReviewForm, today: string): string | null {
  if (!f.periodFrom || !f.periodTo) return "Enter the period the review covers"
  if (f.periodTo < f.periodFrom) return "The period cannot end before it starts"
  if (!f.reviewDate) return "Enter the review date"
  if (f.reviewDate > today || f.periodTo > today) return "A review cannot be dated in the future"
  if (f.reviewDate < f.periodTo) return "The review date cannot be before the end of the period"
  if (!f.overall) return "Choose the overall assessment"
  for (const k of SCORE_KEYS) if (!f.scores[k]) return "Score every area from 1 to 5"
  if (!f.strengths.trim() && !f.improvements.trim()) return "Say what went well or what needs to improve"
  if (f.followUpDate && f.followUpDate < f.reviewDate) return "The follow-up date cannot be before the review"
  return null
}

/** A sensible starting point: the 90 days up to yesterday, reviewed today. */
export function defaultReviewForm(today: string): ReviewForm {
  const d = (n: number) => { const x = new Date(today + "T00:00:00Z"); x.setUTCDate(x.getUTCDate() + n); return x.toISOString().slice(0, 10) }
  return { periodFrom: d(-90), periodTo: today, reviewDate: today, overall: "", scores: {}, strengths: "", improvements: "", trainingNeeds: "", actionsAgreed: "", followUpDate: "" }
}
