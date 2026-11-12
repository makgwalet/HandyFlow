import { describe, it, expect } from "vitest"
import { validateRule, toRuleRequest, runText, emptyRule, fromRule, type Rule } from "./automationOps"

describe("automationOps (6b)", () => {
  it("needs a tank when adding, a positive level and quantity, and a non-negative price", () => {
    expect(validateRule({ ...emptyRule(), below: "2000", qty: "5000" }, true)).toMatch(/Choose the tank/)
    expect(validateRule({ ...emptyRule(), below: "2000", qty: "5000" }, false)).toBeNull()
    expect(validateRule({ ...emptyRule(), tankId: "t", below: "0", qty: "5000" }, true)).toMatch(/level/)
    expect(validateRule({ ...emptyRule(), tankId: "t", below: "2000", qty: "" }, true)).toMatch(/how many litres/)
    expect(validateRule({ ...emptyRule(), tankId: "t", below: "2000", qty: "5000", price: "-1" }, true)).toMatch(/negative/)
    expect(validateRule({ ...emptyRule(), tankId: "t", below: "2000", qty: "5000", price: "21.5" }, true)).toBeNull()
  })
  it("builds the request: the tank only when adding, blank supplier and price as null", () => {
    expect(toRuleRequest({ tankId: "t1", supplierId: "", below: "2000", qty: "5000", price: "", enabled: true }, true))
      .toEqual({ tankId: "t1", supplierId: null, reorderBelowLitres: 2000, orderLitres: 5000, estimatedPrice: null, enabled: true })
    expect(toRuleRequest({ tankId: "t1", supplierId: "s1", below: "2000", qty: "5000", price: "21.5", enabled: false }, false))
      .toEqual({ tankId: null, supplierId: "s1", reorderBelowLitres: 2000, orderLitres: 5000, estimatedPrice: 21.5, enabled: false })
  })
  it("fills the form from a rule", () => {
    const r: Rule = { id: "r", tankId: "t", tankName: "T1", fuelType: "DIESEL", currentLitres: 10, supplierId: null, supplierName: null, reorderBelowLitres: 2000, orderLitres: 5000, estimatedPrice: null, enabled: false, belowLevel: true, lastCheckedAt: null, lastDraftedAt: null }
    expect(fromRule(r)).toEqual({ tankId: "t", supplierId: "", below: "2000", qty: "5000", price: "", enabled: false })
  })
  it("says plainly what a run did", () => {
    expect(runText({ checked: 0, drafted: 0, alreadyOpen: 0, failed: 0, requisitionNumbers: [] })).toMatch(/No rules are switched on/)
    expect(runText({ checked: 2, drafted: 1, alreadyOpen: 0, failed: 0, requisitionNumbers: ["REQ-1"] })).toBe("1 requisition drafted (REQ-1). Nothing was submitted or ordered.")
    const t = runText({ checked: 3, drafted: 0, alreadyOpen: 2, failed: 1, requisitionNumbers: [] })
    expect(t).toMatch(/No requisition was drafted/); expect(t).toMatch(/2 tanks are below the level but already have/); expect(t).toMatch(/1 rule could not run/)
  })
})
