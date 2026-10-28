import { describe, it, expect } from "vitest"
import { EMPTY_HIRE_DETAILS, hireDetailsBody, hireDetailsProblem, missingCount, personalMissing } from "./hireDetails.logic"

const d = (over = {}) => ({ ...EMPTY_HIRE_DETAILS, ...over })

describe("hire details", () => {
  it("accepts nothing at all, because every detail is optional", () => {
    expect(hireDetailsProblem(d())).toBeNull()
    expect(missingCount(d())).toBe(5)
  })
  it("checks the ID and tax number lengths, ignoring spaces", () => {
    expect(hireDetailsProblem(d({ idNumber: "800101 5009 087" }))).toBeNull()
    expect(hireDetailsProblem(d({ idNumber: "12345" }))).toMatch(/13 digits/)
    expect(hireDetailsProblem(d({ taxNumber: "0123456789" }))).toBeNull()
    expect(hireDetailsProblem(d({ taxNumber: "123" }))).toMatch(/10 digits/)
  })
  it("wants the bank, account and branch to go together", () => {
    expect(hireDetailsProblem(d({ bankName: "FNB" }))).toMatch(/account number/)
    expect(hireDetailsProblem(d({ bankAccountNumber: "62012345678" }))).toMatch(/bank name/)
    expect(hireDetailsProblem(d({ bankName: "FNB", bankAccountNumber: "62012345678", bankBranchCode: "250655" }))).toBeNull()
    expect(hireDetailsProblem(d({ bankName: "FNB", bankAccountNumber: "62012345678", bankBranchCode: "25" }))).toMatch(/6 digits/)
  })
  it("sends blanks as null and trims what is given", () => {
    expect(hireDetailsBody(d({ idNumber: " 8001015009087 ", bankName: "  " }))).toEqual({ idNumber: "8001015009087", taxNumber: null, bankName: null, bankAccountNumber: null, bankBranchCode: null, dateOfBirth: null, gender: null, emergencyContactName: null, emergencyContactPhone: null })
  })
  it("checks the date of birth is not in the future and the emergency contact comes whole", () => {
    expect(hireDetailsProblem(d({ dateOfBirth: "1990-05-01" }), "2026-10-28")).toBeNull()
    expect(hireDetailsProblem(d({ dateOfBirth: "2027-01-01" }), "2026-10-28")).toMatch(/future/)
    expect(hireDetailsProblem(d({ emergencyContactName: "Sipho" }))).toMatch(/together/)
    expect(hireDetailsProblem(d({ emergencyContactPhone: "0821234567" }))).toMatch(/together/)
    expect(hireDetailsProblem(d({ emergencyContactName: "Sipho", emergencyContactPhone: "0821234567" }))).toBeNull()
  })
  it("sends the personal details and counts what is missing", () => {
    const full = d({ dateOfBirth: "1990-05-01", gender: "FEMALE", emergencyContactName: " Sipho ", emergencyContactPhone: "0821234567" })
    expect(hireDetailsBody(full)).toMatchObject({ dateOfBirth: "1990-05-01", gender: "FEMALE", emergencyContactName: "Sipho", emergencyContactPhone: "0821234567" })
    expect(personalMissing(full)).toBe(0)
    expect(personalMissing(d())).toBe(3)
    expect(personalMissing(d({ gender: "MALE" }))).toBe(2)
    expect(missingCount(full)).toBe(5)
  })
})
