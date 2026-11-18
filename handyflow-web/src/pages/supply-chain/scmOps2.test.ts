import { describe, it, expect } from "vitest"
import { daysUntil, contractBucket, contractCounts, dueText, returnStep, returnActions, creditProblem, quarantineProblem, replenishmentPicks, replenishmentProblem, deltaPct, deltaText, statusBreakdown, dashboardAlerts } from "./scmOps"

const today = new Date(2026, 10, 18)
describe("contracts", () => {
  it("counts days between calendar dates", () => { expect(daysUntil("2026-11-28", today)).toBe(10); expect(daysUntil("2026-11-17", today)).toBe(-1); expect(daysUntil(null, today)).toBeNull() })
  it("buckets", () => {
    expect(contractBucket({ status: "ACTIVE", endDate: "2027-06-01" }, today)).toBe("ACTIVE")
    expect(contractBucket({ status: "ACTIVE", endDate: "2026-12-31" }, today)).toBe("EXPIRING")
    expect(contractBucket({ status: "ACTIVE", endDate: "2027-06-01", renewalNoticeDue: true }, today)).toBe("EXPIRING")
    expect(contractBucket({ status: "ACTIVE", endDate: "2026-11-01" }, today)).toBe("EXPIRED")
    expect(contractBucket({ status: "DRAFT", endDate: "2026-11-01" }, today)).toBe("DRAFT")
    expect(contractBucket({ status: "TERMINATED", endDate: "2027-01-01" }, today)).toBe("TERMINATED")
  })
  it("counts and words", () => {
    expect(contractCounts([{ status: "ACTIVE", endDate: "2027-06-01" }, { status: "EXPIRED", endDate: "2026-01-01" }], today).EXPIRED).toBe(1)
    expect(dueText(1)).toBe("in 1 day"); expect(dueText(0)).toBe("today"); expect(dueText(-3)).toBe("3 days ago"); expect(dueText(null)).toBe("—")
  })
})
describe("returns", () => {
  it("steps", () => { expect(returnStep("SUBMITTED")).toBe(0); expect(returnStep("APPROVED")).toBe(1); expect(returnStep("CREDITED")).toBe(3); expect(returnStep("REJECTED")).toBe(-1) })
  it("actions respect rights and status", () => {
    expect(returnActions("DRAFT", true, false)).toEqual(["submit", "cancel"])
    expect(returnActions("SUBMITTED", true, false)).toEqual(["cancel"])
    expect(returnActions("SUBMITTED", false, true)).toEqual(["approve", "reject"])
    expect(returnActions("DISPATCHED", true, true)).toEqual(["credit"])
    expect(returnActions("CREDITED", true, true)).toEqual([])
  })
  it("credit validation", () => { expect(creditProblem("", "5")).toMatch(/reference/); expect(creditProblem("CN1", "0")).toMatch(/more than zero/); expect(creditProblem("CN1", "10")).toBeNull() })
})
describe("quarantine", () => {
  it("validates", () => {
    expect(quarantineProblem("release", "", 5, "")).toMatch(/quantity/)
    expect(quarantineProblem("release", "6", 5, "")).toMatch(/Only 5/)
    expect(quarantineProblem("write-off", "2", 5, " ")).toMatch(/reason/)
    expect(quarantineProblem("write-off", "2", 5, "damaged")).toBeNull()
    expect(quarantineProblem("release", "5", 5, "")).toBeNull()
  })
})
describe("replenishment", () => {
  const items = [{ catalogueItemId: "a", suggestedQty: 10 }, { catalogueItemId: "b", suggestedQty: 4 }]
  it("picks ticked items with edits", () => {
    const p = replenishmentPicks(items, { a: true }, { a: "12" })
    expect(p).toEqual([{ catalogueItemId: "a", qty: 12 }]); expect(replenishmentProblem(p)).toBeNull()
  })
  it("refuses none or zero", () => { expect(replenishmentProblem([])).toMatch(/Tick/); expect(replenishmentProblem([{ qty: 0 }])).toMatch(/above zero/) })
})
describe("dashboard", () => {
  it("delta", () => { expect(deltaPct(110, 100)).toBeCloseTo(10); expect(deltaPct(5, 0)).toBeNull(); expect(deltaText(-2.5)).toBe("▼ 2.5%"); expect(deltaText(null)).toBe("—") })
  it("breakdown sorted", () => { expect(statusBreakdown([{ status: "A" }, { status: "B" }, { status: "B" }])).toEqual([{ status: "B", count: 2 }, { status: "A", count: 1 }]) })
  it("alerts only for non-zero, singular/plural", () => {
    const a = dashboardAlerts({ overdueInvoices: 1, priceApprovals: 0, quarantineOpen: 2, contractsExpiring: 0, stockouts: 0, reorders: 3, invoicesForApproval: 0 })
    expect(a.map(x => x.text)).toEqual(["1 supplier invoice overdue", "2 quarantine holds open", "3 items to reorder"])
  })
})
import { lastMonths, monthSeries } from "./scmOps"
describe("trend axis", () => {
  it("lists months across a year boundary", () => { expect(lastMonths(3, new Date(2026, 0, 15))).toEqual(["2025-11", "2025-12", "2026-01"]) })
  it("zero-fills", () => { expect(monthSeries([{ label: "2026-01", amount: 5 }], ["2025-12", "2026-01"])).toEqual([{ month: "2025-12", amount: 0 }, { month: "2026-01", amount: 5 }]) })
})
