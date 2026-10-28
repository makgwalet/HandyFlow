// src/pages/recruiter/hireDetails.logic.ts
//
// The payroll details a new hire may give when an applicant is turned into an employee. All optional; the server checks them again with
// HR's own rules. What is left out stays empty and the employee's onboarding keeps asking for it.
import { bankProblem } from "../hr/employees.logic"

export interface HireDetails {
  idNumber: string; taxNumber: string; bankName: string; bankAccountNumber: string; bankBranchCode: string
  dateOfBirth: string; gender: string; race: string; emergencyContactName: string; emergencyContactPhone: string; emergencyContactRelation: string
}
export const GENDERS: { value: string; label: string }[] = [
  { value: "MALE", label: "Male" }, { value: "FEMALE", label: "Female" }, { value: "NON_BINARY", label: "Non-binary" }, { value: "PREFER_NOT_TO_SAY", label: "Prefer not to say" },
]

/** Race is asked for employment-equity reporting only, and is optional. Same list as HR's employee form. */
export const RACES: { value: string; label: string }[] = [
  { value: "AFRICAN", label: "African" }, { value: "COLOURED", label: "Coloured" }, { value: "INDIAN", label: "Indian" }, { value: "WHITE", label: "White" }, { value: "OTHER", label: "Other" },
]
export const MAX_RELATION = 50

export const EMPTY_HIRE_DETAILS: HireDetails = { idNumber: "", taxNumber: "", bankName: "", bankAccountNumber: "", bankBranchCode: "", dateOfBirth: "", gender: "", race: "", emergencyContactName: "", emergencyContactPhone: "", emergencyContactRelation: "" }

const digits = (s: string) => s.replace(/\s/g, "")

export function hireDetailsProblem(d: HireDetails, today: string = new Date().toISOString().slice(0, 10)): string | null {
  if (d.dateOfBirth && d.dateOfBirth > today) return "The date of birth is in the future."
  if (!!d.emergencyContactName.trim() !== !!d.emergencyContactPhone.trim()) return "Give the emergency contact's name and phone number together, or neither."
  if (d.race && !RACES.some(r => r.value === d.race)) return "Race must be one of: African, Coloured, Indian, White or Other."
  if (d.emergencyContactRelation.trim() && !d.emergencyContactName.trim()) return "Give the emergency contact's name as well as their relation."
  if (d.emergencyContactRelation.trim().length > MAX_RELATION) return `The emergency contact's relation is too long (${MAX_RELATION} characters at most).`
  if (d.idNumber.trim() && !/^\d{13}$/.test(digits(d.idNumber))) return "A South African ID number is 13 digits."
  if (d.taxNumber.trim() && !/^\d{10}$/.test(digits(d.taxNumber))) return "A SARS tax number is 10 digits."
  // The bank fields go together, but all three may be left empty.
  if (!d.bankName.trim() && !d.bankAccountNumber.trim() && !d.bankBranchCode.trim()) return null
  return bankProblem(d.bankName, d.bankAccountNumber, d.bankBranchCode)
}

/** Blank fields are sent as null so the server leaves them empty. */
export function hireDetailsBody(d: HireDetails) {
  const v = (s: string) => (s.trim() ? s.trim() : null)
  return { idNumber: v(d.idNumber), taxNumber: v(d.taxNumber), bankName: v(d.bankName), bankAccountNumber: v(d.bankAccountNumber), bankBranchCode: v(d.bankBranchCode),
    dateOfBirth: d.dateOfBirth || null, gender: d.gender || null, race: d.race || null, emergencyContactName: v(d.emergencyContactName), emergencyContactPhone: v(d.emergencyContactPhone),
    emergencyContactRelation: v(d.emergencyContactRelation) }
}

/** How many of the five details are still missing, for the line under the form. */
export const missingCount = (d: HireDetails) =>
  [d.idNumber, d.taxNumber, d.bankName, d.bankAccountNumber, d.bankBranchCode].filter(x => !x.trim()).length

/** How many of the personal details (date of birth, gender, emergency contact) are still missing. */
export const personalMissing = (d: HireDetails) => [d.dateOfBirth, d.gender, d.emergencyContactName.trim() && d.emergencyContactPhone.trim()].filter(x => !x).length
