import { describe, it, expect } from "vitest"
import { canCorrect, correctionBody, correctionProblem, detailsChanged, detailsOf, notesProblem, type CorrectableClaim } from "./claimCorrection"

const claim: CorrectableClaim = {
  status: "REJECTED", schemeName: "Discovery", memberNumber: "123456", dependentCode: "01",
  lines: [
    { lineType: "CONSULTATION", tariffCode: "0190", icd10Code: "J06.9", description: "Consultation", quantity: 1, unitPrice: 480 },
    { lineType: "PROCEDURE", tariffCode: "0201", icd10Code: "J06.9", description: "Spirometry", quantity: 2, unitPrice: 150 },
    { lineType: "MEDICINE", description: "Amoxicillin", quantity: 1, unitPrice: 90 },
  ],
}

describe("claim correction", () => {
  it("a claim is correctable until money has moved on it", () => {
    for (const status of ["DRAFT", "SUBMITTED", "ACCEPTED", "REJECTED"]) expect(canCorrect({ status })).toBe(true)
    for (const status of ["PARTIAL", "PAID", "CLOSED", "VOIDED"]) expect(canCorrect({ status })).toBe(false)
    expect(canCorrect({ status: "ACCEPTED", schemePaid: 1 })).toBe(false)
    expect(canCorrect({ status: "ACCEPTED", writtenOff: 1 })).toBe(false)
    expect(canCorrect({ status: "ACCEPTED", credited: 1 })).toBe(false)
  })

  it("sends no replacement body when the scheme details are untouched, so the server copies the claim", () => {
    const b = correctionBody(claim, detailsOf(claim), "  Scheme asked for it again  ")
    expect(b).toEqual({ reason: "Scheme asked for it again", corrected: null })
  })

  it("sends the whole claim with the new scheme details when they changed; medicine lines are left to the server", () => {
    const d = { ...detailsOf(claim), memberNumber: "654321" }
    expect(detailsChanged(claim, d)).toBe(true)
    const b = correctionBody(claim, d, "Wrong member number captured")
    expect(b.corrected).toMatchObject({
      schemeName: "Discovery", memberNumber: "654321", dependentCode: "01",
      consultationTariffCode: "0190", consultationIcd10Code: "J06.9", consultationRate: 480,
    })
    expect(b.corrected!.procedures).toEqual([{ tariffCode: "0201", icd10Code: "J06.9", description: "Spirometry", quantity: 2, unitPrice: 150 }])
  })

  it("a cleared field becomes null rather than an empty string", () => {
    const b = correctionBody(claim, { ...detailsOf(claim), dependentCode: "  " }, "Dependent code was not needed")
    expect(b.corrected!.dependentCode).toBeNull()
  })

  it("needs a reason of 10 to 500 characters", () => {
    expect(correctionProblem("short")).toMatch(/at least 10/)
    expect(correctionProblem("x".repeat(501))).toMatch(/under 500/)
    expect(correctionProblem("A proper reason")).toBeNull()
  })

  it("notes are limited to 1000 characters", () => {
    expect(notesProblem("x".repeat(1000))).toBeNull()
    expect(notesProblem("x".repeat(1001))).toMatch(/under 1000/)
    expect(notesProblem("")).toBeNull()
  })
})
