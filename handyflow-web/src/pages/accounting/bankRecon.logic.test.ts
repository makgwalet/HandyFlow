import { describe, it, expect } from "vitest"
import { ageText, describeRule, ruleFormProblem } from "./bankRecon.logic"

describe("describeRule", () => {
  it("puts direction, text and a range in words", () => {
    expect(describeRule({ direction: "OUT", matchText: "BANK FEE", matchField: "DESCRIPTION", minAmount: null, maxAmount: 200 }))
      .toContain("Money out, description contains “BANK FEE”, up to R")
  })
  it("handles a range and no text", () => {
    const s = describeRule({ direction: "IN", matchText: null, matchField: null, minAmount: 10, maxAmount: 20 })
    expect(s.startsWith("Money in, between R")).toBe(true)
  })
})

describe("ruleFormProblem", () => {
  const ok = { name: "Fees", matchText: "FEE", minAmount: "", maxAmount: "", otherAccountId: "a1" }
  it("accepts a complete rule", () => expect(ruleFormProblem(ok)).toBeNull())
  it("needs a name and an account", () => {
    expect(ruleFormProblem({ ...ok, name: " " })).toMatch(/name/)
    expect(ruleFormProblem({ ...ok, otherAccountId: "" })).toMatch(/account/)
  })
  it("needs a condition", () => expect(ruleFormProblem({ ...ok, matchText: "" })).toMatch(/text to look for/))
  it("an amount range alone is a condition, but not a backwards one", () => {
    expect(ruleFormProblem({ ...ok, matchText: "", minAmount: "5" })).toBeNull()
    expect(ruleFormProblem({ ...ok, minAmount: "50", maxAmount: "10" })).toMatch(/more than/)
    expect(ruleFormProblem({ ...ok, minAmount: "x" })).toMatch(/numbers/)
  })
})

describe("ageText", () => {
  it("reads in days", () => {
    expect(ageText(0)).toBe("today"); expect(ageText(1)).toBe("1 day"); expect(ageText(12)).toBe("12 days")
  })
})
