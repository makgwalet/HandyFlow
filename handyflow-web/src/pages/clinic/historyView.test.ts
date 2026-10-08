import { describe, it, expect } from "vitest"
import { EMPTY_AID, EMPTY_FAMILY, EMPTY_SOCIAL, aidBody, aidProblem, certParams, certProblem, familyBody, familyProblem, newestFirst, socialForm, socialLines } from "./historyView"

describe("family history", () => {
  it("needs a condition and a sensible age", () => {
    expect(familyProblem(EMPTY_FAMILY)).toMatch(/condition/)
    expect(familyProblem({ ...EMPTY_FAMILY, conditionName: "Asthma", ageAtOnset: "121" })).toMatch(/0 to 120/)
    expect(familyProblem({ ...EMPTY_FAMILY, conditionName: "Asthma", ageAtOnset: "4.5" })).toMatch(/whole number/)
    expect(familyProblem({ ...EMPTY_FAMILY, conditionName: "Asthma", ageAtOnset: "" })).toBeNull()
    expect(familyProblem({ ...EMPTY_FAMILY, conditionName: "Asthma", ageAtOnset: "0" })).toBeNull()
  })
  it("sends trimmed values and null for blanks", () => {
    expect(familyBody({ relative: "FATHER", conditionName: " Hypertension ", ageAtOnset: "", notes: " " }))
      .toEqual({ relative: "FATHER", conditionName: "Hypertension", ageAtOnset: null, notes: null })
    expect(familyBody({ relative: "MOTHER", conditionName: "Diabetes", ageAtOnset: "55", notes: "" }).ageAtOnset).toBe(55)
  })
})

describe("lifestyle and social history", () => {
  it("shows only what was recorded, never 'unknown'", () => {
    expect(socialLines(EMPTY_SOCIAL)).toEqual([])
    expect(socialLines(null)).toEqual([])
    expect(socialLines({ smokingStatus: "FORMER", occupation: " Teacher ", alcoholUse: "UNKNOWN" }))
      .toEqual([["Smoking", "Former"], ["Occupation", "Teacher"]])
  })
  it("fills missing fields from the empty form", () => {
    expect(socialForm({ smokingStatus: "NEVER", occupation: null as any }).occupation).toBe("")
    expect(socialForm(undefined).alcoholUse).toBe("UNKNOWN")
  })
})

describe("medical aid", () => {
  it("needs the scheme and the member number", () => {
    expect(aidProblem(EMPTY_AID)).toMatch(/scheme/)
    expect(aidProblem({ ...EMPTY_AID, schemeName: "Discovery" })).toMatch(/member number/)
    expect(aidProblem({ ...EMPTY_AID, schemeName: "Discovery", memberNumber: "1", dependentCode: "12345678901" })).toMatch(/too long/)
    expect(aidProblem({ ...EMPTY_AID, schemeName: "Discovery", memberNumber: "1" })).toBeNull()
  })
  it("sends null for blanks", () => {
    expect(aidBody({ ...EMPTY_AID, schemeName: " Bonitas ", memberNumber: "9" })).toMatchObject({ schemeName: "Bonitas", planName: null, memberNumber: "9" })
  })
})

describe("quick sick note and referral", () => {
  it("offers the newest consultation first", () => {
    expect(newestFirst([{ id: "a", consultedAt: "2026-10-01T08:00:00Z" }, { id: "b", consultedAt: "2026-10-07T08:00:00Z" }]).map(c => c.id)).toEqual(["b", "a"])
  })
  it("needs a consultation, and the last day cannot be before the first", () => {
    expect(certProblem({ consultationId: "", unfitFrom: "", unfitTo: "", notes: "" })).toMatch(/consultation/)
    expect(certProblem({ consultationId: "c", unfitFrom: "2026-10-08", unfitTo: "2026-10-07", notes: "" })).toMatch(/before/)
    expect(certProblem({ consultationId: "c", unfitFrom: "2026-10-08", unfitTo: "2026-10-10", notes: "" })).toBeNull()
  })
  it("sends only the dates and notes that were filled in", () => {
    expect(certParams({ consultationId: "c", unfitFrom: "2026-10-08", unfitTo: "", notes: " rest " }).toString()).toBe("unfitFrom=2026-10-08&notes=rest")
  })
})
