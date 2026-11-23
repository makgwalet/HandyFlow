import { describe, expect, it } from "vitest"
import { dateRangeParams, dateRangeProblem } from "./agLedger.logic"

describe("ledger date range", () => {
  it("accepts empty ends, one end, the same day and a proper range", () => {
    expect(dateRangeProblem("", "")).toBe("")
    expect(dateRangeProblem("2026-07-01", "")).toBe("")
    expect(dateRangeProblem("", "2026-09-30")).toBe("")
    expect(dateRangeProblem("2026-07-01", "2026-07-01")).toBe("")
    expect(dateRangeProblem("2026-07-01", "2026-09-30")).toBe("")
  })
  it("refuses a from after the to", () => {
    expect(dateRangeProblem("2026-10-01", "2026-09-30")).toMatch(/from.*after.*to/)
  })
  it("sends only the ends that are set, and nothing while the order is wrong", () => {
    expect(dateRangeParams("", "")).toEqual({})
    expect(dateRangeParams("2026-07-01", "")).toEqual({ from: "2026-07-01" })
    expect(dateRangeParams("", "2026-09-30")).toEqual({ to: "2026-09-30" })
    expect(dateRangeParams("2026-07-01", "2026-09-30")).toEqual({ from: "2026-07-01", to: "2026-09-30" })
    expect(dateRangeParams("2026-10-01", "2026-09-30")).toEqual({})
  })
})
