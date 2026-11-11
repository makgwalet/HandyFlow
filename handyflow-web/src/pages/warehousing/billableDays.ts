// Pure helpers for the billable-day rule panel. The server checks everything again.

export interface DayRuleRow { id: string; clientId: string | null; clientName: string | null; dayRule: string; label: string; minimumBasis: string; minimumBasisLabel: string; effectiveFrom: string; note: string | null; basisSource?: string }
export interface HolidayRow { date: string; name: string }
export interface DayRuleView { rules: DayRuleRow[]; holidays: HolidayRow[]; options: { value: string; label: string }[]; minimumBases?: { value: string; label: string }[]; review?: DayRuleRow[] }

export const STANDARD_TEXT = "Standard: every calendar day bills, including the arrival and dispatch days, weekends and public holidays. A part day bills in full."

/** Null when the rule can be saved, else what is wrong. A rule cannot start in the past: those days are already billed. */
export function ruleProblem(dayRule: string, effectiveFrom: string, today: string): string | null {
  if (!dayRule) return "Choose a rule."
  if (!effectiveFrom) return "Choose the date the rule starts."
  if (effectiveFrom < today) return "A rule cannot start in the past, because those days have already been billed."
  return null
}

export const scopeLabel = (r: Pick<DayRuleRow, "clientId" | "clientName">) => (r.clientId ? r.clientName ?? "A client" : "Whole warehouse (default)")

/** Billable days in a stay (inclusive of both ends) under a rule, for the worked example. */
export function billableDaysInStay(from: string, to: string, rule: string, holidays: string[]): number {
  let n = 0
  const end = new Date(`${to}T12:00:00Z`)
  for (let d = new Date(`${from}T12:00:00Z`); d <= end; d = new Date(d.getTime() + 86400000)) {
    const dow = d.getUTCDay(), iso = d.toISOString().slice(0, 10)
    const weekend = dow === 0 || dow === 6
    if (rule === "ALL_DAYS" || (rule === "WEEKDAYS_ONLY" && !weekend) || (rule === "WEEKDAYS_NO_HOLIDAYS" && !weekend && !holidays.includes(iso))) n++
  }
  return n
}

/** True when a rule skips public holidays but no holiday is listed for the year, so it would silently skip nothing. */
export function holidaysMissing(rules: Pick<DayRuleRow, "dayRule">[], holidays: HolidayRow[], year: number): boolean {
  return rules.some(r => r.dayRule === "WEEKDAYS_NO_HOLIDAYS") && !holidays.some(h => h.date.startsWith(`${year}-`))
}

/** What the minimum-charge choice means when "default" is picked: billable days for a weekday rule, calendar days for every-day billing. */
export const defaultBasisFor = (dayRule: string) => (dayRule === "ALL_DAYS" ? "CALENDAR_DAYS" : "BILLABLE_DAYS")

export const DEFAULT_BASIS_TEXT = "Default for the rule: billable days for a weekday rule, calendar days for every-day billing"

/** The line shown for an older rule that still pro-rates its minimum by calendar days. */
export const reviewText = (r: Pick<DayRuleRow, "clientId" | "clientName" | "label" | "effectiveFrom">) =>
  `${scopeLabel(r)}: ${r.label}, from ${r.effectiveFrom}. Saved before the weekday default existed, so its minimum is still pro-rated by calendar days.`
