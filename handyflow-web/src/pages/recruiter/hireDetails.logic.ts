// src/pages/recruiter/hireDetails.logic.ts
//
// The payroll details a new hire may give when an applicant is turned into an employee. All optional; the server checks them again with
// HR's own rules. What is left out stays empty and the employee's onboarding keeps asking for it.
import { bankProblem } from "../hr/employees.logic"

export interface HireDetails { idNumber: string; taxNumber: string; bankName: string; bankAccountNumber: string; bankBranchCode: string }

export const EMPTY_HIRE_DETAILS: HireDetails = { idNumber: "", taxNumber: "", bankName: "", bankAccountNumber: "", bankBranchCode: "" }

const digits = (s: string) => s.replace(/\s/g, "")

export function hireDetailsProblem(d: HireDetails): string | null {
  if (d.idNumber.trim() && !/^\d{13}$/.test(digits(d.idNumber))) return "A South African ID number is 13 digits."
  if (d.taxNumber.trim() && !/^\d{10}$/.test(digits(d.taxNumber))) return "A SARS tax number is 10 digits."
  // The bank fields go together, but all three may be left empty.
  if (!d.bankName.trim() && !d.bankAccountNumber.trim() && !d.bankBranchCode.trim()) return null
  return bankProblem(d.bankName, d.bankAccountNumber, d.bankBranchCode)
}

/** Blank fields are sent as null so the server leaves them empty. */
export function hireDetailsBody(d: HireDetails) {
  const v = (s: string) => (s.trim() ? s.trim() : null)
  return { idNumber: v(d.idNumber), taxNumber: v(d.taxNumber), bankName: v(d.bankName), bankAccountNumber: v(d.bankAccountNumber), bankBranchCode: v(d.bankBranchCode) }
}

/** How many of the five details are still missing, for the line under the form. */
export const missingCount = (d: HireDetails) => Object.values(d).filter(x => !x.trim()).length
