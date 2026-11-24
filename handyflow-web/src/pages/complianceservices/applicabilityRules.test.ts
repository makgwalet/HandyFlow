import { describe, it, expect } from "vitest"
import { decisionProblem, needsApproval, approveNeedsReason, approveProblem, rowNotes, stateLabel } from "./applicabilityRules"

const today = new Date("2026-11-24T10:00:00Z")

describe("decisionProblem", () => {
  it("lets the suggestion be confirmed without a reason", () => {
    expect(decisionProblem({ state: "APPLICABLE", reason: "", reviewDue: "" }, "APPLICABLE", today)).toBeNull()
  })
  it("asks for a reason when it differs", () => {
    expect(decisionProblem({ state: "APPLICABLE", reason: "short", reviewDue: "" }, "PENDING", today)).toMatch(/at least 15/)
    expect(decisionProblem({ state: "APPLICABLE", reason: "Client confirmed in writing", reviewDue: "" }, "PENDING", today)).toBeNull()
  })
  it("needs a review date when removing", () => {
    const why = "Client has no employees, payroll letter"
    expect(decisionProblem({ state: "NOT_APPLICABLE", reason: why, reviewDue: "" }, "APPLICABLE", today)).toMatch(/again/)
    expect(decisionProblem({ state: "NOT_APPLICABLE", reason: why, reviewDue: "2026-11-24" }, "APPLICABLE", today)).toMatch(/future/)
    expect(decisionProblem({ state: "NOT_APPLICABLE", reason: why, reviewDue: "2029-01-01" }, "APPLICABLE", today)).toMatch(/24 months/)
    expect(decisionProblem({ state: "NOT_APPLICABLE", reason: why, reviewDue: "2027-05-01" }, "APPLICABLE", today)).toBeNull()
  })
})

describe("approval", () => {
  it("is needed only to remove something not already suggested as removed", () => {
    expect(needsApproval("NOT_APPLICABLE", "APPLICABLE")).toBe(true)
    expect(needsApproval("NOT_APPLICABLE", "NOT_APPLICABLE")).toBe(false)
    expect(needsApproval("APPLICABLE", "PENDING")).toBe(false)
  })
  it("asks the decider for a reason", () => {
    expect(approveNeedsReason("u1", "u1")).toBe(true)
    expect(approveNeedsReason("u1", "u2")).toBe(false)
    expect(approveProblem(true, "short")).toMatch(/at least 15/)
    expect(approveProblem(true, "Sole practitioner, no one else")).toBeNull()
    expect(approveProblem(false, "")).toBeNull()
  })
})

describe("rowNotes", () => {
  const base = { effectiveState: "APPLICABLE", state: "APPLICABLE", suggestedState: "APPLICABLE", approvalStatus: "NOT_REQUIRED", basedOnUnconfirmed: false, overridden: false, reviewNeeded: false, decidedBy: null }
  it("says nothing when all is in order", () => expect(rowNotes(base)).toEqual([]))
  it("lists the notes in order of importance", () => {
    const n = rowNotes({ ...base, state: "NOT_APPLICABLE", approvalStatus: "AWAITING", overridden: true, reviewNeeded: true, basedOnUnconfirmed: true })
    expect(n).toHaveLength(3)
    expect(n[0]).toMatch(/still counts as applicable/)
  })
  it("mentions an override once it is approved", () => {
    expect(rowNotes({ ...base, state: "NOT_APPLICABLE", suggestedState: "APPLICABLE", approvalStatus: "APPROVED", overridden: true })[0]).toMatch(/differently/)
  })
  it("labels states", () => expect(stateLabel("PENDING")).toBe("Undecided"))
})
