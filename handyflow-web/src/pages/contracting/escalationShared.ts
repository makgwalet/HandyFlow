// Pure helpers for approval escalation (reminders, escalation, time away, response times). Backend: /api/v1/contracts/approval-escalation.

export type DayCode = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export const DAYS: { code: DayCode; short: string }[] = [
  { code: 'MONDAY', short: 'Mon' }, { code: 'TUESDAY', short: 'Tue' }, { code: 'WEDNESDAY', short: 'Wed' }, { code: 'THURSDAY', short: 'Thu' },
  { code: 'FRIDAY', short: 'Fri' }, { code: 'SATURDAY', short: 'Sat' }, { code: 'SUNDAY', short: 'Sun' },
]

export interface EscalationSettings {
  enabled: boolean
  enabledSince: string | null
  reminder1Hours: number
  reminder2Hours: number
  escalateHours: number
  clockMode: 'BUSINESS_HOURS' | 'CALENDAR'
  workStart: string
  workEnd: string
  workDays: DayCode[]
  skipPublicHolidays: boolean
  escalationUserIds: string[]
}

export interface ClosureDay { id: string; day: string; reason: string | null }
export interface Absence { id: string; userId: string; from: string; to: string; delegateUserId: string | null; reason: string | null }

export interface SlaGroup { key: string; decided: number; averageMinutes: number | null; escalated: number }
export interface SlaOverdue { id: string; action: string; waitedMinutes: number; escalationLevel: number }
export interface SlaResult {
  requested: number; decided: number; waiting: number; cancelledOrOutOfDate: number
  averageMinutes: number | null; medianMinutes: number | null; ninetiethPercentileMinutes: number | null; decidedWithinTargetPercent: number | null
  reminded: number; escalated: number; soleUserExceptions: number; waitingPastTarget: number
  byAction: SlaGroup[]; byApprover: SlaGroup[]; overdue: SlaOverdue[]
}
export interface SlaReport { from: string; to: string; targetHours: number; workingClock: boolean; result: SlaResult; approvers: { userId: string; name: string }[] }

/** What the card says about how far a waiting request has been chased. */
export function stageLabel(stage: number | undefined): string | null {
  if (stage === 1) return 'Reminded'
  if (stage === 2) return 'Reminded twice'
  if (stage != null && stage >= 3) return 'Escalated'
  return null
}

/** Hours on the working clock, in the working days they come to. 72 hours of a 9-hour day is 8 working days, which surprises people. */
export function workingHoursPerDay(start: string, end: string): number {
  const mins = (t: string) => { const [h, m] = t.split(':').map(Number); return (h || 0) * 60 + (m || 0) }
  return Math.max(0, (mins(end) - mins(start)) / 60)
}

export function hoursInWords(hours: number, s: Pick<EscalationSettings, 'clockMode' | 'workStart' | 'workEnd'>): string {
  if (s.clockMode !== 'BUSINESS_HOURS') {
    const d = hours / 24
    return Number.isInteger(d) ? `${hours} hours (${d} ${d === 1 ? 'day' : 'days'}, around the clock)` : `${hours} hours, around the clock`
  }
  const perDay = workingHoursPerDay(s.workStart, s.workEnd)
  if (perDay <= 0) return `${hours} working hours`
  const days = hours / perDay
  const shown = Number.isInteger(days) ? String(days) : days.toFixed(1)
  return `${hours} working hours (about ${shown} working ${days === 1 ? 'day' : 'days'})`
}

/** "2 h 30 min", "3 days 2 h" style, for a number of minutes on whichever clock the report used. */
export function minutesInWords(minutes: number | null | undefined): string {
  if (minutes == null) return '—'
  if (minutes < 1) return 'under a minute'
  const h = Math.floor(minutes / 60)
  const m = Math.round(minutes % 60)
  if (h === 0) return `${m} min`
  if (h < 48) return m ? `${h} h ${m} min` : `${h} h`
  const d = Math.floor(h / 24)
  const rest = h % 24
  return rest ? `${d} days ${rest} h` : `${d} days`
}

/** Returns the first reason the settings cannot be saved, or null. The server checks again. */
export function settingsProblem(s: EscalationSettings): string | null {
  const { reminder1Hours: a, reminder2Hours: b, escalateHours: c } = s
  if (![a, b, c].every(n => Number.isInteger(n) && n >= 1 && n <= 720)) return 'Hours must be whole numbers from 1 to 720.'
  if (!(a < b && b < c)) return 'The first reminder must come before the second, and the second before escalation.'
  if (s.clockMode === 'BUSINESS_HOURS') {
    if (!s.workStart || !s.workEnd || s.workStart >= s.workEnd) return 'Working hours must start before they end.'
    if (s.workDays.length === 0) return 'Choose at least one working day.'
  }
  return null
}

export const isoToday = (): string => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Johannesburg' })

export function daysBetweenInclusive(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1
}

/** Away-form check. */
export function absenceProblem(from: string, to: string, delegate: string, userId: string): string | null {
  if (!from || !to) return 'Choose the first and last day away.'
  if (to < from) return 'The last day away cannot be before the first.'
  if (to < isoToday()) return 'The last day away is already past.'
  if (daysBetweenInclusive(from, to) > 366) return 'Time away can be at most 366 days at a time.'
  if (delegate && delegate === userId) return 'Someone cannot cover for themselves.'
  return null
}
