// Pure rules for the six-step employee registration page. The server checks everything again.
import { EMPTY_FOREIGN, anyForeign, foreignBody, foreignProblem, type ForeignForm } from "./foreignIdentity.logic"
import { NEW_SCHEME_FORM, schemeBody, schemeProblem, type SchemeForm } from "./payrollChecks.logic"

export const STEPS = [
  { id: "personal", title: "Personal information", blurb: "Capture the employee's identity and personal details." },
  { id: "contact", title: "Contact & emergency details", blurb: "How to reach the employee, and who to call in an emergency." },
  { id: "employment", title: "Employment & organisation", blurb: "The role, when it starts and where the person sits." },
  { id: "pay", title: "Compensation & benefits", blurb: "Salary, allowances and medical scheme membership." },
  { id: "banking", title: "Payroll & banking", blurb: "Where salary is paid and the tax number for SARS." },
  { id: "review", title: "Review & submit", blurb: "Check everything, then register the employee." },
] as const

export interface RegForm {
  firstName: string; lastName: string; idNumber: string; dateOfBirth: string; gender: string; race: string; foreign: ForeignForm
  email: string; phone: string; emergencyContactName: string; emergencyContactPhone: string; emergencyContactRelation: string
  startDate: string; employmentType: string; jobTitle: string; department: string; notes: string
  grossSalary: string; payFrequency: string; travelAllowance: string; pensionContribution: string
  scheme: SchemeForm; enrolled: boolean
  bankName: string; bankAccountNumber: string; bankBranchCode: string; taxNumber: string
}

export const EMPTY_REG: RegForm = {
  firstName: "", lastName: "", idNumber: "", dateOfBirth: "", gender: "", race: "", foreign: EMPTY_FOREIGN,
  email: "", phone: "", emergencyContactName: "", emergencyContactPhone: "", emergencyContactRelation: "",
  startDate: "", employmentType: "PERMANENT", jobTitle: "", department: "", notes: "",
  grossSalary: "", payFrequency: "MONTHLY", travelAllowance: "", pensionContribution: "",
  scheme: NEW_SCHEME_FORM, enrolled: false,
  bankName: "", bankAccountNumber: "", bankBranchCode: "", taxNumber: "",
}

/** Why a field is needed, shown beside it. */
export const WHY: Record<string, string> = {
  firstName: "Printed on the payslip and the tax certificate.",
  lastName: "Printed on the payslip and the tax certificate.",
  startDate: "Pay and leave are counted from this date.",
  grossSalary: "The payslip is calculated from it.",
}

export type Errors = Record<string, string>

export function validateSaId(id: string): { valid: boolean; dob?: string; gender?: string } {
  if (!/^\d{13}$/.test(id)) return { valid: false }
  const d = id.split("").map(Number)
  let sum = 0
  for (let i = 0; i < 12; i++) { let x = d[i]; if (i % 2 !== 0) { x *= 2; if (x > 9) x -= 9 } sum += x }
  if ((10 - (sum % 10)) % 10 !== d[12]) return { valid: false }
  const yr = parseInt(id.slice(0, 2)), mo = id.slice(2, 4), dy = id.slice(4, 6)
  const full = yr < 30 ? 2000 + yr : 1900 + yr
  return { valid: true, dob: `${full}-${mo}-${dy}`, gender: parseInt(id.slice(6, 10)) >= 5000 ? "MALE" : "FEMALE" }
}

const email = (s: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim())
const amount = (s: string) => s.trim() !== "" && Number.isFinite(Number(s)) && Number(s) >= 0

/** Problems on one step, keyed by field. Empty means the step can move on. */
export function stepErrors(step: number, f: RegForm): Errors {
  const e: Errors = {}
  if (step === 0) {
    if (!f.firstName.trim()) e.firstName = "Enter the first name"
    if (!f.lastName.trim()) e.lastName = "Enter the last name"
    const id = f.idNumber.trim()
    if (id && !validateSaId(id).valid) e.idNumber = id.length === 13 ? "This ID number's check digit is wrong" : "A South African ID number is 13 digits"
    const fp = foreignProblem(f.foreign); if (fp) e.foreign = fp
  }
  if (step === 1) {
    if (f.email.trim() && !email(f.email)) e.email = "That email address does not look right"
    if (f.phone.trim() && !/^\+?[\d\s()-]{9,16}$/.test(f.phone.trim())) e.phone = "Enter a phone number with 9 to 15 digits"
    if (f.emergencyContactPhone.trim() && !/^\+?[\d\s()-]{9,16}$/.test(f.emergencyContactPhone.trim())) e.emergencyContactPhone = "Enter a phone number with 9 to 15 digits"
    if (f.emergencyContactName.trim() && !f.emergencyContactPhone.trim()) e.emergencyContactPhone = "Add a number for the emergency contact"
  }
  if (step === 2) {
    if (!f.startDate) e.startDate = "Choose the start date"
  }
  if (step === 3) {
    if (!f.grossSalary.trim()) e.grossSalary = "Enter the gross salary"
    else if (!amount(f.grossSalary)) e.grossSalary = "The salary must be a number, 0 or more"
    for (const k of ["travelAllowance", "pensionContribution"] as const) if (f[k].trim() && !amount(f[k])) e[k] = "Enter an amount, 0 or more"
    if (f.enrolled) { const p = schemeProblem(f.scheme); if (p) e.scheme = p.replace(/\.$/, "") }
  }
  if (step === 4) {
    if (f.bankAccountNumber.trim() && !/^\d{6,17}$/.test(f.bankAccountNumber.trim())) e.bankAccountNumber = "An account number is 6 to 17 digits"
    if (f.bankBranchCode.trim() && !/^\d{6}$/.test(f.bankBranchCode.trim())) e.bankBranchCode = "A branch code is 6 digits"
    if (f.bankAccountNumber.trim() && !f.bankName.trim()) e.bankName = "Choose the bank"
    if (f.taxNumber.trim() && !/^\d{10}$/.test(f.taxNumber.trim())) e.taxNumber = "A SARS tax number is 10 digits"
  }
  return e
}

export const allErrors = (f: RegForm): Errors => [0, 1, 2, 3, 4].reduce<Errors>((a, s) => ({ ...a, ...stepErrors(s, f) }), {})

/** The first step that has a problem, or -1. */
export function firstBadStep(f: RegForm): number {
  for (let s = 0; s < 5; s++) if (Object.keys(stepErrors(s, f)).length) return s
  return -1
}

/** Which step a field belongs to, so a server complaint can take the person to it. */
export const STEP_OF: Record<string, number> = {
  firstName: 0, lastName: 0, idNumber: 0, dateOfBirth: 0, gender: 0, race: 0, foreign: 0,
  email: 1, phone: 1, emergencyContactName: 1, emergencyContactPhone: 1, emergencyContactRelation: 1,
  startDate: 2, employmentType: 2, jobTitle: 2, department: 2, notes: 2,
  grossSalary: 3, payFrequency: 3, travelAllowance: 3, pensionContribution: 3, scheme: 3,
  bankName: 4, bankAccountNumber: 4, bankBranchCode: 4, taxNumber: 4,
}

const n = (s: string) => s.trim() || null

export function regBody(f: RegForm) {
  return {
    firstName: f.firstName.trim(), lastName: f.lastName.trim(), idNumber: n(f.idNumber), taxNumber: n(f.taxNumber),
    dateOfBirth: n(f.dateOfBirth), gender: n(f.gender), race: n(f.race), email: n(f.email), phone: n(f.phone),
    startDate: f.startDate, employmentType: f.employmentType, jobTitle: n(f.jobTitle), department: n(f.department),
    grossSalary: Number(f.grossSalary), payFrequency: f.payFrequency,
    bankName: n(f.bankName), bankAccountNumber: n(f.bankAccountNumber), bankBranchCode: n(f.bankBranchCode),
    // Medical aid is a scheme membership (saved after registration), not a money field on the employee.
    medicalAidContribution: 0,
    pensionContribution: f.pensionContribution.trim() ? Number(f.pensionContribution) : 0,
    travelAllowance: f.travelAllowance.trim() ? Number(f.travelAllowance) : 0,
    emergencyContactName: n(f.emergencyContactName), emergencyContactPhone: n(f.emergencyContactPhone),
    emergencyContactRelation: n(f.emergencyContactRelation), notes: n(f.notes),
    ...(anyForeign(f.foreign) ? { foreign: foreignBody(f.foreign) } : {}),
  }
}

export const enrolBody = (f: RegForm) => (f.enrolled ? schemeBody(f.scheme) : null)

/** What the server complained about, mapped onto the form: errors by field plus the step to show. */
export function serverErrors(data: { errors?: Record<string, string>; message?: string } | undefined): { errors: Errors; step: number; message: string } {
  const errors: Errors = {}
  let step = -1
  for (const [k, v] of Object.entries(data?.errors ?? {})) { errors[k] = v; const s = STEP_OF[k]; if (s !== undefined && (step < 0 || s < step)) step = s }
  const message = data?.message ?? ""
  if (!Object.keys(errors).length && /ID number/i.test(message)) { errors.idNumber = message; step = 0 }
  return { errors, step, message }
}

// ── draft kept in the browser ──────────────────────────────────────────────────────────────
export interface Draft { v: 1; step: number; savedAt: string; form: RegForm }
export const draftKey = (userId: string | undefined | null) => `hf-hr-employee-draft:${userId ?? "anon"}`
export const hasContent = (f: RegForm) => JSON.stringify(f) !== JSON.stringify(EMPTY_REG)
export const toDraft = (form: RegForm, step: number, now = new Date()): string => JSON.stringify({ v: 1, step, savedAt: now.toISOString(), form } satisfies Draft)
/** A scheme from a draft saved before the split existed keeps its single count, as other dependants. */
function oldScheme(saved: Partial<RegForm["scheme"]> | undefined): RegForm["scheme"] {
  const s = { ...NEW_SCHEME_FORM, ...(saved ?? {}) }
  const hadSplit = saved && (saved.spouses !== undefined || saved.children !== undefined || saved.other !== undefined)
  return hadSplit ? s : { ...s, other: saved?.dependants ?? "0" }
}
/** A draft read back; anything unreadable is treated as no draft. Missing fields fall back to empty so an old draft still opens. */
export function fromDraft(raw: string | null): Draft | null {
  if (!raw) return null
  try {
    const d = JSON.parse(raw) as Partial<Draft>
    if (d.v !== 1 || !d.form || typeof d.form !== "object") return null
    const form = { ...EMPTY_REG, ...d.form, foreign: { ...EMPTY_FOREIGN, ...(d.form.foreign ?? {}) }, scheme: oldScheme(d.form.scheme) }
    const step = Number.isInteger(d.step) ? Math.min(Math.max(d.step as number, 0), STEPS.length - 1) : 0
    return { v: 1, step, savedAt: d.savedAt ?? "", form }
  } catch { return null }
}
