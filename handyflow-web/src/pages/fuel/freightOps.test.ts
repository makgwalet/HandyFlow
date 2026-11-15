import { describe, it, expect } from "vitest"
import { validateFreight, adjustmentText, type FreightAdjustment } from "./freightOps"

const adj = (o: Partial<FreightAdjustment> = {}): FreightAdjustment => ({ id: "a", amount: 1000, note: "inv 1", litresInTank: 2000, absorbedAmount: 400, unabsorbedAmount: 600, costBefore: 20, costAfter: 20.2, createdAt: "2026-11-15T10:00:00Z", ...o })

describe("freightOps", () => {
  it("needs a positive amount and a note", () => {
    expect(validateFreight("", "inv")).toMatch(/more than zero/)
    expect(validateFreight("0", "inv")).toMatch(/more than zero/)
    expect(validateFreight("-5", "inv")).toMatch(/more than zero/)
    expect(validateFreight("abc", "inv")).toMatch(/more than zero/)
    expect(validateFreight("100", "  ")).toMatch(/what the freight is for/)
    expect(validateFreight("100", "x".repeat(301))).toMatch(/300/)
    expect(validateFreight("100.50", "Carrier inv 88")).toBeNull()
  })
  it("says in words what happened to the tank's cost", () => {
    expect(adjustmentText(adj())).toMatch(/400[.,]00 went into the tank's cost; .*600[.,]00 belongs to fuel already sold/)
    expect(adjustmentText(adj({ absorbedAmount: 1000, unabsorbedAmount: 0 }))).toMatch(/^All /)
    expect(adjustmentText(adj({ absorbedAmount: 0, unabsorbedAmount: 1000 }))).toMatch(/holds none of this fuel/)
  })
})
