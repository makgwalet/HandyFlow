// src/pages/hr/profile.logic.ts
//
// Next of kin, dependants and the career timeline. Plain values in, plain values out; the server checks everything again.

export interface RelativeView {
  id: string; employeeId: string; kind: "NEXT_OF_KIN" | "DEPENDANT"; fullName: string; relationship: string; relationshipLabel: string
  phone: string | null; email: string | null; dateOfBirth: string | null; age: number | null; address: string | null
  beneficiary: boolean; beneficiaryPercent: number | null; notes: string | null; updatedAt: string
}

export interface RelativeForm {
  kind: string; fullName: string; relationship: string; phone: string; email: string; dateOfBirth: string; address: string
  beneficiary: boolean; beneficiaryPercent: string; notes: string
}

export const RELATIONSHIPS: { value: string; label: string }[] = [
  { value: "SPOUSE", label: "Spouse" }, { value: "PARTNER", label: "Life partner" }, { value: "CHILD", label: "Child" }, { value: "PARENT", label: "Parent" },
  { value: "SIBLING", label: "Brother or sister" }, { value: "GUARDIAN", label: "Guardian" }, { value: "FRIEND", label: "Friend" }, { value: "OTHER", label: "Other" },
]

export const emptyRelative = (kind: "NEXT_OF_KIN" | "DEPENDANT"): RelativeForm => ({
  kind, fullName: "", relationship: kind === "DEPENDANT" ? "CHILD" : "", phone: "", email: "", dateOfBirth: "", address: "", beneficiary: false, beneficiaryPercent: "", notes: "",
})

export const fromRelative = (r: RelativeView): RelativeForm => ({
  kind: r.kind, fullName: r.fullName, relationship: r.relationship, phone: r.phone ?? "", email: r.email ?? "", dateOfBirth: r.dateOfBirth ?? "",
  address: r.address ?? "", beneficiary: r.beneficiary, beneficiaryPercent: r.beneficiaryPercent == null ? "" : String(r.beneficiaryPercent), notes: r.notes ?? "",
})

const today = () => new Date().toISOString().slice(0, 10)

/** Everyone's shares added together, leaving out the line being edited. */
export function sharesTotal(list: RelativeView[], excludingId?: string | null): number {
  return list.reduce((sum, r) => sum + (r.beneficiary && r.beneficiaryPercent != null && r.id !== excludingId ? Number(r.beneficiaryPercent) : 0), 0)
}

/** Null when fine. The same rules, in the same words, as the server. */
export function relativeProblem(f: RelativeForm, others: RelativeView[], editingId: string | null = null, now: string = today()): string | null {
  if (!f.fullName.trim()) return "Give the person's full name."
  if (f.fullName.trim().length > 120) return "The name is too long (120 characters at most)."
  if (!RELATIONSHIPS.some(r => r.value === f.relationship)) return "Choose how this person is related to the employee."
  if (f.kind === "NEXT_OF_KIN" && !f.phone.trim()) return "Give a phone number, so the person can be reached."
  if (f.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) return "That email address does not look right."
  if (f.dateOfBirth && f.dateOfBirth > now) return "The date of birth is in the future."
  if (f.kind === "DEPENDANT" && !f.dateOfBirth) return "Give the dependant's date of birth."
  if (f.beneficiary && f.beneficiaryPercent.trim()) {
    const p = Number(f.beneficiaryPercent)
    if (!Number.isFinite(p) || p < 0 || p > 100) return "A beneficiary's share must be between 0 and 100 percent."
    const total = sharesTotal(others, editingId) + p
    if (total > 100) return `Beneficiary shares add up to ${+total.toFixed(2)}%, which is more than 100%.`
  }
  return null
}

export const relativeBody = (f: RelativeForm) => ({
  kind: f.kind, fullName: f.fullName.trim(), relationship: f.relationship, phone: f.phone.trim() || null, email: f.email.trim() || null,
  dateOfBirth: f.dateOfBirth || null, address: f.address.trim() || null, beneficiary: f.beneficiary,
  beneficiaryPercent: f.beneficiary && f.beneficiaryPercent.trim() ? Number(f.beneficiaryPercent) : null, notes: f.notes.trim() || null,
})

/** "Beneficiary, 50%", "Beneficiary" (no share given), or empty. */
export const beneficiaryText = (r: Pick<RelativeView, "beneficiary" | "beneficiaryPercent">): string =>
  !r.beneficiary ? "" : r.beneficiaryPercent == null ? "Beneficiary" : `Beneficiary, ${+Number(r.beneficiaryPercent).toFixed(2)}%`

// ── Career ──

export interface CareerView {
  id: string; kind: string; kindLabel: string; eventDate: string; endDate: string | null; employer: string | null; jobTitle: string | null
  department: string | null; fromValue: string | null; toValue: string | null; percentChange: number | null; note: string | null
  payRelated: boolean; auto: boolean; createdAt: string
}

export interface CareerForm { kind: string; eventDate: string; endDate: string; employer: string; jobTitle: string; department: string; note: string }

export const CAREER_KINDS: { value: string; label: string }[] = [
  { value: "PRIOR_EMPLOYMENT", label: "Earlier employer" }, { value: "PROMOTION", label: "Promotion" },
  { value: "TRANSFER", label: "Transfer" }, { value: "OTHER", label: "Other" },
]

export const emptyCareer = (): CareerForm => ({ kind: "PRIOR_EMPLOYMENT", eventDate: "", endDate: "", employer: "", jobTitle: "", department: "", note: "" })

/** Null when fine. {@code startDate} is the day the person joined this company. */
export function careerProblem(f: CareerForm, startDate: string | null, now: string = today()): string | null {
  if (!CAREER_KINDS.some(k => k.value === f.kind)) return "Choose earlier employer, promotion, transfer or other."
  if (!f.eventDate) return "Give the date."
  const limit = new Date(now + "T00:00:00Z"); limit.setUTCFullYear(limit.getUTCFullYear() + 1)
  if (f.eventDate > limit.toISOString().slice(0, 10)) return "The date is more than a year ahead."
  if (f.note.trim().length > 500) return "The note is too long (500 characters at most)."
  if (f.kind === "PRIOR_EMPLOYMENT") {
    if (!f.employer.trim()) return "Give the earlier employer."
    if (!f.jobTitle.trim()) return "Give the job title held there."
    if (!f.endDate) return "Give the date the person left that job."
    if (f.endDate < f.eventDate) return "The person cannot leave a job before starting it."
    if (startDate && f.endDate > startDate) return "An earlier job must end on or before the day the person joined this company."
    return null
  }
  if (startDate && f.eventDate < startDate) return "This is dated before the person joined the company."
  if (f.kind === "PROMOTION" && !f.jobTitle.trim()) return "Give the job title the person was promoted to."
  if (f.kind === "TRANSFER" && !f.department.trim()) return "Give the department the person moved to."
  if (f.kind === "OTHER" && !f.note.trim()) return "Say what happened."
  return null
}

export const careerBody = (f: CareerForm) => ({
  kind: f.kind, eventDate: f.eventDate, endDate: f.kind === "PRIOR_EMPLOYMENT" ? f.endDate || null : null,
  employer: f.kind === "PRIOR_EMPLOYMENT" ? f.employer.trim() || null : null, jobTitle: f.jobTitle.trim() || null, department: f.department.trim() || null, note: f.note.trim() || null,
})

const money = (v: string | null): string => {
  const n = Number(v)
  return v == null || !Number.isFinite(n) ? "—" : `R${n.toLocaleString("en-ZA", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
}

const EMPLOYMENT_TYPES: Record<string, string> = { PERMANENT: "Permanent", FIXED_TERM: "Fixed term", CONTRACT: "Contract", PART_TIME: "Part time", TEMPORARY: "Temporary", INTERN: "Intern" }

/** One line saying what the entry is about: the change, the position, or the earlier employer. */
export function careerSummary(e: CareerView): string {
  switch (e.kind) {
    case "SALARY_CHANGED": {
      const pct = e.percentChange == null ? "" : ` (${e.percentChange > 0 ? "+" : ""}${e.percentChange}%)`
      return `${money(e.fromValue)} → ${money(e.toValue)}${pct}`
    }
    case "EMPLOYMENT_TYPE_CHANGED": return `${EMPLOYMENT_TYPES[e.fromValue ?? ""] ?? e.fromValue ?? "—"} → ${EMPLOYMENT_TYPES[e.toValue ?? ""] ?? e.toValue ?? "—"}`
    case "TITLE_CHANGED": case "DEPARTMENT_CHANGED": case "MANAGER_CHANGED": return `${e.fromValue ?? "—"} → ${e.toValue ?? "—"}`
    case "HIRED": return [e.jobTitle, e.department].filter(Boolean).join(", ")
    case "PRIOR_EMPLOYMENT": return [e.jobTitle, e.employer].filter(Boolean).join(" at ")
    case "PROMOTION": return e.jobTitle ?? ""
    case "TRANSFER": return e.department ?? ""
    default: return ""
  }
}

/** Whole years and months between two dates, for "3 years 2 months with us"; empty when the end is before the start. */
export function tenureText(from: string, to: string = today()): string {
  const a = new Date(from + "T00:00:00Z"), b = new Date(to + "T00:00:00Z")
  let months = (b.getUTCFullYear() - a.getUTCFullYear()) * 12 + (b.getUTCMonth() - a.getUTCMonth())
  if (b.getUTCDate() < a.getUTCDate()) months -= 1
  if (months < 0) return ""
  const y = Math.floor(months / 12), m = months % 12
  const parts = [y ? `${y} ${y === 1 ? "year" : "years"}` : "", m ? `${m} ${m === 1 ? "month" : "months"}` : ""].filter(Boolean)
  return parts.length ? parts.join(" ") : "less than a month"
}
