import { describe, it, expect } from "vitest"
import { emptyForm, formProblem, lineTotals, nextText, toRequest } from "./recurring.logic"

const good = () => {
  const f = emptyForm("2026-11-01")
  f.name = "Rent"; f.description = "Monthly rent"
  f.lines = [{ accountId: "a", description: "", debit: "100", credit: "" }, { accountId: "b", description: "", debit: "", credit: "100" }]
  return f
}

describe("recurring.logic", () => {
  it("accepts a balanced journal", () => expect(formProblem(good())).toBeNull())
  it("asks for the basics", () => {
    const f = good(); f.name = " "
    expect(formProblem(f)).toMatch(/name/)
    const g = good(); g.endDate = "2026-10-01"
    expect(formProblem(g)).toMatch(/end date/)
  })
  it("refuses an unbalanced or malformed journal", () => {
    const f = good(); f.lines[1].credit = "90"
    expect(formProblem(f)).toMatch(/do not equal/)
    const g = good(); g.lines[0].credit = "5"
    expect(formProblem(g)).toMatch(/not both/)
    const h = good(); h.lines[1].accountId = ""
    expect(formProblem(h)).toMatch(/needs an account/)
    const i = good(); i.lines = [i.lines[0], { accountId: "", description: "", debit: "", credit: "" }]
    expect(formProblem(i)).toMatch(/at least two/)
  })
  it("adds up lines to the cent", () => {
    expect(lineTotals([{ accountId: "a", description: "", debit: "0.1", credit: "" }, { accountId: "b", description: "", debit: "0.2", credit: "" }]).debit).toBe(0.3)
  })
  it("checks a prepayment", () => {
    const f = emptyForm("2026-11-01"); f.name = "Insurance"; f.description = "Annual insurance"; f.kind = "PREPAYMENT"
    expect(formProblem(f)).toMatch(/amount/)
    f.totalAmount = "1200"; expect(formProblem(f)).toMatch(/at least 2/)
    f.runs = "12"; expect(formProblem(f)).toMatch(/accounts/)
    f.expenseAccountId = "x"; f.prepaidAccountId = "x"; expect(formProblem(f)).toMatch(/differ/)
    f.prepaidAccountId = "y"; expect(formProblem(f)).toBeNull()
    expect(toRequest(f)).toMatchObject({ kind: "PREPAYMENT", totalAmount: 1200, runs: 12, lines: null, expenseAccountId: "x", prepaidAccountId: "y" })
  })
  it("builds the request for a recurring journal", () => {
    expect(toRequest(good())).toMatchObject({ kind: "RECURRING", totalAmount: null, endDate: null, runs: null,
      lines: [{ accountId: "a", debit: 100, credit: 0 }, { accountId: "b", debit: 0, credit: 100 }] })
  })
  it("says what happens next", () => {
    expect(nextText({ status: "ACTIVE", nextRunDate: "2026-12-01", runsDone: 2, runsTotal: 12 })).toBe("Next draft on 2026-12-01, 10 to go.")
    expect(nextText({ status: "PAUSED", nextRunDate: null, runsDone: 0, runsTotal: null })).toMatch(/Paused/)
    expect(nextText({ status: "ENDED", nextRunDate: null, runsDone: 12, runsTotal: 12 })).toMatch(/Finished after 12/)
  })
})
