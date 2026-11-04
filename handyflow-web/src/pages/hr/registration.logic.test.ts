import { describe, it, expect } from "vitest"
import { EMPTY_REG, allErrors, enrolBody, firstBadStep, fromDraft, hasContent, regBody, serverErrors, stepErrors, toDraft, validateSaId, type RegForm } from "./registration.logic"

const ok: RegForm = { ...EMPTY_REG, firstName: "Thandi", lastName: "Nkosi", startDate: "2026-11-01", grossSalary: "25000" }

describe("registration steps", () => {
  it("only the name, start date and salary are required", () => {
    expect(Object.keys(stepErrors(0, EMPTY_REG)).sort()).toEqual(["firstName", "lastName"])
    expect(stepErrors(2, EMPTY_REG).startDate).toBeTruthy()
    expect(stepErrors(3, EMPTY_REG).grossSalary).toBeTruthy()
    expect(allErrors(ok)).toEqual({})
    expect(firstBadStep(ok)).toBe(-1)
    expect(firstBadStep({ ...ok, startDate: "" })).toBe(2)
  })
  it("checks the SA ID with its check digit", () => {
    expect(validateSaId("8001015009087").valid).toBe(true)
    expect(stepErrors(0, { ...ok, idNumber: "8001015009088" }).idNumber).toMatch(/check digit/)
    expect(stepErrors(0, { ...ok, idNumber: "800101" }).idNumber).toMatch(/13 digits/)
  })
  it("checks contact, bank and tax formats only when something is typed", () => {
    expect(stepErrors(1, { ...ok, email: "nope" }).email).toBeTruthy()
    expect(stepErrors(1, { ...ok, emergencyContactName: "Sipho" }).emergencyContactPhone).toMatch(/number/)
    expect(stepErrors(4, ok)).toEqual({})
    expect(stepErrors(4, { ...ok, bankAccountNumber: "123" }).bankAccountNumber).toBeTruthy()
    expect(stepErrors(4, { ...ok, bankAccountNumber: "1234567890" }).bankName).toBeTruthy()
    expect(stepErrors(4, { ...ok, bankBranchCode: "25" }).bankBranchCode).toBeTruthy()
    expect(stepErrors(4, { ...ok, taxNumber: "123" }).taxNumber).toBeTruthy()
  })
  it("a medical scheme only counts when enrolled, and never blocks when not", () => {
    expect(stepErrors(3, { ...ok, enrolled: false })).toEqual({})
    expect(stepErrors(3, { ...ok, enrolled: true }).scheme).toBeTruthy()
    const f = { ...ok, enrolled: true, scheme: { schemeName: "Discovery", memberNumber: "1", dependants: "2", effectiveFrom: "2026-11-01", notes: "" } }
    expect(stepErrors(3, f)).toEqual({})
    expect(enrolBody(f)).toMatchObject({ schemeName: "Discovery", dependants: 2 })
    expect(enrolBody(ok)).toBeNull()
  })
  it("sends medical aid as 0 on the employee and numbers as numbers", () => {
    const b = regBody({ ...ok, travelAllowance: "500" })
    expect(b).toMatchObject({ grossSalary: 25000, travelAllowance: 500, pensionContribution: 0, medicalAidContribution: 0, idNumber: null })
    expect(b).not.toHaveProperty("foreign")
  })
  it("takes a server complaint to the right step", () => {
    expect(serverErrors({ errors: { startDate: "bad", firstName: "x" } }).step).toBe(0)
    expect(serverErrors({ message: "Another employee already has this ID number." })).toMatchObject({ step: 0, errors: { idNumber: expect.any(String) } })
    expect(serverErrors({ message: "Something else" }).step).toBe(-1)
  })
  it("keeps a draft and survives rubbish", () => {
    expect(hasContent(EMPTY_REG)).toBe(false)
    expect(hasContent(ok)).toBe(true)
    const d = fromDraft(toDraft(ok, 3, new Date("2026-11-04T10:00:00Z")))!
    expect(d.step).toBe(3); expect(d.form.firstName).toBe("Thandi"); expect(d.savedAt).toBe("2026-11-04T10:00:00.000Z")
    expect(fromDraft(null)).toBeNull(); expect(fromDraft("{nope")).toBeNull(); expect(fromDraft('{"v":2}')).toBeNull()
    expect(fromDraft('{"v":1,"step":99,"form":{"firstName":"A"}}')!.step).toBe(5)
    expect(fromDraft('{"v":1,"step":1,"form":{"firstName":"A"}}')!.form.employmentType).toBe("PERMANENT")
  })
})
