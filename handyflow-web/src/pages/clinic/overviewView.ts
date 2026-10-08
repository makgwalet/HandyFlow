// Rules for the patient overview: age wording, sex at birth, pregnancy status and the add-dependant form.
// Pure, so they can be tested without a screen.
import { saIdParts } from "./patientFile.shared"

export const SEX_AT_BIRTH = ["MALE", "FEMALE", "INTERSEX", "UNKNOWN"] as const
export const SEX_LABEL: Record<string, string> = { MALE: "Male", FEMALE: "Female", INTERSEX: "Intersex", UNKNOWN: "Unknown" }
export const PREGNANCY = ["NOT_PREGNANT", "PREGNANT", "UNKNOWN"] as const
export const PREGNANCY_LABEL: Record<string, string> = { NOT_PREGNANT: "Not pregnant", PREGNANT: "Pregnant", UNKNOWN: "Unknown" }

/** "7 m" under two years, "34 y" after. Null when there is no usable date of birth. */
export function ageText(dob: string | null | undefined, now: Date = new Date()): string | null {
  if (!dob || !/^\d{4}-\d{2}-\d{2}/.test(dob)) return null
  const [y, m, d] = dob.slice(0, 10).split("-").map(Number)
  if (!y || !m || !d) return null
  let months = (now.getFullYear() - y) * 12 + (now.getMonth() + 1 - m)
  if (now.getDate() < d) months -= 1
  if (months < 0) return null
  return months < 24 ? `${months} m` : `${Math.floor(months / 12)} y`
}

/** What the SA ID number suggests. Only a suggestion: sex at birth is confirmed by a person. */
export function sexFromSaId(id: string | null | undefined): "MALE" | "FEMALE" | null {
  const p = saIdParts(id ?? undefined)
  return p ? (p.male ? "MALE" : "FEMALE") : null
}

export const pregnancyApplies = (sexAtBirth: string | null | undefined) => sexAtBirth === "FEMALE"

/** The PATCH body. Pregnancy status is cleared whenever sex at birth is not female, so it cannot be left stale. */
export function profilePatch(sexAtBirth: string, pregnancyStatus: string): { sexAtBirth: string | null; pregnancyStatus: string | null } {
  const sex = sexAtBirth || null
  return { sexAtBirth: sex, pregnancyStatus: pregnancyApplies(sex) && pregnancyStatus ? pregnancyStatus : null }
}

export interface DependantForm { firstName: string; lastName: string; idNumber: string; dateOfBirth: string; gender: string; phone: string; relationship: string; sexAtBirth: string }
export const EMPTY_DEPENDANT: DependantForm = { firstName: "", lastName: "", idNumber: "", dateOfBirth: "", gender: "", phone: "", relationship: "CHILD", sexAtBirth: "" }

/** Why the dependant cannot be added yet, or null. */
export function dependantProblem(f: DependantForm, now: Date = new Date()): string | null {
  if (!f.firstName.trim() || !f.lastName.trim()) return "First and last name are required."
  const id = f.idNumber.replace(/\D/g, "")
  if (f.idNumber.trim() && (id.length !== 13 || !saIdParts(id))) return "That is not a valid SA ID number (13 digits with a real date of birth). Leave it empty if there is none."
  if (f.dateOfBirth) {
    const t = new Date(f.dateOfBirth + "T00:00:00")
    if (Number.isNaN(t.getTime())) return "Date of birth is not a valid date."
    if (t.getTime() > now.getTime()) return "Date of birth cannot be in the future."
  }
  return null
}

/** Fills date of birth, gender and the sex-at-birth suggestion from a complete valid ID, never overwriting what was typed. */
export function applyIdNumber(f: DependantForm, raw: string): DependantForm {
  const v = raw.replace(/\D/g, "").slice(0, 13)
  const p = saIdParts(v)
  if (v.length !== 13 || !p) return { ...f, idNumber: v }
  const dob = `${p.year}-${String(p.month).padStart(2, "0")}-${String(p.day).padStart(2, "0")}`
  const sex = p.male ? "MALE" : "FEMALE"
  return { ...f, idNumber: v, dateOfBirth: dob, gender: sex, sexAtBirth: f.sexAtBirth || sex }
}
