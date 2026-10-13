import { describe, it, expect } from "vitest"
import { canVoid, listParams, statusLabel, voidProblem } from "./issuedDocumentsView"

describe("issued documents", () => {
  it("only a document that stands can be withdrawn", () => {
    expect(canVoid({ status: "ISSUED" })).toBe(true)
    expect(canVoid({ status: "VOIDED" })).toBe(false)
  })
  it("needs a reason of 5 to 500 characters", () => {
    expect(voidProblem("no")).toMatch(/at least 5/)
    expect(voidProblem("x".repeat(501))).toMatch(/under 500/)
    expect(voidProblem(" Issued to the wrong patient ")).toBeNull()
  })
  it("sends only the filters that are set", () => {
    expect(listParams("", "", "  ")).toEqual({})
    expect(listParams("SICK_NOTE", "VOIDED", " thandi ")).toEqual({ type: "SICK_NOTE", status: "VOIDED", q: "thandi" })
  })
  it("calls a voided document withdrawn", () => {
    expect(statusLabel("VOIDED")).toBe("Withdrawn")
    expect(statusLabel("ISSUED")).toBe("Issued")
  })
})
