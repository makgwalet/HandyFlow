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
  RESERVED: "Held for request", RELEASED: "Hold released", TAKEN: "Taken", REVERSAL: "Reversed", EXPIRY: "Forfeited under company policy",
}

export const kindLabel = (k: string) => KIND_LABEL[k] ?? k

/** Signed days for display: +2.0 / −1.5. */
export function signedDays(n: number | string): string {
  const v = Number(n)
  return `${v > 0 ? "+" : v < 0 ? "−" : ""}${Math.abs(v).toFixed(1)}`
}

export const todayIso = () => iso(new Date())

/** Whether to offer the carry-over policy form. The server decides again. */
export const canSetPolicy = (permissions: string[]) => permissions.includes("HR_MANAGE")

export const MAX_CAP = 100

export const MIN_EXPIRY = 6
export const MAX_EXPIRY = 24

/** Null when the policy may be sent. An empty cap and an empty expiry together mean "no policy". Messages match the server's. */
export function policyProblem(cap: string, effectiveYear: string, agreement: string, thisYear: number, expiry = ""): string | null {
  if (cap.trim() === "" && expiry.trim() === "") return null
  if (cap.trim() !== "") {
    const n = Number(cap)
    if (!Number.isFinite(n)) return "Enter the most days that may be carried over."
    if (n < 0) return "The most that can be carried over cannot be negative."
    if (n > MAX_CAP) return `The most that can be carried over cannot be more than ${MAX_CAP} days.`
    if (!/^\d+(\.\d{1,2})?$/.test(cap.trim())) return "Use at most two decimals for the days."
  }
  if (expiry.trim() !== "") {
    const m = Number(expiry)
    if (!Number.isInteger(m) || m < MIN_EXPIRY || m > MAX_EXPIRY)
      return `Carried leave can expire after ${MIN_EXPIRY} to ${MAX_EXPIRY} months. Employees have six months after a leave cycle to take their leave.`
  }
  const y = Number(effectiveYear)
  if (!Number.isInteger(y)) return "Say from which year the limit applies."
  if (y <= thisYear) return `The limit can only start from next year (${thisYear + 1}) or later.`
  if (y > thisYear + 5) return "That is too far ahead."
  if (!agreement.trim()) return "Leave can only be forfeited by written agreement. Say which agreement or policy document allows it."
  if (agreement.trim().length > 200) return "The agreement reference is too long (200 characters at most)."
  return null
}

/** The body sent to the server. Both empty removes the policy. */
export function policyBody(cap: string, expiry: string, effectiveYear: string, agreement: string) {
  if (cap.trim() === "" && expiry.trim() === "") return { carryOverCapDays: null, carryExpiryMonths: null, effectiveYear: null, agreementRef: null }
  return {
    carryOverCapDays: cap.trim() === "" ? null : Number(cap), carryExpiryMonths: expiry.trim() === "" ? null : Number(expiry),
    effectiveYear: Number(effectiveYear), agreementRef: agreement.trim(),
  }
}

/** One plain sentence on what the policy does. */
export function policySummary(p: { carryOverCapDays: number | null; carryExpiryMonths?: number | null; effectiveYear: number | null; agreementRef: string | null }): string {
  const parts: string[] = []
  if (p.carryOverCapDays != null) parts.push(`At most ${p.carryOverCapDays} days are carried over from the end of ${(p.effectiveYear ?? 0) - 1} onward.`)
  if (p.carryExpiryMonths != null) parts.push(`Leave carried into ${p.effectiveYear} or later that is not used within ${p.carryExpiryMonths} months is forfeited.`)
  if (parts.length === 0) return "No limit: all unused annual leave is carried over."
  return `${parts.join(" ")} Agreement: ${p.agreementRef ?? "–"}.`
}
