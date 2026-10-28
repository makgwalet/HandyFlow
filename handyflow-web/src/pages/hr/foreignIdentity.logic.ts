// src/pages/hr/foreignIdentity.logic.ts
//
// Passport and work permit for employees without a South African ID. Plain values in, plain values out. Mirrors EmployeeRules on the
// server, which checks everything again: the country code is only checked for shape here, the server knows the real list.

export interface ForeignForm {
  passportNumber: string; passportCountry: string; passportExpiry: string
  workPermitType: string; workPermitNumber: string; workPermitExpiry: string
}

/** What the server sends and takes. Empty strings never leave the form: they go as null. */
export interface ForeignBody {
  passportNumber: string | null; passportCountry: string | null; passportExpiry: string | null
  workPermitType: string | null; workPermitNumber: string | null; workPermitExpiry: string | null
}

export const EMPTY_FOREIGN: ForeignForm = { passportNumber: "", passportCountry: "", passportExpiry: "", workPermitType: "", workPermitNumber: "", workPermitExpiry: "" }

export const PERMIT_TYPES: { value: string; label: string }[] = [
  { value: "PERMANENT_RESIDENCE", label: "Permanent residence" },
  { value: "GENERAL_WORK", label: "General work visa" },
  { value: "CRITICAL_SKILLS", label: "Critical skills work visa" },
  { value: "INTRA_COMPANY_TRANSFER", label: "Intra-company transfer visa" },
  { value: "CORPORATE", label: "Corporate visa" },
  { value: "RELATIVE", label: "Relative's visa (spouse or life partner)" },
  { value: "ASYLUM_OR_REFUGEE", label: "Asylum seeker permit or refugee status" },
  { value: "OTHER", label: "Other" },
]

export const permitLabel = (type: string | null | undefined): string => PERMIT_TYPES.find(t => t.value === type)?.label ?? "—"

export const cleanPassport = (s: string): string => s.replace(/[\s-]/g, "").toUpperCase()

export function fromServer(f: Partial<ForeignBody> | null | undefined): ForeignForm {
  return {
    passportNumber: f?.passportNumber ?? "", passportCountry: f?.passportCountry ?? "", passportExpiry: f?.passportExpiry ?? "",
    workPermitType: f?.workPermitType ?? "", workPermitNumber: f?.workPermitNumber ?? "", workPermitExpiry: f?.workPermitExpiry ?? "",
  }
}

export const anyForeign = (f: ForeignForm): boolean => Object.values(f).some(v => v.trim() !== "")

/** Null when empty or fine. The same rules, in the same words, as the server. */
export function foreignProblem(f: ForeignForm, startDate?: string): string | null {
  const num = cleanPassport(f.passportNumber)
  const country = f.passportCountry.trim().toUpperCase()
  if (num || country || f.passportExpiry) {
    if (!num) return "Give the passport number as well as its country or expiry date."
    if (!/^[A-Z0-9]+$/.test(num)) return "The passport number can only have letters and digits."
    if (num.length < 5 || num.length > 20) return "The passport number must be 5 to 20 letters and digits."
    if (!country) return "Give the country that issued the passport."
    if (!/^[A-Z]{2}$/.test(country)) return "The passport country must be a two-letter country code, for example ZW or MW."
  }
  const type = f.workPermitType.trim()
  if (!type) {
    if (f.workPermitNumber.trim() || f.workPermitExpiry) return "Choose the kind of work permit or visa."
  } else {
    if (!f.workPermitNumber.trim()) return "Give the permit or visa number."
    if (f.workPermitNumber.trim().length > 40) return "The permit number is too long (40 characters at most)."
    if (type !== "PERMANENT_RESIDENCE" && !f.workPermitExpiry) return "Give the date the permit or visa expires."
    if (startDate && f.workPermitExpiry && f.workPermitExpiry < startDate) return "The work permit expires before the start date."
  }
  return null
}

/** Cleaned values, blanks as null. A permit type of "" drops the permit's other fields, as the server does. */
export function foreignBody(f: ForeignForm): ForeignBody {
  const num = cleanPassport(f.passportNumber)
  const type = f.workPermitType.trim()
  return {
    passportNumber: num || null,
    passportCountry: num ? (f.passportCountry.trim().toUpperCase() || null) : null,
    passportExpiry: num ? (f.passportExpiry || null) : null,
    workPermitType: type || null,
    workPermitNumber: type ? (f.workPermitNumber.trim() || null) : null,
    workPermitExpiry: type ? (f.workPermitExpiry || null) : null,
  }
}

const DAY_MS = 86_400_000
const daysBetween = (from: string, to: string): number => Math.round((Date.parse(to + "T00:00:00Z") - Date.parse(from + "T00:00:00Z")) / DAY_MS)

export type ExpiryState = "expired" | "soon" | "ok" | null

/** Expired, within 90 days, or fine. Null when there is no date. */
export function expiryState(date: string | null | undefined, today: string = new Date().toISOString().slice(0, 10)): ExpiryState {
  if (!date) return null
  const left = daysBetween(today, date.slice(0, 10))
  return left < 0 ? "expired" : left <= 90 ? "soon" : "ok"
}

/** "Expires in 30 days", "Expires tomorrow", "Expires today", "Expired 3 days ago"; empty without a date. */
export function expiryText(date: string | null | undefined, today: string = new Date().toISOString().slice(0, 10)): string {
  if (!date) return ""
  const left = daysBetween(today, date.slice(0, 10))
  if (left > 1) return `Expires in ${left} days`
  if (left === 1) return "Expires tomorrow"
  if (left === 0) return "Expires today"
  return `Expired ${-left} ${-left === 1 ? "day" : "days"} ago`
}

// ── Employee list: documents ending ───────────────────────────────────────────────────────────

/** The choices for the "documents ending" filter on the employee list. The value is the days ahead the server looks; 0 is already ended or ending today. */
export const EXPIRING_FILTERS: { value: string; label: string }[] = [
  { value: "", label: "Any documents" },
  { value: "0", label: "Passport or permit ended" },
  { value: "30", label: "Ending within 30 days" },
  { value: "60", label: "Ending within 60 days" },
  { value: "90", label: "Ending within 90 days" },
]

/** The `expiringWithin` query value for a filter choice, or null for no filter (or a value the server would refuse). */
export function expiringParam(choice: string): string | null {
  if (choice.trim() === "") return null
  const n = Number(choice)
  return Number.isInteger(n) && n >= 0 && n <= 366 ? String(n) : null
}

/** The document on a row that is nearest its end, as "Work permit expired 3 days ago"; null when nothing is ended or ending within 90 days. */
export function endingBadge(f: Partial<ForeignBody> | null | undefined, today: string = new Date().toISOString().slice(0, 10)): { text: string; state: "expired" | "soon" } | null {
  if (!f) return null
  const docs = [
    { label: "Work permit", date: f.workPermitExpiry ?? null },
    { label: "Passport", date: f.passportExpiry ?? null },
  ].filter((d): d is { label: string; date: string } => !!d.date && expiryState(d.date, today) !== "ok")
  if (docs.length === 0) return null
  docs.sort((a, b) => a.date.localeCompare(b.date))
  const first = docs[0]
  return { text: `${first.label} ${expiryText(first.date, today).toLowerCase()}`, state: expiryState(first.date, today) as "expired" | "soon" }
}
