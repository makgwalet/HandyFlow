// Allowed values and format checks for a client's profile. The server enforces them; this only catches mistakes early.

export const ENTITY_TYPES: [string, string][] = [
  ["PTY_LTD", "Private company (Pty) Ltd"], ["LTD", "Public company Ltd"], ["NPC", "Non-profit company"], ["CC", "Close corporation"],
  ["SOLE_PROPRIETOR", "Sole proprietor"], ["PARTNERSHIP", "Partnership"], ["TRUST", "Trust"], ["NGO", "NGO / NPO"],
  ["COOPERATIVE", "Co-operative"], ["STATE_ENTITY", "State entity"], ["OTHER", "Other"],
]
export const TURNOVER_BANDS: [string, string][] = [
  ["UNDER_1M", "Under R1m"], ["1M_TO_10M", "R1m – R10m"], ["10M_TO_50M", "R10m – R50m"], ["50M_TO_250M", "R50m – R250m"], ["OVER_250M", "Over R250m"],
]
export const RISK_RATINGS: [string, string][] = [["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"]]
export const ONBOARDING_STATUSES: [string, string][] = [
  ["NOT_STARTED", "Not started"], ["IN_PROGRESS", "In progress"], ["COMPLETE", "Complete"], ["ON_HOLD", "On hold"],
]
export const SOURCES: [string, string][] = [
  ["CLIENT_SUPPLIED", "Supplied by the client"], ["STAFF_ENTERED", "Entered by staff"],
  ["DOCUMENT", "Read from a document"], ["REGISTER_CHECK", "Checked against a register"],
]

/** The facts whose origin is recorded, in the order they are shown. */
export const TRACKED_FIELDS: [string, string][] = [
  ["legalName", "Legal name"], ["registrationNumber", "Registration number"], ["entityType", "Entity type"],
  ["taxNumber", "Tax number"], ["vatNumber", "VAT number"], ["registeredAddress", "Registered address"],
  ["industry", "Industry"], ["employeeCount", "Employees"], ["turnoverBand", "Turnover band"],
]

export const MIN_EXCEPTION_REASON = 15

export const labelOf = (options: [string, string][], value: string | null | undefined): string =>
  options.find(o => o[0] === value)?.[1] ?? (value || "—")

const digits = (v: string) => v.replace(/[\s-]/g, "")

/** A message when the value cannot be right, otherwise null. Empty is fine. */
export function fieldProblem(field: "registrationNumber" | "taxNumber" | "vatNumber" | "employeeCount", value: string): string | null {
  const v = value.trim()
  if (!v) return null
  if (field === "registrationNumber") return /^[0-9A-Za-z/-]{5,30}$/.test(v.replace(/\s+/g, "")) ? null : "Use letters, digits, / and - only"
  if (field === "taxNumber") return /^\d{10}$/.test(digits(v)) ? null : "Tax numbers have 10 digits"
  if (field === "vatNumber") {
    if (!/^\d{10}$/.test(digits(v))) return "VAT numbers have 10 digits"
    return digits(v).startsWith("4") ? null : "VAT numbers start with 4"
  }
  const n = Number(v)
  return Number.isInteger(n) && n >= 0 && n <= 10_000_000 ? null : "Enter a whole number, 0 or more"
}

export type FieldSource = {
  fieldName: string; source: string; verified: boolean; confirmedByEnterer: boolean; recordedBy?: string | null
}

export type SourceState = "NONE" | "UNCONFIRMED" | "CONFIRMED" | "CONFIRMED_BY_ENTERER"

export function sourceState(s: FieldSource | undefined): SourceState {
  if (!s) return "NONE"
  if (!s.verified) return "UNCONFIRMED"
  return s.confirmedByEnterer ? "CONFIRMED_BY_ENTERER" : "CONFIRMED"
}

/** Whether confirming needs a reason: the person confirming is the one who entered it. */
export function confirmNeedsReason(s: FieldSource | undefined, userId: string | null | undefined): boolean {
  return !!s && !!userId && !!s.recordedBy && s.recordedBy === userId
}

export function confirmBlocker(needsReason: boolean, reason: string): string | null {
  if (!needsReason) return null
  return reason.trim().length >= MIN_EXCEPTION_REASON
    ? null
    : `You entered this, so someone else should confirm it. If no one else can, give a reason of at least ${MIN_EXCEPTION_REASON} characters.`
}

/** How much of the profile is filled in and confirmed, for a one-line summary. */
export function profileProgress(filled: string[], unconfirmed: string[]): { filled: number; confirmed: number; total: number } {
  const total = TRACKED_FIELDS.length
  return { filled: filled.length, confirmed: filled.length - unconfirmed.length, total }
}
