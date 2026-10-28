import { describe, it, expect } from "vitest"
import { BANK_LAYOUTS, OUTCOME_TEXT, applyText, layoutFilename, layoutMime, returnFileProblem, type ReturnPreview } from "./payRunPayments.logic"
import { ACCOUNT_TYPES } from "./employees.logic"

const preview = (matched: number): ReturnPreview => ({ rows: 3, matched, lines: [] })

describe("bank return import", () => {
  it("says what applying will do, or nothing when nothing matched", () => {
    expect(applyText(null)).toBeNull()
    expect(applyText(preview(0))).toBeNull()
    expect(applyText(preview(1))).toBe("Mark 1 payment as returned")
    expect(applyText(preview(3))).toBe("Mark 3 payments as returned")
  })
  it("checks the chosen file", () => {
    expect(returnFileProblem(null)).toMatch(/Choose/)
    expect(returnFileProblem({ name: "x.csv", size: 0 })).toMatch(/empty/)
    expect(returnFileProblem({ name: "x.csv", size: 2_000_000 })).toMatch(/too large/)
    expect(returnFileProblem({ name: "x.pdf", size: 10 })).toMatch(/CSV or text/)
    expect(returnFileProblem({ name: "Unpaid.CSV", size: 10 })).toBeNull()
  })
  it("explains every outcome", () => {
    for (const k of ["MATCHED", "AMBIGUOUS", "UNMATCHED", "DUPLICATE", "ALREADY_RETURNED"] as const) expect(OUTCOME_TEXT[k].length > 5).toBe(true)
  })
})

describe("bank layouts", () => {
  it("offers ABSA as a CSV and BankServ as text", () => {
    expect(BANK_LAYOUTS.map(l => l.value)).toEqual(["GENERIC_CSV", "BANKSERV_ACB", "ABSA_BIO"])
    expect(layoutFilename("ABSA_BIO", "PR-00005")).toBe("salary-payments-PR-00005.csv")
    expect(layoutFilename("BANKSERV_ACB", "PR-00005")).toBe("salary-payments-PR-00005.txt")
    expect(layoutMime("ABSA_BIO")).toBe("text/csv")
    expect(layoutMime("BANKSERV_ACB")).toBe("text/plain")
  })
  it("lists the four account types the server knows", () => {
    expect(ACCOUNT_TYPES.map(t => t.value)).toEqual(["CURRENT", "SAVINGS", "TRANSMISSION", "BOND"])
  })
})
