import { describe, it, expect } from "vitest"
import { openFormProblem, resolveFormProblem, daysLate, needsAmount, isOpenDispute } from "./disputes.logic"

describe("disputes.logic", () => {
  it("checks the open form", () => {
    expect(openFormProblem({ reasonType: "", description: "long enough text", amountDisputed: "" })).toMatch(/reason/)
    expect(openFormProblem({ reasonType: "AMOUNT", description: "short", amountDisputed: "" })).toMatch(/Describe/)
    expect(openFormProblem({ reasonType: "AMOUNT", description: "long enough text", amountDisputed: "0" })).toMatch(/more than zero/)
    expect(openFormProblem({ reasonType: "AMOUNT", description: "long enough text", amountDisputed: "50" })).toBeNull()
    expect(openFormProblem({ reasonType: "OTHER", description: "long enough text", amountDisputed: "" })).toBeNull()
  })
  it("checks the resolve form", () => {
    const ok = "Debtor confirmed by phone"
    expect(resolveFormProblem({ resolution: "", note: ok, amountConceded: "" }, 100)).toMatch(/outcome/)
    expect(resolveFormProblem({ resolution: "REJECTED", note: "no", amountConceded: "" }, 100)).toMatch(/Explain/)
    expect(resolveFormProblem({ resolution: "UPHELD", note: ok, amountConceded: "" }, 100)).toMatch(/amount conceded/)
    expect(resolveFormProblem({ resolution: "UPHELD", note: ok, amountConceded: "150" }, 100)).toMatch(/More than/)
    expect(resolveFormProblem({ resolution: "UPHELD", note: ok, amountConceded: "100" }, 100)).toBeNull()
    expect(resolveFormProblem({ resolution: "PARTLY_UPHELD", note: ok, amountConceded: "40" }, null)).toBeNull()
    expect(resolveFormProblem({ resolution: "REJECTED", note: ok, amountConceded: "10" }, 100)).toMatch(/No amount/)
    expect(resolveFormProblem({ resolution: "WITHDRAWN", note: ok, amountConceded: "" }, 100)).toBeNull()
  })
  it("counts days late", () => {
    expect(daysLate("2026-10-23", new Date(2026, 9, 23))).toBe(0)
    expect(daysLate("2026-10-23", new Date(2026, 9, 26))).toBe(3)
    expect(daysLate("2026-10-23", new Date(2026, 9, 20))).toBe(0)
  })
  it("knows which outcomes concede money and which disputes are open", () => {
    expect(needsAmount("UPHELD")).toBe(true)
    expect(needsAmount("REJECTED")).toBe(false)
    expect(isOpenDispute("UNDER_REVIEW")).toBe(true)
    expect(isOpenDispute("RESOLVED")).toBe(false)
  })
})
