// Pure helpers for the attendance screens. The server works out what each day means; this only decides what to offer and how
// to word it. South Africa has no daylight saving, so a fixed +02:00 offset is exact.

export interface AttendanceRights {
  /** Manage schedules, correct anyone's time. */
  manage: boolean
  /** Approve or reject overtime. */
  decide: boolean
  /** See a register of people (everyone, or a manager's team). */
  register: boolean
  /** Correct time; the server limits managers to their own team. */
  correct: boolean
}

export function attendanceRights(permissions: string[]): AttendanceRights {
  const has = (...p: string[]) => p.some(x => permissions.includes(x))
  const manage = has("HR_MANAGE", "HR_ATTENDANCE_MANAGE")
  return {
    manage,
    decide: has("HR_MANAGE", "HR_OVERTIME_APPROVE"),
    register: manage || has("HR_READ", "HR_TEAM_READ"),
    correct: manage || has("HR_TEAM_READ"),
  }
}

export const STATUS_LABEL: Record<string, string> = {
  PRESENT: "Present", LATE: "Late", ABSENT: "Absent", ON_LEAVE: "On leave", PUBLIC_HOLIDAY: "Public holiday",
  REST_DAY: "Rest day", INCOMPLETE: "No clock-out", PENDING: "Today",
}

/** Colour tokens for a status chip: [text, background]. */
export function statusColors(status: string): [string, string] {
  switch (status) {
    case "PRESENT": return ["var(--hf-success-text-strong)", "var(--hf-success-soft-strong)"]
    case "LATE": return ["var(--hf-warning-text-strong)", "var(--hf-warning-soft)"]
    case "ABSENT":
    case "INCOMPLETE": return ["var(--hf-danger-text)", "var(--hf-danger-soft)"]
    default: return ["var(--hf-text-muted)", "var(--hf-surface-sunken)"]
  }
}

export const CLAIM_LABEL: Record<string, string> = { PENDING: "Waiting", APPROVED: "Approved", REJECTED: "Rejected", WITHDRAWN: "Withdrawn" }
export const KIND_LABEL: Record<string, string> = { OVERTIME: "Overtime (1.5×)", SUNDAY: "Sunday (2×)", PUBLIC_HOLIDAY: "Public holiday" }

/** 135 -> "2h 15m"; 0 -> "–". */
export function fmtMinutes(m: number): string {
  if (!m) return "–"
  const h = Math.floor(m / 60), r = m % 60
  return h === 0 ? `${r}m` : r === 0 ? `${h}h` : `${h}h ${String(r).padStart(2, "0")}m`
}

const SAST_MS = 2 * 3600_000

/** The South African date and time (HH:MM) of an instant. */
export function sast(iso: string): { date: string; time: string } {
  const s = new Date(Date.parse(iso) + SAST_MS).toISOString()
  return { date: s.slice(0, 10), time: s.slice(11, 16) }
}

export function fmtTime(iso: string | null): string { return iso ? sast(iso).time : "–" }

/** A South African date and time as an instant the server understands. */
export function toInstant(date: string, time: string): string { return `${date}T${time}:00+02:00` }

/** R1 234.50: a dot for cents and a space between thousands, the same on every machine. */
export function rand(n: number): string {
  const [whole, cents] = Math.abs(n).toFixed(2).split(".")
  return `${n < 0 ? "-" : ""}R${whole.replace(/\B(?=(\d{3})+(?!\d))/g, " ")}.${cents}`
}

export interface EntryForm { date: string; timeIn: string; timeOut: string; reason: string }

/** The first thing wrong with a form for adding or correcting an entry, or null. */
export function entryFormProblem(f: EntryForm, now = new Date()): string | null {
  if (!f.date) return "Enter the date."
  if (!f.timeIn) return "Enter the time in."
  if (!f.timeOut) return "Enter the time out."
  if (f.timeOut <= f.timeIn) return "The time out must be after the time in."
  if (Date.parse(toInstant(f.date, f.timeOut)) > now.getTime() + 300_000) return "That time is in the future."
  if (!f.reason.trim()) return "Give the reason for the change."
  if (f.reason.trim().length > 500) return "Keep the reason under 500 characters."
  return null
}

export function reasonProblem(reason: string, what: string): string | null {
  if (!reason.trim()) return `Give the reason ${what}.`
  if (reason.trim().length > 500) return "Keep the reason under 500 characters."
  return null
}

export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
}

/** The last `n` days ending today (inclusive). */
export function lastDays(n: number, today = new Date()): { from: string; to: string } {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (n - 1))
  return { from: isoDate(from), to: isoDate(today) }
}

/** The calendar month of a date as a period, ending today for the current month. */
export function monthPeriod(today = new Date()): { from: string; to: string } {
  return { from: isoDate(new Date(today.getFullYear(), today.getMonth(), 1)), to: isoDate(today) }
}

/** Longest period the server will build. */
export const MAX_DAYS = 62

export function periodProblem(from: string, to: string): string | null {
  if (!from || !to) return "Choose the dates."
  if (to < from) return "The end date is before the start date."
  const days = (Date.parse(to) - Date.parse(from)) / 86_400_000
  if (days > MAX_DAYS) return `Choose a period of ${MAX_DAYS} days or less.`
  return null
}

export interface DayLike {
  date: string; overtimeKind: string | null; overtimeMinutes: number; overtimeClaimStatus: string | null; status: string
}

/** Days in a timesheet that could have an overtime claim made for them. */
export function claimableDays(days: DayLike[], aboveThreshold: boolean, today: string): DayLike[] {
  if (aboveThreshold) return []
  return days.filter(d => d.overtimeMinutes > 0 && d.overtimeKind && d.overtimeKind !== "PUBLIC_HOLIDAY" && d.date < today
    && d.status !== "INCOMPLETE" && (!d.overtimeClaimStatus || d.overtimeClaimStatus === "WITHDRAWN" || d.overtimeClaimStatus === "REJECTED"))
}

export const WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const
export const WEEKDAY_LABEL: Record<string, string> = { MON: "Mon", TUE: "Tue", WED: "Wed", THU: "Thu", FRI: "Fri", SAT: "Sat", SUN: "Sun" }

export interface ScheduleForm { name: string; days: string[]; startTime: string; endTime: string; breakMinutes: string }

export function scheduleFormProblem(f: ScheduleForm): string | null {
  if (!f.name.trim()) return "Give the schedule a name."
  if (f.days.length === 0) return "Choose at least one working day."
  if (!f.startTime || !f.endTime) return "Enter the start and end times."
  if (f.endTime <= f.startTime) return "The end time must be after the start time. Shifts across midnight are not supported yet."
  const b = Number(f.breakMinutes)
  if (!Number.isInteger(b) || b < 0 || b > 180) return "The unpaid break must be between 0 and 180 minutes."
  return null
}
