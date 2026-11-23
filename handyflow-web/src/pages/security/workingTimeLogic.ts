// Working-time policies: form rules and wording. The server enforces every rule again; this only avoids pointless clicks.
import { addDays, todaySast } from "./weeklyHoursLogic"

export interface Policy {
  id?: string; category: string; effectiveFrom: string
  warnAt: number; ordinaryWeekly: number; maxOvertimeWeekly: number; maxDaily: number; maxDailyOvertime: number; minRestHours: number
  legalBasis: string | null
}
export interface Draft {
  category: string; effectiveFrom: string
  warnAt: string; ordinaryWeekly: string; maxOvertimeWeekly: string; maxDaily: string; maxDailyOvertime: string; minRestHours: string
  legalBasis: string
}

export const DEFAULT_CATEGORY = "DEFAULT"
/** The general BCEA figures. Going beyond any of them needs a written legal basis. */
export const GENERAL = { ordinaryWeekly: 45, maxOvertimeWeekly: 10, maxDaily: 12, maxDailyOvertime: 3, minRestHours: 12 }
export const BASIS_MIN = 10
export const BASIS_MAX = 500

export function emptyDraft(today: string = todaySast()): Draft {
  return { category: DEFAULT_CATEGORY, effectiveFrom: today, warnAt: "40", ordinaryWeekly: "45", maxOvertimeWeekly: "10", maxDaily: "12", maxDailyOvertime: "3", minRestHours: "12", legalBasis: "" }
}

export function normaliseCategory(raw: string): string { return raw.trim().toUpperCase().replace(/ /g, "_") }
export function validCategory(raw: string): boolean { return /^[A-Z0-9_]{1,40}$/.test(normaliseCategory(raw)) }

/** A plain decimal number, or null. */
export function num(s: string): number | null {
  const t = s.trim()
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(t)) return null
  return Number(t)
}

export function ceilingOf(d: Draft): number | null {
  const o = num(d.ordinaryWeekly), t = num(d.maxOvertimeWeekly)
  return o === null || t === null ? null : Math.round((o + t) * 100) / 100
}

export function exceedsGeneral(d: Draft): boolean {
  const o = num(d.ordinaryWeekly), t = num(d.maxOvertimeWeekly), day = num(d.maxDaily), dot = num(d.maxDailyOvertime), r = num(d.minRestHours)
  return (o !== null && o > GENERAL.ordinaryWeekly) || (t !== null && t > GENERAL.maxOvertimeWeekly) || (day !== null && day > GENERAL.maxDaily)
    || (dot !== null && dot > GENERAL.maxDailyOvertime) || (r !== null && r < GENERAL.minRestHours)
}

/** The first reason the form cannot be saved, or null. */
export function draftError(d: Draft, today: string = todaySast()): string | null {
  if (!validCategory(d.category)) return "Category must be 1 to 40 letters, digits or underscores."
  if (!/^\d{4}-\d{2}-\d{2}$/.test(d.effectiveFrom)) return "Choose the date it starts."
  if (d.effectiveFrom < today) return "A policy cannot start in the past."
  const warn = num(d.warnAt), o = num(d.ordinaryWeekly), t = num(d.maxOvertimeWeekly), day = num(d.maxDaily), dot = num(d.maxDailyOvertime), r = num(d.minRestHours)
  if ([warn, o, t, day, dot, r].some(v => v === null)) return "Enter every number of hours (up to 2 decimals)."
  if (o! <= 0 || o! > 84) return "Ordinary weekly hours must be above 0 and at most 84."
  if (t! > 40) return "Weekly overtime must be between 0 and 40."
  if (warn! <= 0 || warn! > o!) return "The warning level must be above 0 and not above the ordinary weekly hours."
  if (day! <= 0 || day! > 24) return "Daily hours must be above 0 and at most 24."
  if (dot! > day!) return "Daily overtime cannot be more than the daily hours."
  if (r! > 24) return "Rest between shifts must be between 0 and 24 hours."
  const basis = d.legalBasis.trim()
  if (basis.length > BASIS_MAX) return `The legal basis is too long (${BASIS_MAX} characters at most).`
  if (exceedsGeneral(d) && basis.length < BASIS_MIN) return "These limits go beyond the general BCEA figures. Record the legal basis (agreement or determination) that allows it."
  return null
}

export function toRequest(d: Draft) {
  const basis = d.legalBasis.trim()
  return {
    category: normaliseCategory(d.category), effectiveFrom: d.effectiveFrom,
    warnAt: num(d.warnAt), ordinaryWeekly: num(d.ordinaryWeekly), maxOvertimeWeekly: num(d.maxOvertimeWeekly),
    maxDaily: num(d.maxDaily), maxDailyOvertime: num(d.maxDailyOvertime), minRestHours: num(d.minRestHours),
    legalBasis: basis === "" ? null : basis,
  }
}

export function validPolicies(data: unknown): Policy[] | null {
  if (!Array.isArray(data)) return null
  const ok = data.every(p => p && typeof p.category === "string" && typeof p.effectiveFrom === "string" && typeof p.ordinaryWeekly === "number"
    && typeof p.maxOvertimeWeekly === "number" && typeof p.maxDaily === "number" && typeof p.minRestHours === "number")
  return ok ? (data as Policy[]) : null
}

export function categoriesOf(policies: Policy[]): string[] {
  return Array.from(new Set([DEFAULT_CATEGORY, ...policies.map(p => p.category)])).sort((a, b) => (a === DEFAULT_CATEGORY ? -1 : b === DEFAULT_CATEGORY ? 1 : a.localeCompare(b)))
}

export type PolicyStatus = "In force" | "Starts later" | "Replaced"
/** In force: the latest one of its category that has started. A policy dated mid-week first counts from the following week's Monday, so "In force" here is the date, not the week. */
export function statusOf(p: Policy, all: Policy[], today: string = todaySast()): PolicyStatus {
  if (p.effectiveFrom > today) return "Starts later"
  const latest = all.filter(q => q.category === p.category && q.effectiveFrom <= today).map(q => q.effectiveFrom).sort().pop()
  return latest === p.effectiveFrom ? "In force" : "Replaced"
}

export function summaryOf(p: Policy): string {
  const ceiling = Math.round((p.ordinaryWeekly + p.maxOvertimeWeekly) * 100) / 100
  return `${p.ordinaryWeekly} h ordinary + ${p.maxOvertimeWeekly} h overtime (ceiling ${ceiling} h), ${p.maxDaily} h a day, ${p.minRestHours} h rest`
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
/** "1 Dec 2026". Written out so it reads the same in every browser locale. */
export function dateText(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number)
  return `${d} ${MONTHS[m - 1]} ${y}`
}

/** The Monday after the date: when a mid-week start first applies. */
export function firstFullWeek(effectiveFrom: string): string {
  const [y, m, d] = effectiveFrom.split("-").map(Number)
  const dow = (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7
  return dow === 0 ? effectiveFrom : addDays(effectiveFrom, 7 - dow)
}

export function errorText(e: unknown, fallback: string): string {
  const m = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  return typeof m === "string" && m.length > 0 && m.length < 300 ? m : fallback
}
