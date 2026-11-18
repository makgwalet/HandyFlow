import { describe, it, expect } from "vitest"
import { pctProblem, rateProblem, rateBody, restateText, savingPct } from "./scmPolicy"

describe("scmPolicy helpers", () => {
  it("validates a percentage", () => {
    expect(pctProblem("", "tolerance")).toContain("Enter")
    expect(pctProblem("101", "tolerance")).toContain("between")
    expect(pctProblem("-1", "tolerance")).toContain("between")
    expect(pctProblem("0", "tolerance")).toBeNull()
    expect(pctProblem("7.5", "tolerance")).toBeNull()
  })
  it("validates an exchange rate", () => {
    const ok = { currency: "usd", rate: "18.55", effectiveFrom: "2026-11-18" }
    expect(rateProblem(ok, "ZAR")).toBeNull()
    expect(rateProblem({ ...ok, currency: "ZAR" }, "ZAR")).toContain("base currency")
    expect(rateProblem({ ...ok, currency: "US" }, "ZAR")).toContain("3-letter")
    expect(rateProblem({ ...ok, rate: "0" }, "ZAR")).toContain("greater than zero")
    expect(rateProblem({ ...ok, rate: "1.123456789" }, "ZAR")).toContain("8 decimal")
    expect(rateProblem({ ...ok, effectiveFrom: "" }, "ZAR")).toContain("date")
  })
  it("sends the rate as upper-case currency and a number", () => {
    expect(rateBody({ currency: " usd ", rate: "18.5", effectiveFrom: "2026-11-18" })).toEqual({ currency: "USD", rate: 18.5, effectiveFrom: "2026-11-18" })
  })
  it("says what a change did to commitments", () => {
    expect(restateText(0, 0)).toBe("")
    expect(restateText(1, 0)).toBe("1 budget commitment restated.")
    expect(restateText(5, 2)).toContain("2 left alone")
  })
  it("computes the saving share", () => {
    expect(savingPct({ saving: 100, leakage: 0, listValue: 1000, paidValue: 900 })).toBe(10)
    expect(savingPct({ saving: 0, leakage: 0, listValue: 0, paidValue: 0 })).toBeNull()
  })
})
