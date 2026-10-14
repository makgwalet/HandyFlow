import { describe, it, expect } from "vitest"
import { batchTotal, describePolicy, emptyPolicy, policyBody, policyProblem, selectable } from "./expensePolicy.logic"

describe("expensePolicy.logic", () => {
  it("needs at least one rule", () => {
    expect(policyProblem(emptyPolicy())).toMatch(/at least one/)
    expect(policyProblem({ ...emptyPolicy(), receiptAbove: "0" })).toBeNull()
  })
  it("checks each value", () => {
    expect(policyProblem({ ...emptyPolicy(), maxPerClaim: "0" })).toMatch(/more than zero/)
    expect(policyProblem({ ...emptyPolicy(), receiptAbove: "-1" })).toMatch(/negative/)
    expect(policyProblem({ ...emptyPolicy(), maxAgeDays: "0" })).toMatch(/1 day/)
  })
  it("builds the request, dropping the action when there is no limit", () => {
    expect(policyBody({ category: "MEALS", maxPerClaim: "300", limitAction: "BLOCK", receiptAbove: "", maxAgeDays: "" }))
      .toEqual({ category: "MEALS", maxPerClaim: 300, limitAction: "BLOCK", receiptAbove: null, maxAgeDays: null })
    expect(policyBody({ ...emptyPolicy(), receiptAbove: "50" }).limitAction).toBeNull()
  })
  it("describes a policy in words", () => {
    const text = describePolicy({ maxPerClaim: 300, limitAction: "BLOCK", receiptAbove: 0, maxAgeDays: 60 }).join(" | ")
    expect(text).toMatch(/more is refused/)
    expect(text).toMatch(/every claim/)
    expect(text).toMatch(/older than 60 days/)
    expect(describePolicy({ maxPerClaim: null, limitAction: null, receiptAbove: 50, maxAgeDays: null })[0]).toMatch(/above/)
  })
  it("totals the ticked claims and skips those already batched", () => {
    const claims = [{ claimId: "a", amount: 10.1, inBatch: false }, { claimId: "b", amount: 20.2, inBatch: true }]
    expect(batchTotal(claims, new Set(["a", "b"]))).toBe(30.3)
    expect(selectable(claims)).toBe(1)
  })
})
