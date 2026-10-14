import { describe, it, expect } from "vitest"
import { accountProblem, agreementProblem, emptyLine, journalProblem, journalRequest, journalTotals, monthRange, paymentProblem, periodProblem, timeProblem, timeValue, unbilledTotal, type TimeEntry } from "./practice.logic"

const L = (accountId: string, debit = "", credit = "") => ({ ...emptyLine(), accountId, debit, credit })

describe("journal", () => {
  it("balances to the cent", () => {
    expect(journalTotals([L("a", "0.1"), L("b", "0.2"), L("c", "", "0.3")]).difference).toBe(0)
    expect(journalProblem("2026-10-01", [L("a", "0.1"), L("b", "0.2"), L("c", "", "0.3")])).toBeNull()
  })
  it("says what is wrong", () => {
    expect(journalProblem("", [])).toMatch(/date/)
    expect(journalProblem("2026-10-01", [L("a", "10")])).toMatch(/two lines/)
    expect(journalProblem("2026-10-01", [L("a", "10"), L("", "", "10")])).toMatch(/account on every line/)
    expect(journalProblem("2026-10-01", [L("a", "10", "5"), L("b", "", "5")])).toMatch(/not both/)
    expect(journalProblem("2026-10-01", [L("a", "10"), L("b")])).toMatch(/needs an amount/)
    expect(journalProblem("2026-10-01", [L("a", "10"), L("b", "", "7.5")])).toMatch(/^Debits and credits differ by R .*2[,.]50\.$/)
    expect(journalProblem("2026-10-01", [L("a", "-1"), L("b", "", "1")])).toMatch(/zero or more/)
  })
  it("builds the request without blank lines", () => {
    const r = journalRequest("c1", "2026-10-01", " Rent ", "", [L("a", "100"), L("b", "", "100"), emptyLine()])
    expect(r).toMatchObject({ clientId: "c1", description: "Rent", reference: null, entryType: "MANUAL" })
    expect(r.lines).toEqual([{ accountId: "a", description: null, debitAmount: 100, creditAmount: 0 }, { accountId: "b", description: null, debitAmount: 0, creditAmount: 100 }])
  })
})

describe("time", () => {
  const f = { date: "2026-10-10", activity: "VAT", description: "", hours: "1.5", rate: "850", billable: true, who: "" }
  it("checks date, hours and rate", () => {
    expect(timeProblem(f, "2026-10-14")).toBeNull()
    expect(timeProblem({ ...f, date: "2026-10-20" }, "2026-10-14")).toMatch(/future/)
    expect(timeProblem({ ...f, hours: "0" }, "2026-10-14")).toMatch(/Hours/)
    expect(timeProblem({ ...f, hours: "25" }, "2026-10-14")).toMatch(/Hours/)
    expect(timeProblem({ ...f, rate: "x" }, "2026-10-14")).toMatch(/rate/)
  })
  it("values time and totals only billable unbilled work", () => {
    expect(timeValue(f)).toBe(1275)
    const row = (lineTotal: number, billable: boolean) => ({ lineTotal, billable } as TimeEntry)
    expect(unbilledTotal([row(100.1, true), row(200.2, true), row(999, false)])).toBe(300.3)
  })
})

describe("agreements, invoices, accounts", () => {
  it("needs the fee that matches the billing type", () => {
    expect(agreementProblem("RETAINER", "", "500", "2026-01-01", "")).toMatch(/monthly fee/)
    expect(agreementProblem("RETAINER", "1500", "", "2026-01-01", "")).toBeNull()
    expect(agreementProblem("TIME_AND_MATERIALS", "1500", "", "2026-01-01", "")).toMatch(/hourly rate/)
    expect(agreementProblem("TIME_AND_MATERIALS", "", "850", "2026-01-01", "2026-01-01")).toMatch(/after the start/)
    expect(agreementProblem("RETAINER", "1500", "", "", "")).toMatch(/start/)
  })
  it("checks invoice periods and payments", () => {
    expect(periodProblem("2026-09-01", "2026-08-31")).toMatch(/before/)
    expect(periodProblem("2026-09-01", "2026-09-30")).toBeNull()
    expect(paymentProblem("0", 100)).toMatch(/amount/)
    expect(paymentProblem("100.01", 100)).toMatch(/more than/)
    expect(paymentProblem("100", 100)).toBeNull()
  })
  it("defaults the invoice period to last month, including January", () => {
    expect(monthRange(new Date(2026, 9, 14))).toEqual(["2026-09-01", "2026-09-30"])
    expect(monthRange(new Date(2026, 0, 5))).toEqual(["2025-12-01", "2025-12-31"])
    expect(monthRange(new Date(2026, 2, 5))).toEqual(["2026-02-01", "2026-02-28"])
  })
  it("validates a custom account", () => {
    expect(accountProblem("", "x", "ASSET")).toMatch(/code/)
    expect(accountProblem("9000", "", "ASSET")).toMatch(/name/)
    expect(accountProblem("9000", "Petty cash", "BAD")).toMatch(/type/)
    expect(accountProblem("9000", "Petty cash", "ASSET")).toBeNull()
  })
})
