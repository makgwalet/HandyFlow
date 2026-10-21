// Pure helpers for the leave screen. The server counts, decides and refuses; this only avoids showing a button whose
// click would be refused and catches typing mistakes early. Messages match the server's.

export interface LeaveRights {
  /** Approve, reject and cancel. */
  decide: boolean
  /** Add or take off days with a reason; also cancel leave that has started. */
  adjust: boolean
}

export function leaveRights(permissions: string[]): LeaveRights {
  const has = (...p: string[]) => p.some(x => permissions.includes(x))
  return { decide: has("LEAVE_APPROVE", "HR_MANAGE"), adjust: has("LEAVE_ADJUST") }
}

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
const parse = (s: string) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d) }

/** Weekdays in the range, less the given public holidays (ISO dates). Half a day counts as 0.5. */
export function workingDays(start: string, end: string, halfDay = false, holidays: string[] = []): number {
  if (!start || !end) return 0
  const a = parse(start), b = parse(end)
  if (b < a) return 0
  let n = 0
  for (const cur = new Date(a); cur <= b; cur.setDate(cur.getDate() + 1)) {
    const wd = cur.getDay()
    if (wd > 0 && wd < 6 && !holidays.includes(iso(cur))) n++
  }
  return halfDay && n > 0 ? 0.5 : n
}

/** Sick leave over two working days is expected to come with a medical certificate. */
export function certificateExpected(leaveType: string, days: number): boolean {
  return leaveType === "SICK" && days > 2
}

export interface LeaveForm { leaveType: string; startDate: string; endDate: string; halfDay: boolean }

/** First problem with a new request, or null. */
export function requestProblem(f: LeaveForm, days: number): string | null {
  if (!f.startDate || !f.endDate) return "Enter the first and last day of the leave."
  if (f.endDate < f.startDate) return "The last day is before the first day."
  if (f.halfDay && f.startDate !== f.endDate) return "A half day can only be one day."
  if (days === 0) return "There is no working day in that period (weekends and public holidays are not leave days)."
  return null
}

export function reasonProblem(reason: string, what: string): string | null {
  return reason.trim() ? null : `Give a reason for ${what}.`
}

export function adjustmentProblem(days: string, reason: string, date: string, today: string): string | null {
  const n = Number(days)
  if (!days.trim() || !Number.isFinite(n) || n === 0) return "Enter the number of days to add or take off."
  if (Math.abs(n) > 365) return "That is too many days for one adjustment."
  if (!reason.trim()) return "Give the reason for the adjustment."
  if (!date) return "Choose the date the adjustment belongs to."
  if (date > today) return "An adjustment cannot be dated in the future."
  return null
}

export interface LeaveRow { status: string; startDate: string }

/** Whether to offer Cancel. The server has the final say; leave that has started needs the adjust right. */
export function canCancel(row: LeaveRow, rights: LeaveRights, today: string): boolean {
  if (row.status === "PENDING") return rights.decide
  if (row.status === "APPROVED") return row.startDate > today ? rights.decide || rights.adjust : rights.adjust
  return false
}

export const KIND_LABEL: Record<string, string> = {
  OPENING: "Opening balance", ACCRUAL: "Earned", CARRY_FORWARD: "Carried forward", ADJUSTMENT: "Adjustment",
  RESERVED: "Held for request", RELEASED: "Hold released", TAKEN: "Taken", REVERSAL: "Reversed",
}

export const kindLabel = (k: string) => KIND_LABEL[k] ?? k

/** Signed days for display: +2.0 / −1.5. */
export function signedDays(n: number | string): string {
  const v = Number(n)
  return `${v > 0 ? "+" : v < 0 ? "−" : ""}${Math.abs(v).toFixed(1)}`
}

export const todayIso = () => iso(new Date())
