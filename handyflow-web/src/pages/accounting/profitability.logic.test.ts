import { describe, it, expect } from "vitest"
import { direct, split, lossMakers, ruleProblem, ruleSummary, subtypeLabel, marginText, cell, type Row } from "./profitability.logic"

const row = (code: string, income: number, expenses: Record<string, number>): Row => {
  const expenseTotal = Object.values(expenses).reduce((a, b) => a + b, 0)
  return { centreId: code, code, name: code, kind: "SITE", income, expenses, expenseTotal, profit: income - expenseTotal, marginPercent: null }
}

describe("profitability.logic", () => {
  it("splits direct costs from overheads by the ticked subtypes", () => {
    const r = row("S1", 1000, { PAYROLL: 600, TRAVEL: 50, RENT: 100 })
    expect(direct(r, new Set(["PAYROLL"]))).toBe(600)
    const s = split(r, new Set(["PAYROLL", "TRAVEL"]))
    expect(s).toMatchObject({ income: 1000, direct: 650, contribution: 350, overhead: 100, profit: 250, margin: 25 })
  })
  it("has no margin without income", () => {
    expect(split(row("S", 0, { PAYROLL: 50 }), new Set()).margin).toBeNull()
    expect(marginText(null)).toBe("—")
    expect(marginText(12.34)).toBe("12.3%")
  })
  it("lists loss-makers worst first", () => {
    const rows = [row("A", 100, { X: 150 }), row("B", 100, { X: 50 }), row("C", 100, { X: 300 })]
    expect(lossMakers(rows).map(r => r.code)).toEqual(["C", "A"])
  })
  it("shows zero as a dash", () => { expect(cell(0)).toBe("—") })
  it("names subtypes", () => {
    expect(subtypeLabel("PAYROLL")).toBe("Wages and salaries")
    expect(subtypeLabel("SOME_NEW_TYPE")).toBe("Some new type")
  })
  it("checks a rule", () => {
    expect(ruleProblem("", "5140", "", "", "c")).toMatch(/Name/)
    expect(ruleProblem("x", "", "", "", "c")).toMatch(/condition/)
    expect(ruleProblem("x", "", "ab", "", "c")).toMatch(/3 characters/)
    expect(ruleProblem("x", "5140", "", "", "")).toMatch(/cost centre/)
    expect(ruleProblem("x", "5140", "", "", "c")).toBeNull()
  })
  it("describes a rule", () => {
    expect(ruleSummary({ accountCode: "5140", textContains: "CA 123", entryType: null })).toBe("account 5140 and text contains “CA 123”")
  })
})
