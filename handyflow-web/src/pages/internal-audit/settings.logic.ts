// What the Audit Settings form checks and shows. The same limits the server enforces, so mistakes are caught before saving.

import type { AuditSettingValues } from "../../api/auditSettings.api"

export const SCORE_MIN = 5
export const SCORE_MAX = 25

export function problems(v: AuditSettingValues): string[] {
  const p: string[] = []
  const ints = Object.values(v).every(n => Number.isInteger(n))
  if (!ints) return ["Enter whole numbers in every box"]
  if (v.lowMax < SCORE_MIN) p.push(`Low must end at ${SCORE_MIN} or higher`)
  if (v.mediumMax <= v.lowMax) p.push("Medium must end above where Low ends")
  if (v.highMax <= v.mediumMax) p.push("High must end above where Medium ends")
  if (v.highMax > SCORE_MAX - 1) p.push(`High must end at ${SCORE_MAX - 1} or lower, so Critical has at least one score`)
  if (v.repeatEveryDays < 1 || v.repeatEveryDays > 30) p.push("Repeat reminders every 1 to 30 days")
  if (v.escalateAfterDays < 2 || v.escalateAfterDays > 90) p.push("Escalate after 2 to 90 days")
  if (v.firstReviewReminderDay < 1 || v.firstReviewReminderDay > 30) p.push("Remind about review comments after 1 to 30 days")
  return p
}

/** The four bands the numbers make, for a preview while typing. Only meaningful when `problems` is empty. */
export function bandsFrom(v: AuditSettingValues): { level: string; from: number; to: number }[] {
  return [
    { level: "LOW", from: SCORE_MIN, to: v.lowMax }, { level: "MEDIUM", from: v.lowMax + 1, to: v.mediumMax },
    { level: "HIGH", from: v.mediumMax + 1, to: v.highMax }, { level: "CRITICAL", from: v.highMax + 1, to: SCORE_MAX },
  ]
}

export const sameValues = (a: AuditSettingValues, b: AuditSettingValues) => (Object.keys(a) as (keyof AuditSettingValues)[]).every(k => a[k] === b[k])

/** Text boxes give strings; an empty or non-numeric box becomes NaN, which `problems` rejects. */
export const toNumber = (s: string) => (s.trim() === "" ? NaN : Number(s))
