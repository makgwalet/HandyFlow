import { describe, it, expect } from "vitest"
import { keyDatesProblem, resultText, sourceText } from "./deadlineRules"

describe("keyDatesProblem", () => {
  it("accepts nothing, a month, and a past date", () => {
    expect(keyDatesProblem("", "", "2026-11-24")).toBeNull()
    expect(keyDatesProblem("2", "2019-03-15", "2026-11-24")).toBeNull()
  })
  it("refuses a bad month and a future registration", () => {
    expect(keyDatesProblem("13", "", "2026-11-24")).toMatch(/month/)
    expect(keyDatesProblem("", "2027-01-01", "2026-11-24")).toMatch(/future/)
  })
})

describe("resultText", () => {
  it("says what happened in one line", () => {
    expect(resultText({ created: 3, cancelled: 1, unchanged: 2, skipped: [{ code: "A", reason: "x" }] }))
      .toBe("3 new dates, 1 cancelled because the rule or applicability changed, 2 already there, 1 could not be worked out")
    expect(resultText({ created: 1, cancelled: 0, unchanged: 0, skipped: [] })).toBe("1 new date")
    expect(resultText({ created: 0, cancelled: 0, unchanged: 0, skipped: [] })).toBe("No new dates")
  })
})

describe("sourceText", () => {
  it("names the obligation and version, or says by hand", () => {
    expect(sourceText("CIPC-AR", 2)).toBe("From CIPC-AR v2")
    expect(sourceText(null, null)).toBe("Added by hand")
  })
})
