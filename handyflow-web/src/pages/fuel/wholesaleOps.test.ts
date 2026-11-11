import { describe, it, expect } from "vitest"
import { adjustMessage, canDecide, canWithdraw, controlsToForm, toControlsRequest, validateControls, type AdjustmentRequest } from "./wholesaleOps"

const req = (o: Partial<AdjustmentRequest> = {}): AdjustmentRequest => ({ id: "r1", tankId: "t1", tankName: "D1", litres: -800, reason: "Loss", status: "PENDING", requestedBy: "alice", requestedAt: "2026-11-11T10:00:00Z", ...o })

describe("wholesaleOps", () => {
  it("blank threshold means no limit; otherwise it must be above zero", () => {
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "" })).toBeNull()
    expect(validateControls({ requireSupplierApproval: false, threshold: "0", variance: "" })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "abc", variance: "" })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "500", variance: "0.5" })).toBeNull()
  })
  it("round-trips the controls", () => {
    expect(controlsToForm({ requireSupplierApproval: true, adjustmentThresholdLitres: null })).toEqual({ requireSupplierApproval: true, threshold: "", variance: "" })
    expect(toControlsRequest({ requireSupplierApproval: true, threshold: " 750 ", variance: "0.5" })).toEqual({ requireSupplierApproval: true, adjustmentThresholdLitres: 750, varianceTolerancePct: 0.5 })
    expect(toControlsRequest({ requireSupplierApproval: false, threshold: "", variance: "" }).adjustmentThresholdLitres).toBeNull()
  })
  it("rejects a variance tolerance outside 0 to 100", () => {
    for (const v of ["-1", "101", "x"]) expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: v })).toMatch(/0 and 100/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "0" })).toBeNull()
  })
  it("explains a held adjustment and says nothing for a posted one", () => {
    expect(adjustMessage({ pendingApproval: true })).toMatch(/second person/)
    expect(adjustMessage({ pendingApproval: false })).toBeNull()
    expect(adjustMessage(undefined)).toBeNull()
  })
  it("never offers the requester their own approval", () => {
    expect(canDecide(req(), "alice")).toBe(false)
    expect(canDecide(req(), "bob")).toBe(true)
    expect(canDecide(req({ status: "APPROVED" }), "bob")).toBe(false)
    expect(canWithdraw(req(), "alice")).toBe(true)
    expect(canWithdraw(req(), "bob")).toBe(false)
  })
})
