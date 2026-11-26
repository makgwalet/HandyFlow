import { describe, it, expect } from "vitest"
import { emptyPrice, feeName, lineText, money, priceBody, priceProblem, stateText, suggestionText, vatText, withdrawProblem } from "./priceRules"

const ok = () => ({ ...emptyPrice("2020-03-01"), feeType: "TUITION", amount: "1500.50", includesVat: "yes", sourceNote: "Price list of 2020 as approved" })

describe("priceProblem", () => {
  it("accepts a complete fee", () => expect(priceProblem(ok())).toBeNull())
  it("checks each field", () => {
    expect(priceProblem({ ...ok(), feeType: "" })).toBe("Choose the kind of fee")
    expect(priceProblem({ ...ok(), feeType: "OTHER" })).toBe("Say what the fee is for")
    expect(priceProblem({ ...ok(), amount: "0" })).toBe("The amount must be more than 0")
    expect(priceProblem({ ...ok(), amount: "10.005" })).toBe("The amount can have at most two decimal places")
    expect(priceProblem({ ...ok(), includesVat: "" })).toBe("Say whether the amount includes VAT")
    expect(priceProblem({ ...ok(), validFrom: "" })).toBe("Give the day the fee starts")
    expect(priceProblem({ ...ok(), validTo: "2020-02-01" })).toBe("The end date cannot be before the start date")
    expect(priceProblem({ ...ok(), sourceNote: "short" })).toContain("at least 10")
  })
  it("sends numbers, booleans and nulls", () => {
    expect(priceBody("v1", ok())).toEqual({ programmeVersionId: "v1", funderId: null, feeType: "TUITION", description: null, amount: 1500.5, includesVat: true, validFrom: "2020-03-01", validTo: null, sourceNote: "Price list of 2020 as approved" })
  })
})
describe("wording", () => {
  it("formats money and VAT plainly", () => {
    expect(money(1500.5)).toContain("1")
    expect(money(1500.5)).toMatch(/50$/)
    expect(vatText(true)).toBe("including VAT")
    expect(vatText(false)).toBe("excluding VAT")
  })
  it("names a fee and describes a line", () => {
    expect(feeName({ feeType: "OTHER", description: "Exam resit" })).toBe("Exam resit")
    expect(feeName({ feeType: "TUITION", description: null })).toBe("Tuition")
    expect(lineText({ feeType: "TUITION", description: null, amount: 100, includesVat: false, funderName: null, validFrom: "2020-03-01", validTo: null })).toContain("any funder · from 2020-03-01, no end date")
    expect(suggestionText({ priceLineId: "p", feeType: "TUITION", description: null, amount: 100, includesVat: true, general: false, funderName: "Skills fund", sourceNote: "x" })).toContain("(Skills fund)")
  })
  it("names the state and needs a reason to withdraw", () => {
    expect(stateText({ status: "ACTIVE", inForceToday: true, validFrom: "2020-03-01" }, "2020-03-05")).toBe("In force")
    expect(stateText({ status: "ACTIVE", inForceToday: false, validFrom: "2020-04-01" }, "2020-03-05")).toBe("Not started")
    expect(stateText({ status: "ACTIVE", inForceToday: false, validFrom: "2020-01-01" }, "2020-03-05")).toBe("Ended")
    expect(withdrawProblem("short")).toContain("at least 10")
    expect(withdrawProblem("Replaced by the new price list")).toBeNull()
  })
})
