// Pure helpers for the performance screens. The server decides who may see and do what, and offers only what is allowed; this words things,
// colours them and catches obvious mistakes before a request is sent. It mirrors the server's checks only to save a round trip.
import { todaySast } from "./er.logic"

export interface PerfRights { read: boolean; manage: boolean }

export function perfRights(permissions: string[]): PerfRights {
  const has = (p: string) => permissions.includes(p)
  return { read: has("HR_PERF_READ") || has("HR_PERF_MANAGE"), manage: has("HR_PERF_MANAGE") }
}

export const RATING_LABELS = ["Unsatisfactory", "Needs improvement", "Meets expectations", "Exceeds expectations", "Outstanding"]
export const ratingText = (r: number | null | undefined) => (r && r >= 1 && r <= 5 ? `${r} – ${RATING_LABELS[r - 1]}` : "–")

export const REVIEW_STATUS: Record<string, string> = {
  AWAITING_SELF: "Waiting for self-review", AWAITING_MANAGER: "Waiting for manager", SUBMITTED: "Waiting for release", SHARED: "Released",
  ACKNOWLEDGED: "Acknowledged", EXCLUDED: "Left out",
}
export const CYCLE_STATUS: Record<string, string> = { DRAFT: "Draft", OPEN: "Open", CLOSED: "Closed" }
export const GOAL_STATUS: Record<string, string> = {
  PROPOSED: "Proposed", ACTIVE: "Under way", ACHIEVED: "Achieved", PARTLY_ACHIEVED: "Partly achieved", NOT_ACHIEVED: "Not achieved", CANCELLED: "Cancelled",
}
export const PLAN_STATUS: Record<string, string> = { DRAFT: "Draft", ACTIVE: "Running", MET: "Met", NOT_MET: "Not met", CANCELLED: "Cancelled" }
export const CHECKIN_LABEL: Record<string, string> = { ON_TRACK: "On track", AT_RISK: "At risk", OFF_TRACK: "Off track" }
export const RELATIONSHIPS: Record<string, string> = { PEER: "Peer", DIRECT_REPORT: "Reports to them", OTHER: "Other" }
export const GOAL_OUTCOMES = ["ACHIEVED", "PARTLY_ACHIEVED", "NOT_ACHIEVED"]

const GOOD = ["var(--hf-success-text-strong)", "var(--hf-success-soft-strong)"] as const
const WARN = ["var(--hf-warning-text-strong)", "var(--hf-warning-soft)"] as const
const INFO = ["var(--hf-info-text)", "var(--hf-info-soft)"] as const
const BAD = ["var(--hf-danger-text)", "var(--hf-danger-soft)"] as const
const MUTED = ["var(--hf-text-muted)", "var(--hf-surface-sunken)"] as const

/** [text, background] for a status chip. */
export function reviewColors(status: string): readonly [string, string] {
  switch (status) {
    case "AWAITING_SELF":
    case "AWAITING_MANAGER": return WARN
    case "SUBMITTED": return INFO
    case "SHARED":
    case "ACKNOWLEDGED": return GOOD
    default: return MUTED
  }
}
export function goalColors(status: string): readonly [string, string] {
  switch (status) {
    case "PROPOSED": return WARN
    case "ACTIVE": return INFO
    case "ACHIEVED": return GOOD
    case "PARTLY_ACHIEVED": return WARN
    case "NOT_ACHIEVED": return BAD
    default: return MUTED
  }
}
export function planColors(status: string): readonly [string, string] {
  switch (status) {
    case "ACTIVE": return INFO
    case "MET": return GOOD
    case "NOT_MET": return BAD
    case "DRAFT": return WARN
    default: return MUTED
  }
}
export function checkinColors(progress: string | null | undefined): readonly [string, string] {
  if (progress === "ON_TRACK") return GOOD
  if (progress === "AT_RISK") return WARN
  if (progress === "OFF_TRACK") return BAD
  return MUTED
}

const short = (v: string | null | undefined, min: number, max: number, what: string): string | null => {
  const t = (v ?? "").trim()
  if (!t) return `${what} is required.`
  if (t.length < min) return `${what} is too short (at least ${min} characters).`
  if (t.length > max) return `${what} is too long (${max} characters at most).`
  return null
}

const validRating = (r: number | null | undefined) => Number.isInteger(r) && (r as number) >= 1 && (r as number) <= 5

export function selfProblem(rating: number | null, comment: string): string | null {
  if (!validRating(rating)) return "Choose your rating from 1 to 5."
  return comment.length > 4000 ? "Your comments are too long (4000 characters at most)." : null
}

export function managerProblem(rating: number | null, comment: string): string | null {
  if (!validRating(rating)) return "Choose the rating from 1 to 5."
  const c = short(comment, 3, 4000, "Your comments")
  if (c) return c
  if ((rating === 1 || rating === 2 || rating === 5) && comment.trim().length < 20) return `A rating of ${rating} needs the reasons in more detail (at least 20 characters).`
  return null
}

export function calibrationProblem(rating: number | null, reason: string, current: number | null): string | null {
  if (!validRating(rating)) return "Choose the final rating from 1 to 5."
  if (rating === current) return "That is already the final rating."
  return short(reason, 10, 1000, "The reason for changing the rating")
}

export const reasonProblem = (what: string, v: string, min = 3, max = 500) => short(v, min, max, what)

export interface CycleForm { name: string; periodStart: string; periodEnd: string; selfDue: string; managerDue: string; include360: boolean }

export function cycleProblem(f: CycleForm): string | null {
  const n = short(f.name, 3, 120, "The cycle name")
  if (n) return n
  if (!f.periodStart || !f.periodEnd) return "Give the start and end of the period being reviewed."
  if (f.periodEnd < f.periodStart) return "The period cannot end before it starts."
  if (!f.selfDue || !f.managerDue) return "Give the dates by which the self-review and the manager review are due."
  if (f.selfDue < f.periodStart) return "The self-review cannot be due before the period starts."
  if (f.managerDue < f.selfDue) return "The manager review cannot be due before the self-review."
  return null
}

export interface GoalForm { title: string; description: string; measure: string; dueDate: string }

export function goalProblem(f: GoalForm, isNew: boolean, now: Date = new Date()): string | null {
  const t = short(f.title, 3, 200, "The goal")
  if (t) return t
  if (f.description.length > 4000) return "The description is too long (4000 characters at most)."
  if (f.measure.length > 500) return "How success is measured is too long (500 characters at most)."
  if (!f.dueDate) return "Give the date the goal is due."
  if (isNew && f.dueDate < todaySast(now)) return "A new goal cannot be due in the past."
  return null
}

export function progressProblem(progress: string): string | null {
  const n = Number(progress)
  return progress.trim() !== "" && Number.isInteger(n) && n >= 0 && n <= 100 ? null : "Progress is a whole number from 0 to 100."
}

export function goalCloseProblem(outcome: string, note: string): string | null {
  if (!GOAL_OUTCOMES.includes(outcome)) return "Choose how the goal ended."
  return outcome === "ACHIEVED" ? null : short(note, 5, 2000, "What happened")
}

export function feedbackProblem(rating: number | null, strengths: string, improvements: string): string | null {
  if (!validRating(rating)) return "Give an overall rating from 1 to 5."
  if (strengths.trim().length < 10 && improvements.trim().length < 10) return "Say something specific: what the person does well, or what could be better (at least 10 characters)."
  return strengths.length > 2000 || improvements.length > 2000 ? "Each comment is limited to 2000 characters." : null
}

export interface PlanForm { title: string; shortfall: string; standard: string; support: string; startDate: string; endDate: string }

export function planProblem(f: PlanForm): string | null {
  const t = short(f.title, 3, 200, "The plan's title")
  if (t) return t
  const s = short(f.shortfall, 10, 4000, "What the shortfall is")
  if (s) return s
  const d = short(f.standard, 10, 4000, "The standard required")
  if (d) return d
  const p = short(f.support, 10, 4000, "The support the company will give")
  if (p) return p
  if (!f.startDate || !f.endDate) return "Give the start and end of the plan."
  if (f.endDate < f.startDate) return "The plan cannot end before it starts."
  const days = (Date.parse(f.endDate) - Date.parse(f.startDate)) / 86400000
  return days > 365 ? "A plan longer than a year is too long. Set shorter steps." : null
}

export function startPlanProblem(on: string, now: Date = new Date()): string | null {
  if (!on) return "Give the date the employee was told about the plan."
  return on > todaySast(now) ? "The employee cannot have been told on a future date." : null
}

export function checkinProblem(on: string, start: string, progress: string, note: string, now: Date = new Date()): string | null {
  if (!on) return "Give the date of the check-in."
  if (on < start) return "A check-in cannot be before the plan starts."
  if (on > todaySast(now)) return "A check-in cannot be recorded before it happens."
  if (!(progress in CHECKIN_LABEL)) return "Say whether the employee is on track, at risk or off track."
  return short(note, 10, 4000, "What was discussed")
}

export function extendProblem(newEnd: string, oldEnd: string, reason: string): string | null {
  if (!newEnd || newEnd <= oldEnd) return `The new end date must be later than ${oldEnd}.`
  return short(reason, 5, 1000, "The reason for giving more time")
}

export function closePlanProblem(outcome: string, note: string, end: string, checkins: number, now: Date = new Date()): string | null {
  if (outcome !== "MET" && outcome !== "NOT_MET") return "Choose whether the plan was met or not met."
  const n = short(note, 10, 2000, "The outcome")
  if (n) return n
  if (outcome === "NOT_MET") {
    if (todaySast(now) < end) return `The plan runs until ${end}. The employee must have all the time they were promised before it is ended as not met.`
    if (checkins < 1) return "Record at least one check-in before ending a plan as not met."
  }
  return null
}

/** How far through its time a plan is, as a whole percentage, for a progress bar. */
export function planElapsed(start: string, end: string, now: Date = new Date()): number {
  const s = Date.parse(start), e = Date.parse(end), t = Date.parse(todaySast(now))
  if (!(e > s)) return 100
  return Math.max(0, Math.min(100, Math.round(((t - s) / (e - s)) * 100)))
}
