import { describe, it, expect } from "vitest"
import { fieldProblem, sourceState, confirmNeedsReason, confirmBlocker, labelOf, ENTITY_TYPES, profileProgress, TRACKED_FIELDS } from "./profileRules"

describe("fieldProblem", () => {
  it("accepts empty values", () => {
    expect(fieldProblem("taxNumber", "  ")).toBeNull()
    expect(fieldProblem("employeeCount", "")).toBeNull()
  })
  it("checks registration numbers", () => {
    expect(fieldProblem("registrationNumber", "2015 / 123456 / 07")).toBeNull()
    expect(fieldProblem("registrationNumber", "12")).not.toBeNull()
    expect(fieldProblem("registrationNumber", "20;15")).not.toBeNull()
  })
  it("checks tax and VAT numbers", () => {
    expect(fieldProblem("taxNumber", "9012 345 678")).toBeNull()
    expect(fieldProblem("taxNumber", "12345")).toMatch(/10 digits/)
    expect(fieldProblem("vatNumber", "4123456789")).toBeNull()
    expect(fieldProblem("vatNumber", "9012345678")).toMatch(/start with 4/)
  })
  it("checks employee counts", () => {
    expect(fieldProblem("employeeCount", "12")).toBeNull()
    expect(fieldProblem("employeeCount", "-1")).not.toBeNull()
    expect(fieldProblem("employeeCount", "1.5")).not.toBeNull()
    expect(fieldProblem("employeeCount", "abc")).not.toBeNull()
  })
})

describe("sourceState", () => {
  it("tells the four states apart", () => {
    expect(sourceState(undefined)).toBe("NONE")
    expect(sourceState({ fieldName: "a", source: "DOCUMENT", verified: false, confirmedByEnterer: false })).toBe("UNCONFIRMED")
    expect(sourceState({ fieldName: "a", source: "DOCUMENT", verified: true, confirmedByEnterer: false })).toBe("CONFIRMED")
    expect(sourceState({ fieldName: "a", source: "DOCUMENT", verified: true, confirmedByEnterer: true })).toBe("CONFIRMED_BY_ENTERER")
  })
})

describe("confirming", () => {
  const s = { fieldName: "a", source: "DOCUMENT", verified: false, confirmedByEnterer: false, recordedBy: "u1" }
  it("needs a reason only from the person who entered it", () => {
    expect(confirmNeedsReason(s, "u1")).toBe(true)
    expect(confirmNeedsReason(s, "u2")).toBe(false)
    expect(confirmNeedsReason(undefined, "u1")).toBe(false)
    expect(confirmNeedsReason(s, null)).toBe(false)
  })
  it("blocks a short reason", () => {
    expect(confirmBlocker(false, "")).toBeNull()
    expect(confirmBlocker(true, "short")).toMatch(/at least 15/)
    expect(confirmBlocker(true, "Sole practitioner, nobody else")).toBeNull()
  })
})

describe("labels and progress", () => {
  it("shows a label, the raw value, or a dash", () => {
    expect(labelOf(ENTITY_TYPES, "NPC")).toBe("Non-profit company")
    expect(labelOf(ENTITY_TYPES, "XYZ")).toBe("XYZ")
    expect(labelOf(ENTITY_TYPES, null)).toBe("—")
  })
  it("counts filled and confirmed facts", () => {
    expect(profileProgress(["legalName", "vatNumber"], ["vatNumber"])).toEqual({ filled: 2, confirmed: 1, total: TRACKED_FIELDS.length })
  })
})
