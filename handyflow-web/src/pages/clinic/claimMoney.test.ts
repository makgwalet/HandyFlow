import { describe, it, expect } from "vitest"
import { adjustmentProblem, allocationProblem, manualTotal, moneyActions, owed, reasonProblem } from "./claimMoney"

const claim = (o: object) => ({ status: "ACCEPTED", schemePortion: 1000, ...o })

describe("owed", () => {
  it("uses the server figure when present", () => expect(owed(claim({ schemeOutstanding: 250 }))).toBe(250))
  it("works it out otherwise, never below zero", () => {
    expect(owed(claim({ schemePaid: 400, writtenOff: 100 }))).toBe(500)
    expect(owed(claim({ schemePaid: 2000 }))).toBe(0)
  })
})

describe("moneyActions", () => {
  it("an accepted claim can be paid, part-paid, written off, credited or voided", () =>
    expect(moneyActions(claim({}))).toEqual(["paid", "partial", "writeOff", "creditNote", "void"]))
  it("a part-paid claim can no longer be voided", () =>
    expect(moneyActions(claim({ status: "PARTIAL", schemePaid: 300, schemeOutstanding: 700 }))).toEqual(["paid", "partial", "writeOff", "creditNote"]))
  it("a rejected claim can be credited or voided, not paid or written off", () =>
    expect(moneyActions(claim({ status: "REJECTED" }))).toEqual(["creditNote", "void"]))
  it("a submitted claim can only be voided", () => expect(moneyActions(claim({ status: "SUBMITTED" }))).toEqual(["void"]))
  it("paid, closed and voided claims offer nothing", () => {
    expect(moneyActions(claim({ status: "PAID", schemePaid: 1000, schemeOutstanding: 0 }))).toEqual([])
    expect(moneyActions(claim({ status: "CLOSED", writtenOff: 1000, schemeOutstanding: 0 }))).toEqual([])
    expect(moneyActions(claim({ status: "VOIDED" }))).toEqual([])
  })
  it("a cash claim with nothing owed by a scheme has no payment actions", () =>
    expect(moneyActions({ status: "ACCEPTED", schemePortion: 0 })).toEqual(["void"]))
})

describe("adjustmentProblem", () => {
  it("accepts an amount within the balance with a real reason", () => expect(adjustmentProblem("250.50", 300, "Scheme short-paid the tariff")).toBeNull())
  it("refuses more than is owed", () => expect(adjustmentProblem("300.01", 300, "Scheme short-paid the tariff")).toMatch(/more than/))
  it("refuses zero, text and empty", () => {
    expect(adjustmentProblem("0", 300, "Scheme short-paid the tariff")).not.toBeNull()
    expect(adjustmentProblem("abc", 300, "Scheme short-paid the tariff")).not.toBeNull()
    expect(adjustmentProblem("", 300, "Scheme short-paid the tariff")).not.toBeNull()
  })
  it("needs a reason of ten characters", () => expect(adjustmentProblem("10", 300, "short")).toMatch(/reason/))
})

describe("reasonProblem", () => {
  it("counts trimmed characters", () => {
    expect(reasonProblem("   short  ")).not.toBeNull()
    expect(reasonProblem("Wrong member number")).toBeNull()
    expect(reasonProblem("x".repeat(501))).not.toBeNull()
  })
})

describe("allocation", () => {
  const owedById = { a: 800, b: 400 }
  it("oldest-first needs only a scheme and an amount", () => expect(allocationProblem("Discovery", "1200", false, {}, owedById, "")).toBeNull())
  it("needs a scheme and an amount", () => {
    expect(allocationProblem("", "1200", false, {}, owedById, "")).toMatch(/scheme/)
    expect(allocationProblem("Discovery", "", false, {}, owedById, "")).toMatch(/amount/)
  })
  it("a manual split must add up and give a reason", () => {
    expect(allocationProblem("Discovery", "500", true, { a: "100", b: "400" }, owedById, "Remittance names these two")).toBeNull()
    expect(allocationProblem("Discovery", "500", true, { a: "100", b: "300" }, owedById, "Remittance names these two")).toMatch(/add up/)
    expect(allocationProblem("Discovery", "500", true, { a: "100", b: "400" }, owedById, "no")).toMatch(/reason/)
  })
  it("a manual amount cannot exceed what the claim owes", () =>
    expect(allocationProblem("Discovery", "500", true, { b: "500" }, owedById, "Remittance names this one")).toMatch(/more than/))
  it("manualTotal ignores blanks and flags non-numbers", () => {
    expect(manualTotal({ a: "100.10", b: "", c: "0.2" })).toBe(100.3)
    expect(manualTotal({ a: "x" })).toBeNull()
  })
})
