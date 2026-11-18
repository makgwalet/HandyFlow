// Pure helpers for the Fuel operations policy screens: what the server accepts, in words, so a bad value is caught before it is sent.
// The server checks everything again.

export const unwrap = (r: any) => r?.data?.data ?? r?.data

export interface ArrivalPolicy { freshHours: number; defaultFreshHours: number; enforced: boolean }
export interface PodPolicy { enforced: boolean; allowedExceptions: string[] }
export interface OverduePolicy { graceMinutes: number; escalationMinutes: number }
export interface OfflinePolicy { maxAgeHours: number; clockSkewMinutes: number }

export const RELEASE_REASONS: { code: string; label: string }[] = [
  { code: "DRIVER_DELAYED", label: "The driver was delayed" },
  { code: "SITE_QUEUE", label: "A queue at the site" },
  { code: "OFFLINE_AT_SITE", label: "No signal at the site" },
  { code: "RECHECK_NOT_POSSIBLE", label: "A new check could not be done" },
  { code: "OTHER", label: "Another reason (explain)" },
]

export const POD_EXCEPTIONS: { code: string; label: string; hint: string }[] = [
  { code: "UNATTENDED_SITE", label: "Unattended site", hint: "The site is never staffed. It must be marked as such under the customer's site." },
  { code: "AUTOMATED_RECEIVING", label: "Automated receiving", hint: "The site receives automatically. It must be marked as such under the customer's site." },
  { code: "AFTER_HOURS_APPROVED", label: "Approved after-hours delivery", hint: "A person with approval rights approves it first, for one delivery." },
  { code: "CONTACTLESS_AUTHORISED", label: "Authorised contactless delivery", hint: "A person with approval rights approves it first, for one delivery." },
]
export const podLabel = (code: string) => POD_EXCEPTIONS.find(e => e.code === code)?.label ?? code

export const PUSH_TYPES: { type: string; label: string }[] = [
  { type: "OVERDUE_DELIVERY", label: "Overdue delivery" },
  { type: "DIP_VARIANCE", label: "Dip variance" },
  { type: "SHIFT_OUTSIDE_TOLERANCE", label: "Shift closed outside tolerance" },
  { type: "ARRIVAL_CHECK_BLOCKED", label: "Driver arrival check blocked" },
]
export const pushLabel = (t: string) => PUSH_TYPES.find(p => p.type === t)?.label ?? t

export const CONFLICT_LABEL: Record<string, string> = {
  SHIFT_OPEN: "Open shift", SHIFT_CLOSE: "Close shift and cash-up", STAY_BOOK: "Book stay", STAY_CHECK_IN: "Check truck in", STAY_CHECK_OUT: "Check truck out",
}

const whole = (v: string) => /^\d+$/.test(v.trim())

export function validateArrival(freshHours: string): string | null {
  if (!whole(freshHours)) return "Enter the number of hours as a whole number."
  const n = Number(freshHours)
  return n < 1 || n > 72 ? "A check can stay fresh for between 1 and 72 hours." : null
}

export function validateOverdue(grace: string, escalation: string): string | null {
  for (const [v, what] of [[grace, "grace"], [escalation, "escalation delay"]] as const) {
    if (!whole(v)) return `Enter the ${what} in whole minutes.`
    const n = Number(v)
    if (n < 15 || n > 1440) return `The ${what} must be between 15 and 1440 minutes.`
  }
  return null
}

export function validateOffline(maxAge: string, skew: string): string | null {
  if (!whole(maxAge)) return "Enter the longest wait in whole hours."
  if (Number(maxAge) < 1 || Number(maxAge) > 336) return "The longest a queued write may wait must be between 1 and 336 hours."
  if (!whole(skew)) return "Enter the clock allowance in whole minutes."
  if (Number(skew) > 60) return "The clock allowance must be between 0 and 60 minutes."
  return null
}

export function validateRelease(reasonCode: string, explanation: string): string | null {
  if (!reasonCode) return "Choose a reason."
  if (explanation.trim().length < 10) return "Explain in at least 10 characters why this check may stand."
  if (explanation.length > 1000) return "The explanation is too long (1000 characters at most)."
  return null
}

export function validateApprove(type: string, reason: string): string | null {
  if (!type) return "Choose the kind of exception."
  if (reason.trim().length < 10) return "Give a reason of at least 10 characters."
  if (reason.length > 500) return "The reason is too long (500 characters at most)."
  return null
}

/** Whole hours or days for a long wait, so "72" reads as "3 days". */
export function hoursText(h: number): string { return h >= 48 && h % 24 === 0 ? `${h / 24} days` : `${h} hour${h === 1 ? "" : "s"}` }
