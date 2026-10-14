import { describe, it, expect } from "vitest"
import { emptyStrategy, formFromStrategy, segmentText, strategyProblem, strategyRequest } from "./strategies.logic"

describe("strategy form", () => {
  it("needs a name and steps on different days", () => {
    const f = emptyStrategy()
    expect(strategyProblem(f)).toMatch(/name/)
    expect(strategyProblem({ ...f, name: "Small debts" })).toBeNull()
    expect(strategyProblem({ ...f, name: "x", steps: [] })).toMatch(/at least one step/)
    expect(strategyProblem({ ...f, name: "x", steps: [{ afterDays: "7", action: "CALL", instruction: "" }, { afterDays: "7", action: "SEND_REMINDER", instruction: "" }] })).toMatch(/day 7/)
    expect(strategyProblem({ ...f, name: "x", steps: [{ afterDays: "", action: "CALL", instruction: "" }] })).toMatch(/day count/)
  })
  it("checks ranges", () => {
    const f = { ...emptyStrategy(), name: "x" }
    expect(strategyProblem({ ...f, minBalance: "500", maxBalance: "100" })).toMatch(/largest balance/)
    expect(strategyProblem({ ...f, minDays: "30", maxDays: "10" })).toMatch(/most days/)
    expect(strategyProblem({ ...f, quietDays: "40" })).toMatch(/0 to 30/)
    expect(strategyProblem({ ...f, minBalance: "abc" })).toMatch(/smallest balance/)
  })
  it("sorts steps by day and turns blanks into null", () => {
    const r = strategyRequest({ ...emptyStrategy(), name: " Big ", minBalance: "50000", steps: [
      { afterDays: "30", action: "CALL", instruction: " Offer a plan " }, { afterDays: "7", action: "SEND_REMINDER", instruction: "" }] })
    expect(r).toMatchObject({ name: "Big", minBalance: 50000, maxBalance: null, minDays: null, priority: 10 })
    expect(r.steps.map(s => s.afterDays)).toEqual([7, 30])
    expect(r.steps[1].instruction).toBe("Offer a plan")
  })
  it("round-trips a saved strategy and describes who it covers", () => {
    const s = { name: "A", description: null, priority: 2, minBalance: null, maxBalance: 5000, minDays: 30, maxDays: null, quietDays: 3, steps: [{ afterDays: 7, action: "CALL", instruction: null }] }
    expect(formFromStrategy(s).maxBalance).toBe("5000")
    expect(segmentText(s)).toMatch(/balance up to R .*5.?000, 30\+ days overdue/)
    expect(segmentText({ minBalance: null, maxBalance: null, minDays: null, maxDays: null })).toBe("every case")
  })
})
