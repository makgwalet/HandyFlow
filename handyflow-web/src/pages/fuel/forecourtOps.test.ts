import { describe, it, expect } from "vitest"
import { validateNozzle, nozzleFields, nozzleLabel, validatePump, validateOpen, validateClose, pumpTotals, expectedTotals, takings, needsReason, varianceText, validateAdjustment, pendingAdjustments, approvedAdjustmentTotal, canDecideAdjustment, canWithdrawAdjustment, awaitingReview, type CloseRow, type ShiftAdjustment, type Shift, providerTimesText } from "./forecourtOps"

const row = (o: Partial<CloseRow> = {}): CloseRow => ({ pumpId: "p1", name: "Pump 1", opening: 1000, price: 20, closing: "1210", test: "10", ...o })

describe("forecourtOps", () => {
  it("works out litres net of test litres and the value at the shift price", () => {
    expect(pumpTotals(row())).toEqual({ litres: 200, value: 4000 })
    expect(pumpTotals(row({ closing: "999" }))).toBeNull()
    expect(pumpTotals(row({ test: "999" }))).toBeNull()
    expect(expectedTotals([row(), row({ pumpId: "p2", opening: 5000, closing: "5100", test: "" })])).toEqual({ litres: 300, value: 6000 })
  })
  it("adds the tenders, ignoring blanks", () => {
    expect(takings("4000", "", "1999.5", "0.5")).toBe(6000)
  })
  it("needs a reason only beyond R5, and words the variance", () => {
    expect(needsReason(-5)).toBe(false)
    expect(needsReason(5.01)).toBe(true)
    expect(varianceText(-12.5)).toMatch(/short/)
    expect(varianceText(3)).toMatch(/over/)
    expect(varianceText(0)).toBe("Balanced")
  })
  it("validates a pump", () => {
    expect(validatePump("", "t", "20")).toMatch(/name/)
    expect(validatePump("P1", "", "20")).toMatch(/tank/)
    expect(validatePump("P1", "t", "0")).toMatch(/greater than zero/)
    expect(validatePump("P1", "t", "21.5")).toBeNull()
  })
  it("validates opening a shift", () => {
    expect(validateOpen("", [{ pumpId: "p", meter: "1", name: "P" }])).toMatch(/attendant/)
    expect(validateOpen("A", [])).toMatch(/at least one/)
    expect(validateOpen("A", [{ pumpId: "p", meter: "", name: "Pump 1" }])).toMatch(/Pump 1/)
    expect(validateOpen("A", [{ pumpId: "p", meter: "0", name: "P" }])).toBeNull()
  })
  it("validates closing: meters, test litres and the reason rule", () => {
    const ok = { cash: "4000", card: "0", account: "0", other: "0" }
    expect(validateClose([row({ closing: "" })], ok, "")).toMatch(/closing meter/)
    expect(validateClose([row({ closing: "900" })], ok, "")).toMatch(/below the opening/)
    expect(validateClose([row({ test: "500" })], ok, "")).toMatch(/more than the meter moved/)
    expect(validateClose([row()], { ...ok, cash: "-1" }, "")).toMatch(/zero or more/)
    expect(validateClose([row()], ok, "")).toBeNull()
    expect(validateClose([row()], { ...ok, cash: "3000" }, "")).toMatch(/Give a reason/)
    expect(validateClose([row()], { ...ok, cash: "3000" }, "Drive-off reported")).toBeNull()
  })

  it("fleet-card takings count, and the company tolerance replaces R5", () => {
    expect(takings("1000", "", "", "", "500")).toBe(1500)
    expect(needsReason(-20)).toBe(true)
    expect(needsReason(-20, 50)).toBe(false)
    expect(needsReason(-50, 50)).toBe(false)
    const ok = { cash: "3980", card: "0", account: "0", other: "0" }
    expect(validateClose([row()], ok, "")).toMatch(/Give a reason/)
    expect(validateClose([row()], ok, "", { tolerance: 50 })).toBeNull()
    expect(validateClose([row()], { ...ok, cash: "2000", fleetCard: "2000" }, "")).toBeNull()
  })
  it("approved adjustments lower what the shift is expected to hold", () => {
    expect(validateClose([row()], { cash: "3900", card: "0", account: "0", other: "0" }, "", { adjustments: 100 })).toBeNull()
    expect(validateClose([row()], { cash: "3900", card: "0", account: "0", other: "0" }, "")).toMatch(/Give a reason/)
  })
  const adj = (o: Partial<ShiftAdjustment> = {}): ShiftAdjustment => ({ id: "a1", shiftId: "s1", kind: "REFUND", amount: 50, reason: "Overfill", status: "PENDING", recordedBy: "alice", recordedAt: "2026-11-11T08:00:00Z", ...o })
  it("validates a refund, void or discount", () => {
    expect(validateAdjustment("", "Overfill")).toMatch(/above R 0/)
    expect(validateAdjustment("0", "Overfill")).toMatch(/above R 0/)
    expect(validateAdjustment("50", "no")).toMatch(/5 characters/)
    expect(validateAdjustment("50", "Overfill")).toBeNull()
  })
  it("counts pending and approved entries; four-eyes in the UI", () => {
    const l = [adj(), adj({ id: "a2", status: "APPROVED", amount: 25.5 }), adj({ id: "a3", status: "APPROVED", amount: 10 }), adj({ id: "a4", status: "REJECTED" })]
    expect(pendingAdjustments(l)).toBe(1)
    expect(approvedAdjustmentTotal(l)).toBe(35.5)
    expect(canDecideAdjustment(adj(), "bob")).toBe(true)
    expect(canDecideAdjustment(adj(), "alice")).toBe(false)
    expect(canDecideAdjustment(adj({ status: "APPROVED" }), "bob")).toBe(false)
    expect(canWithdrawAdjustment(adj(), "alice")).toBe(true)
    expect(canWithdrawAdjustment(adj(), "bob")).toBe(false)
  })
  it("counts closed shifts waiting for review", () => {
    const sh = (o: Partial<Shift>) => ({ status: "CLOSED", reviewStatus: "NONE", ...o }) as Shift
    expect(awaitingReview([sh({ reviewStatus: "PENDING_REVIEW" }), sh({}), sh({ status: "REVERSED", reviewStatus: "PENDING_REVIEW" })])).toBe(1)
  })

  it("D01 nozzle grouping: a number needs a dispenser and 1 to 20; ungrouped pumps send nothing; clearing sends an empty label", () => {
    expect(validateNozzle("", "")).toBeNull()
    expect(validateNozzle("Island 1", "")).toBeNull()
    expect(validateNozzle("", "2")).toMatch(/dispenser/)
    expect(validateNozzle("Island 1", "0")).toMatch(/1 to 20/)
    expect(validateNozzle("Island 1", "2.5")).toMatch(/1 to 20/)
    expect(validateNozzle("Island 1", "3")).toBeNull()
    expect(nozzleFields("", "", false)).toEqual({})
    expect(nozzleFields("", "", true)).toEqual({ dispenserLabel: "" })
    expect(nozzleFields(" Island 1 ", "2", false)).toEqual({ dispenserLabel: "Island 1", nozzleNumber: 2 })
    expect(nozzleFields("Island 1", "", true)).toEqual({ dispenserLabel: "Island 1" })
    expect(nozzleLabel({ dispenserLabel: "Island 1", nozzleNumber: 2 })).toBe("Island 1, nozzle 2")
    expect(nozzleLabel({ dispenserLabel: null, nozzleNumber: null })).toBe("–")
  })
})

describe("providerTimesText", () => {
  it("is empty for a shift that was not imported", () => {
    expect(providerTimesText({})).toBeNull()
    expect(providerTimesText({ providerOpenedAt: null, providerClosedAt: null })).toBeNull()
  })
  it("names whichever provider times exist", () => {
    expect(providerTimesText({ providerOpenedAt: "2026-11-12T06:00:00Z" })).toMatch(/^Provider times: opened /)
    expect(providerTimesText({ providerOpenedAt: "2026-11-12T06:00:00Z", providerClosedAt: "2026-11-12T14:00:00Z" })).toMatch(/opened .*, closed /)
  })
})
