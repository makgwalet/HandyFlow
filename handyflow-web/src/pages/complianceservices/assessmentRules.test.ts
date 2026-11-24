import { describe, it, expect } from "vitest"
import { judgementProblem, weightsProblem, scoreText, changeText, statusLabel, statusTone, controlLabel } from "./assessmentRules"

describe("judgementProblem", () => {
  it("lets a yes through with no note", () => expect(judgementProblem({ control: "YES", workflow: "NONE", note: "" })).toBeNull())
  it("wants a note for no or partial", () => {
    expect(judgementProblem({ control: "NO", workflow: "NONE", note: "short" })).toMatch(/what is missing/)
    expect(judgementProblem({ control: "PARTIAL", workflow: "NONE", note: "" })).toMatch(/what is missing/)
    expect(judgementProblem({ control: "NO", workflow: "NONE", note: "No information officer appointed" })).toBeNull()
  })
  it("wants a note when something is under way", () => {
    expect(judgementProblem({ control: "YES", workflow: "REMEDIATION", note: "" })).toMatch(/what is being done/)
    expect(judgementProblem({ control: "YES", workflow: "AUTHORITY", note: "Application lodged with the regulator" })).toBeNull()
  })
  it("ignores spaces when counting the note", () => expect(judgementProblem({ control: "NO", workflow: "NONE", note: "   short   " })).not.toBeNull())
})

describe("weightsProblem", () => {
  it("accepts rising weights", () => expect(weightsProblem({ low: 1, medium: 2, high: 4, critical: 8 })).toBeNull())
  it("accepts equal weights", () => expect(weightsProblem({ low: 3, medium: 3, high: 3, critical: 3 })).toBeNull())
  it("refuses out of range or fractional weights", () => {
    expect(weightsProblem({ low: 0, medium: 2, high: 4, critical: 8 })).toMatch(/1 to 100/)
    expect(weightsProblem({ low: 1, medium: 2, high: 4, critical: 101 })).toMatch(/1 to 100/)
    expect(weightsProblem({ low: 1.5, medium: 2, high: 4, critical: 8 })).toMatch(/whole number/)
  })
  it("refuses weights that fall as risk rises", () => expect(weightsProblem({ low: 5, medium: 2, high: 4, critical: 8 })).toMatch(/riskier/))
})

describe("scoreText", () => {
  it("shows the percentage and counts", () => expect(scoreText(76.9, 3, 0, false)).toBe("76.9% · 3 scored"))
  it("says nothing scored rather than 0%", () => expect(scoreText(null, 0, 2, true)).toBe("Nothing scored yet · 0 scored · 2 not assessed · provisional"))
})

describe("labels", () => {
  it("names statuses and controls", () => {
    expect(statusLabel("EVIDENCE_MISSING")).toBe("Evidence missing")
    expect(statusLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW")
    expect(controlLabel("PARTIAL")).toBe("Partly in place")
  })
  it("tones never leave a status without one", () => {
    expect(statusTone("COMPLIANT")).toBe("good")
    expect(statusTone("REMEDIATION_IN_PROGRESS")).toBe("warn")
    expect(statusTone("EXPIRED")).toBe("bad")
    expect(statusTone("NOT_ASSESSED")).toBe("neutral")
  })
  it("describes a change with its reason", () => {
    expect(changeText({ code: "A", from: "COMPLIANT", to: "EXPIRED", reason: "Certificate expired" })).toBe("A: Compliant → Evidence expired (Certificate expired)")
    expect(changeText({ code: "B", from: null, to: "COMPLIANT", reason: null })).toBe("B: new → Compliant")
    expect(changeText({ code: "C", from: "COMPLIANT", to: null, reason: null })).toBe("C: Compliant → no longer assigned")
  })
})
