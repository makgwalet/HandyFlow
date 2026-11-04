import { describe, expect, it } from "vitest"
import { lastActualRun, payrollCard, projectedRun } from "./hrDashboard.logic"

const run = (n: string, status: string, payDate: string, net = 1000) => ({ payRunNumber: n, status, payDate, totalNet: net })

describe("hr dashboard payroll", () => {
  it("never treats a draft as actual payroll", () => {
    const runs = [run("PR-10", "DRAFT", "2026-11-01"), run("PR-09", "FINALISED", "2026-10-01")]
    expect(lastActualRun(runs)?.payRunNumber).toBe("PR-09")
  })

  it("counts approved, finalised and old completed runs as actual", () => {
    for (const s of ["APPROVED", "FINALISED", "COMPLETED"]) expect(lastActualRun([run("A", s, "2026-01-01")])).toBeDefined()
  })

  it("ignores cancelled runs", () => {
    expect(lastActualRun([run("A", "CANCELLED", "2026-01-01")])).toBeUndefined()
    expect(projectedRun([run("A", "CANCELLED", "2026-01-01")])).toBeUndefined()
  })

  it("picks the newest actual run by pay date, not list order", () => {
    const runs = [run("OLD", "FINALISED", "2026-06-01"), run("NEW", "FINALISED", "2026-09-01")]
    expect(lastActualRun(runs)?.payRunNumber).toBe("NEW")
  })

  it("labels the card as approved and mentions a newer unapproved run", () => {
    const c = payrollCard([run("PR-10", "PREPARED", "2026-11-01"), run("PR-09", "FINALISED", "2026-10-01")])
    expect(c.label).toBe("Last approved payroll")
    expect(c.run?.payRunNumber).toBe("PR-09")
    expect(c.note).toBe("PR-10 is not approved yet")
  })

  it("with only a draft, says it is projected", () => {
    const c = payrollCard([run("PR-01", "DRAFT", "2026-11-01")])
    expect(c.label).toBe("Payroll (not approved yet)")
    expect(c.note).toContain("draft")
    expect(payrollCard([run("PR-01", "PREPARED", "2026-11-01")]).note).toContain("waiting for approval")
  })

  it("with no runs, shows nothing", () => {
    expect(payrollCard([]).run).toBeUndefined()
  })
})
