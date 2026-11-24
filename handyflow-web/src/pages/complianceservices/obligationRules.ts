// Allowed values and plain-words descriptions for the obligation catalogue. The server enforces the rules.
import { ENTITY_TYPES, TURNOVER_BANDS } from "./profileRules"

export const FREQUENCIES: [string, string][] = [
  ["ONCE", "Once"], ["MONTHLY", "Monthly"], ["QUARTERLY", "Quarterly"], ["BIANNUAL", "Twice a year"], ["ANNUAL", "Yearly"],
  ["ON_EXPIRY", "When it expires"], ["ON_EVENT", "When something happens"],
]
export const RISK_LEVELS: [string, string][] = [["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"], ["CRITICAL", "Critical"]]
export const RESPONSIBLE_ROLES: [string, string][] = [["CONSULTANT", "Consultant"], ["REVIEWER", "Reviewer"], ["CLIENT", "Client"], ["EXTERNAL_PROVIDER", "External provider"]]
export const DUE_RULES: [string, string][] = [
  ["NONE", "No due date"], ["ANNUAL_FIXED", "Same date every year"], ["ANNUAL_AFTER_FYE", "Months after the financial year end"],
  ["MONTHLY_DAY", "A day of every month"], ["QUARTER_END_PLUS", "Days after each quarter end"], ["ANNIVERSARY", "Months after the registration anniversary"],
  ["AFTER_EVENT", "Days after an event"], ["BEFORE_EXPIRY", "Days before it expires"],
]
export const CONDITION_FIELDS: [string, string][] = [
  ["ENTITY_TYPE", "Entity type"], ["INDUSTRY", "Industry"], ["EMPLOYEE_COUNT", "Employees"], ["TURNOVER_BAND", "Turnover band"], ["VAT_REGISTERED", "VAT registered"],
]
export const STATUS_LABELS: Record<string, string> = { DRAFT: "Draft", IN_REVIEW: "In review", APPROVED: "Approved" }
export const MIN_EXCEPTION_REASON = 15
export const MIN_REVIEW_REASON = 10

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]

export type DueRule = { dueRuleType: string; dueMonth: number | null; dueDay: number | null; dueOffsetDays: number | null; dueOffsetMonths: number | null }

/** The due-date rule in plain words. */
export function describeDueRule(r: DueRule): string {
  switch (r.dueRuleType) {
    case "ANNUAL_FIXED": return r.dueMonth && r.dueDay ? `Every ${r.dueDay} ${MONTHS[r.dueMonth - 1]}` : "A fixed date each year"
    case "ANNUAL_AFTER_FYE": return `${r.dueOffsetMonths ?? "?"} months after the financial year end`
    case "MONTHLY_DAY": return `The ${r.dueDay ?? "?"}th of every month`
    case "QUARTER_END_PLUS": return `${r.dueOffsetDays ?? "?"} days after each quarter end`
    case "ANNIVERSARY": return `${r.dueOffsetMonths ?? "?"} months after the registration anniversary`
    case "AFTER_EVENT": return `${r.dueOffsetDays ?? "?"} days after the event`
    case "BEFORE_EXPIRY": return `${r.dueOffsetDays ?? "?"} days before it expires`
    default: return "No due date"
  }
}

/** What the rule still needs, or null. Mirrors the server's checks. */
export function dueRuleProblem(r: DueRule): string | null {
  const days = (n: number | null, max: number) => n != null && Number.isInteger(n) && n >= 0 && n <= max
  switch (r.dueRuleType) {
    case "ANNUAL_FIXED": {
      if (!r.dueMonth || r.dueMonth < 1 || r.dueMonth > 12) return "Choose a month"
      const len = new Date(2024, r.dueMonth, 0).getDate()
      return r.dueDay && r.dueDay >= 1 && r.dueDay <= len ? null : "That day does not exist in that month"
    }
    case "MONTHLY_DAY": return r.dueDay && r.dueDay >= 1 && r.dueDay <= 31 ? null : "Choose a day from 1 to 31"
    case "ANNUAL_AFTER_FYE":
    case "ANNIVERSARY": return days(r.dueOffsetMonths, 24) ? null : "Enter months from 0 to 24"
    case "QUARTER_END_PLUS":
    case "AFTER_EVENT":
    case "BEFORE_EXPIRY": return days(r.dueOffsetDays, 730) ? null : "Enter days from 0 to 730"
    default: return null
  }
}

export type Condition = { field: string; operator: string; value: string | null }

export function describeCondition(c: Condition): string {
  const label = (opts: [string, string][], v: string) => opts.find(o => o[0] === v)?.[1] ?? v
  switch (c.field) {
    case "ENTITY_TYPE": return `Entity type is ${(c.value ?? "").split(",").filter(Boolean).map(v => label(ENTITY_TYPES, v)).join(" or ")}`
    case "TURNOVER_BAND": return `Turnover is ${(c.value ?? "").split(",").filter(Boolean).map(v => label(TURNOVER_BANDS, v)).join(" or ")}`
    case "INDUSTRY": return `Industry is ${(c.value ?? "").split(",").filter(Boolean).join(" or ")}`
    case "EMPLOYEE_COUNT": return `${c.operator === "GTE" ? "At least" : "At most"} ${c.value} employees`
    case "VAT_REGISTERED": return c.operator === "IS_TRUE" ? "Is VAT registered" : "Is not VAT registered"
    default: return c.field
  }
}

/** The operator a field uses, so the screen does not offer a choice that would be refused. */
export function operatorsFor(field: string): string[] {
  if (field === "EMPLOYEE_COUNT") return ["GTE", "LTE"]
  if (field === "VAT_REGISTERED") return ["IS_TRUE", "IS_FALSE"]
  return ["IN"]
}

export function conditionProblem(c: Condition): string | null {
  if (c.field === "VAT_REGISTERED") return null
  if (!(c.value ?? "").trim()) return "Give a value"
  if (c.field === "EMPLOYEE_COUNT") { const n = Number(c.value); return Number.isInteger(n) && n >= 0 ? null : "Employees must be a whole number" }
  return null
}

/** Conditions that can never all be met. */
export function conditionsClash(cs: Condition[]): string | null {
  const nums = (op: string) => cs.filter(c => c.field === "EMPLOYEE_COUNT" && c.operator === op).map(c => Number(c.value))
  const min = Math.max(-Infinity, ...nums("GTE")), max = Math.min(Infinity, ...nums("LTE"))
  if (min > max) return "The employee limits cannot both be met"
  if (new Set(cs.filter(c => c.field === "VAT_REGISTERED").map(c => c.operator)).size > 1) return "VAT registered cannot be both true and false"
  return null
}

export type VersionDraft = { name: string; legalSource: string; frequency: string; riskLevel: string; responsibleRole: string; changeSummary: string }

/** What a version still needs before it can go for review. */
export function missingForReview(v: VersionDraft, versionNumber: number): string[] {
  const m: string[] = []
  if (!v.name.trim()) m.push("name")
  if (!v.legalSource.trim()) m.push("legal source")
  if (!v.frequency) m.push("frequency")
  if (!v.riskLevel) m.push("risk level")
  if (!v.responsibleRole) m.push("responsible role")
  if (versionNumber > 1 && !v.changeSummary.trim()) m.push("what changed")
  return m
}

/** Whether the approver needs to give a reason: they wrote or sent the version. */
export function approveNeedsReason(v: { authoredBy: string | null; submittedBy: string | null }, userId: string | null | undefined): boolean {
  return !!userId && (v.authoredBy === userId || v.submittedBy === userId)
}

export function approveBlocker(needsReason: boolean, reason: string, effectiveFrom: string): string | null {
  if (!effectiveFrom) return "Choose the date this version takes effect"
  if (needsReason && reason.trim().length < MIN_EXCEPTION_REASON) {
    return `You wrote or sent this, so someone else should approve it. If no one else can, give a reason of at least ${MIN_EXCEPTION_REASON} characters.`
  }
  return null
}

/** The span a version is in force, in words. */
export function describeSpan(from: string | null, to: string | null): string {
  if (!from) return "Not yet in force"
  return to ? `In force ${from} to ${to}` : `In force from ${from}`
}
