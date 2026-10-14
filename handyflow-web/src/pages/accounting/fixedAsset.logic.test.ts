import { describe, it, expect } from "vitest"
import { emptyForm, formProblem, toRequest, suggestAccount, lifeText, monthName, type Account } from "./fixedAsset.logic"

const ok = () => ({ ...emptyForm(), name: "Bakkie", acquiredOn: "2026-01-10", cost: "300000", residualValue: "60000",
  costAccountId: "a", accumulatedAccountId: "b", expenseAccountId: "c" })

describe("fixedAsset.logic", () => {
  it("accepts a complete straight-line form", () => { expect(formProblem(ok())).toBeNull() })
  it("explains what is missing", () => {
    expect(formProblem({ ...ok(), name: " " })).toMatch(/name/)
    expect(formProblem({ ...ok(), acquiredOn: "" })).toMatch(/date/)
    expect(formProblem({ ...ok(), cost: "0" })).toMatch(/Cost/)
    expect(formProblem({ ...ok(), residualValue: "300000" })).toMatch(/less than the cost/)
    expect(formProblem({ ...ok(), costAccountId: "" })).toMatch(/accounts/)
    expect(formProblem({ ...ok(), lifeMonths: "0" })).toMatch(/Useful life/)
    expect(formProblem({ ...ok(), method: "REDUCING_BALANCE", ratePercent: "" })).toMatch(/rate/)
    expect(formProblem({ ...ok(), method: "REDUCING_BALANCE", ratePercent: "25" })).toBeNull()
  })
  it("sends life for straight line and rate for reducing balance, never both", () => {
    expect(toRequest(ok())).toMatchObject({ method: "STRAIGHT_LINE", lifeMonths: 60, ratePercent: null, cost: 300000 })
    expect(toRequest({ ...ok(), method: "REDUCING_BALANCE", ratePercent: "25" })).toMatchObject({ lifeMonths: null, ratePercent: 25 })
  })
  it("suggests accounts by subtype", () => {
    const accts: Account[] = [
      { id: "1", accountCode: "1520", accountName: "Vehicles", accountType: "ASSET", accountSubtype: "FIXED_ASSET" },
      { id: "2", accountCode: "1590", accountName: "Acc dep", accountType: "ASSET", accountSubtype: "DEPRECIATION" },
      { id: "3", accountCode: "5220", accountName: "Dep", accountType: "EXPENSE", accountSubtype: "DEPRECIATION" }]
    expect(suggestAccount(accts, "cost")).toBe("1")
    expect(suggestAccount(accts, "accumulated")).toBe("2")
    expect(suggestAccount(accts, "expense")).toBe("3")
    expect(suggestAccount([], "cost")).toBe("")
  })
  it("words the life", () => {
    expect(lifeText({ method: "STRAIGHT_LINE", lifeMonths: 66, ratePercent: null })).toBe("5 yr 6 mo")
    expect(lifeText({ method: "REDUCING_BALANCE", lifeMonths: null, ratePercent: 25 })).toBe("25% a year")
  })
  it("names the month", () => { expect(monthName("2026-10-01")).toMatch(/2026/) })
})
