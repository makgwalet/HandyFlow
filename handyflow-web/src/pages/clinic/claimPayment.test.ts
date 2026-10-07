import { describe, it, expect } from "vitest"
import { parseAmount, partialAmountProblem } from "./claimPayment"

describe("parseAmount", () => {
  it("reads rand amounts with a point or comma", () => {
    expect(parseAmount("300")).toBe(300)
    expect(parseAmount(" 300.5 ")).toBe(300.5)
    expect(parseAmount("300,50")).toBe(300.5)
  })
  it("rejects anything else", () => {
    for (const t of ["", "abc", "1.234", "-5", "1e3", "R300"]) expect(parseAmount(t)).toBeNull()
  })
})

describe("partialAmountProblem", () => {
  it("needs an amount", () => expect(partialAmountProblem("", 520)).toMatch(/Enter the amount/))
  it("needs a number", () => expect(partialAmountProblem("lots", 520)).toMatch(/like 300/))
  it("must be above zero", () => expect(partialAmountProblem("0", 520)).toMatch(/more than zero/))
  it("must be below the total", () => {
    expect(partialAmountProblem("520", 520)).toMatch(/less than the claim total/)
    expect(partialAmountProblem("600", 520)).toMatch(/less than the claim total/)
  })
  it("accepts an amount between zero and the total", () => {
    expect(partialAmountProblem("300", 520)).toBeNull()
    expect(partialAmountProblem("519.99", 520)).toBeNull()
  })
})
