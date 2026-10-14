import { describe, it, expect } from "vitest"
import { defaultQuery, forecastParams, fmtR, gridProblem, gridToLines, itemProblem, monthLabels, rowTotal, varianceText, type GridRow } from "./budget.logic"

const row = (months: string[]): GridRow => ({ accountId: "a1", code: "4000", name: "Sales", type: "INCOME", months })
const blank = () => Array(12).fill("")

describe("budget grid", () => {
  it("sends only the cells that have an amount", () => {
    const m = blank(); m[0] = "100"; m[3] = "250,5"; m[4] = "0"
    expect(gridToLines([row(m)])).toEqual([{ accountId: "a1", month: 0, amount: 100 }, { accountId: "a1", month: 3, amount: 250.5 }])
  })
  it("rejects text and negatives", () => {
    const m = blank(); m[1] = "abc"
    expect(gridProblem([row(m)])).toMatch(/not a number/)
    const n = blank(); n[1] = "-5"
    expect(gridProblem([row(n)])).toMatch(/negative/)
    expect(gridProblem([row(blank())])).toBeNull()
  })
  it("totals a row and labels twelve months from the start", () => {
    const m = blank(); m[0] = "10"; m[1] = "5,5"
    expect(rowTotal(row(m))).toBe(15.5)
    const l = monthLabels("2026-03-01")
    expect(l).toHaveLength(12)
    expect(l[0]).toMatch(/Mar/); expect(l[11]).toMatch(/Feb/)
  })
  it("words a variance and formats money", () => {
    expect(varianceText(-200, -20, false)).toMatch(/Worse by .*200[.,]00 \(20%\)/)
    expect(varianceText(50, null, true)).toMatch(/Better by/)
    expect(fmtR(-1234.5)).toMatch(/^-R .*1.?234[.,]50$/)
  })
})

describe("forecast inputs", () => {
  it("builds the query and drops an empty minimum", () => {
    expect(forecastParams(defaultQuery())).toBe("customerDelayDays=0&overdueReceiptInDays=14&overduePayInDays=7&collectPercent=100")
    expect(forecastParams({ ...defaultQuery(), lowBalance: "5000" })).toContain("lowBalance=5000")
  })
  it("checks a manual item", () => {
    expect(itemProblem("2026-11-01", "100", "Payroll")).toBeNull()
    expect(itemProblem("", "100", "Payroll")).toMatch(/date/)
    expect(itemProblem("2026-11-01", "0", "Payroll")).toMatch(/more than zero/)
    expect(itemProblem("2026-11-01", "5", " ")).toMatch(/what/)
  })
})
