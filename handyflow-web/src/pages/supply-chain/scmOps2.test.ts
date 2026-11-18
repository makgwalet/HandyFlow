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
import { tenderStep, createStepProblem, bestIndex, quoteTotals, technicalScoreProblem, responseProgress, emptyRfq } from "./scmOps"
describe("tenders", () => {
  it("steps", () => { expect(tenderStep("DRAFT", false)).toBe(0); expect(tenderStep("AWARDED", false)).toBe(3); expect(tenderStep("AWARDED", true)).toBe(4); expect(tenderStep("CANCELLED", false)).toBe(-1) })
  it("create steps gate progress", () => {
    const f = emptyRfq()
    expect(createStepProblem(0, f)).toMatch(/title/); f.title = "Tyres"
    expect(createStepProblem(0, f)).toBeNull(); expect(createStepProblem(1, f)).toMatch(/item/)
    f.lines[0].itemName = "Tyre"; expect(createStepProblem(1, f)).toBeNull()
    expect(createStepProblem(2, f)).toMatch(/Invite/); f.supplierIds = ["s"]; expect(createStepProblem(2, f)).toBeNull()
    f.weights.price = 70; expect(createStepProblem(3, f)).toMatch(/120/)
  })
  it("best cell ignores ties and gaps", () => {
    expect(bestIndex([5, 3, 9], "low")).toBe(1); expect(bestIndex([5, 3, 9], "high")).toBe(2)
    expect(bestIndex([3, 3, 9], "low")).toBeNull(); expect(bestIndex([null, 4], "low")).toBe(1); expect(bestIndex([null, null], "low")).toBeNull()
  })
  it("quote totals", () => {
    const t = quoteTotals({ supplierId: "s", quoteReference: "", validUntil: "", leadTimeDays: "5", warrantyMonths: "", paymentTermsDays: "", deliveryCost: "100", otherCharges: "", lines: [
      { rfqLineId: "a", noBid: false, quantity: "10", unitPrice: "100", discountPct: "10" }, { rfqLineId: "b", noBid: true, quantity: "1", unitPrice: "999", discountPct: "" }] })
    expect(t).toEqual({ subtotal: 900, landed: 1000 })
  })
  it("score and progress", () => {
    expect(technicalScoreProblem("")).toMatch(/Enter/); expect(technicalScoreProblem("101")).toMatch(/0 to 100/); expect(technicalScoreProblem("7.5")).toMatch(/whole/); expect(technicalScoreProblem("80")).toBeNull()
    expect(responseProgress([{ status: "RESPONDED" }, { status: "INVITED" }, { status: "DECLINED" }])).toEqual({ answered: 2, total: 3, pct: 67 })
  })
})
import { budgetTotals, allocationBy, budgetVariance, budgetBar, budgetCheckProblem, budgetCheckParams } from "./scmOps"
describe("budgets", () => {
  const b = (o = {}) => ({ id: "1", name: "A", scopeType: "DEPARTMENT", scopeValue: "W", amount: 1000, committed: 400, actual: 200, used: 500, available: 500, utilisationPct: 50, state: "OK", status: "ACTIVE", ...o })
  it("totals skip closed budgets", () => {
    const t = budgetTotals([b(), b({ id: "2", amount: 500, used: 600, state: "EXCEEDED" }), b({ id: "3", status: "CLOSED" })])
    expect(t).toMatchObject({ allocated: 1500, used: 1100, available: 400, exceeded: 1, count: 2 })
  })
  it("allocation shares", () => {
    const g = allocationBy([b({ amount: 300 }), b({ id: "2", amount: 100 }), b({ id: "3", scopeType: "PROJECT", amount: 50 })])
    expect(g[0].scopeType).toBe("DEPARTMENT"); expect(g[0].items[0].sharePct).toBe(75); expect(g[1].allocated).toBe(50)
  })
  it("variance and bar", () => {
    expect(budgetVariance({ amount: 1000, used: 1200 })).toBe(-200)
    expect(budgetBar({ amount: 1000, actual: 200, used: 500 })).toEqual({ actualPct: 20, committedPct: 30, overPct: 0 })
    expect(budgetBar({ amount: 1000, actual: 300, used: 1200 })).toEqual({ actualPct: 30, committedPct: 70, overPct: 20 })
    expect(budgetBar({ amount: 0, actual: 0, used: 5 })).toEqual({ actualPct: 0, committedPct: 0, overPct: 0 })
  })
  it("what-if check", () => {
    expect(budgetCheckProblem({ department: "", costCentre: "", projectRef: "", amount: "5" })).toMatch(/department/)
    expect(budgetCheckProblem({ department: "W", costCentre: "", projectRef: "", amount: "0" })).toMatch(/amount/)
    expect(budgetCheckParams({ department: " W ", costCentre: "", projectRef: "", amount: "5" })).toEqual({ amount: 5, department: "W" })
  })
})
import { inspectionProblem, inspectionBody, inspectionOutcome, emptyReturn, returnProblem, returnBody, returnValue } from "./scmOps"
describe("inspection and returns forms", () => {
  const l = (o = {}) => ({ lineId: "a", itemName: "Valve", accepted: 10, passed: "8", failed: "2", defectCode: "CRACK", notes: "", ...o })
  it("inspection rules", () => {
    expect(inspectionProblem([l({ passed: "", failed: "" })])).toMatch(/at least one/)
    expect(inspectionProblem([l({ passed: "9", failed: "2" })])).toMatch(/more than the 10/)
    expect(inspectionProblem([l({ defectCode: "" })])).toMatch(/what is wrong/)
    expect(inspectionProblem([l({ passed: "-1" })])).toMatch(/negative/)
    expect(inspectionProblem([l()])).toBeNull()
    expect(inspectionBody([l({ defectCode: " " , failed: "0" })], " ").lines[0]).toEqual({ lineId: "a", qtyPassed: 8, qtyFailed: 0, defectCode: null, notes: null })
    expect(inspectionOutcome([l()]).text).toBe("2 of 10 units fail"); expect(inspectionOutcome([l({ failed: "0" })]).text).toMatch(/All/)
  })
  it("return rules and body", () => {
    const f = emptyReturn(); expect(returnProblem(f)).toMatch(/supplier/)
    f.supplierId = "s"; expect(returnProblem(f)).toMatch(/Name the item/)
    f.lines[0].itemName = "Bolts"; expect(returnProblem(f)).toMatch(/unit cost/)
    f.lines[0].unitCost = "10"; expect(returnProblem(f)).toBeNull(); expect(returnValue(f)).toBe(10)
    f.reason = "OTHER"; expect(returnProblem(f)).toMatch(/notes/)
    expect(returnProblem({ ...f, reason: "DAMAGED", lines: [{ grLineId: "g", itemName: "X", qty: "5", unitCost: "", max: 3 }] })).toMatch(/only 3/)
    expect(returnBody({ ...f, reason: "DAMAGED", lines: [{ grLineId: "g", itemName: "X", qty: "2", unitCost: "", max: 3 }] }).lines[0]).toEqual({ grLineId: "g", itemName: null, qty: 2, unitCost: null })
  })
})
import { permissionArea, scmPermissionGroups, effectivePermissions, permissionIds, realEdits, revokeWarning } from "./scmOps"
describe("roles and permissions", () => {
  it("areas and groups", () => {
    expect(permissionArea("SCM_PO_CREATE")).toBe("Po"); expect(permissionArea("SCM_ADMIN")).toBe("General")
    const g = scmPermissionGroups([{ name: "SCM_RFQ_MANAGE" }, { name: "SCM_ADMIN" }, { name: "FUEL_READ" }, { name: "SCM_BUDGET_READ" }, { name: "SCM_BUDGET_MANAGE" }])
    expect(g.map(x => x.area)).toEqual(["General", "Budget", "Rfq"]); expect(g[1].items.map(i => i.name)).toEqual(["SCM_BUDGET_MANAGE", "SCM_BUDGET_READ"])
  })
  it("applies edits without disturbing other permissions", () => {
    expect(effectivePermissions(["A", "SCM_READ"], { SCM_READ: false, SCM_ADMIN: true }).sort()).toEqual(["A", "SCM_ADMIN"])
    expect(permissionIds(["A", "X"], { A: "1" })).toEqual(["1"])
    expect(realEdits(["SCM_READ"], { SCM_READ: true, SCM_ADMIN: false, SCM_PO_CREATE: true })).toEqual({ SCM_PO_CREATE: true })
  })
  it("warns when admin is revoked", () => {
    expect(revokeWarning("Buyer", { SCM_ADMIN: false }, 2)).toMatch(/2 users in Buyer/); expect(revokeWarning("Buyer", { SCM_ADMIN: false }, 0)).toBeNull(); expect(revokeWarning("Buyer", { SCM_READ: false }, 3)).toBeNull()
  })
})
