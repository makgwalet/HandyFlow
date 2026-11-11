import { describe, it, expect } from "vitest"
import { adjustMessage, canDecide, canWithdraw, controlsToForm, toControlsRequest, validateControls, type AdjustmentRequest } from "./wholesaleOps"

const req = (o: Partial<AdjustmentRequest> = {}): AdjustmentRequest => ({ id: "r1", tankId: "t1", tankName: "D1", litres: -800, reason: "Loss", status: "PENDING", requestedBy: "alice", requestedAt: "2026-11-11T10:00:00Z", ...o })

describe("wholesaleOps", () => {
  it("blank threshold means no limit; otherwise it must be above zero", () => {
    expect(validateControls({ requireSupplierApproval: false, threshold: "" })).toBeNull()
    expect(validateControls({ requireSupplierApproval: false, threshold: "0" })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "abc" })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "500" })).toBeNull()
  })
  it("round-trips the controls", () => {
    expect(controlsToForm({ requireSupplierApproval: true, adjustmentThresholdLitres: null })).toEqual({ requireSupplierApproval: true, threshold: "" })
    expect(toControlsRequest({ requireSupplierApproval: true, threshold: " 750 " })).toEqual({ requireSupplierApproval: true, adjustmentThresholdLitres: 750 })
    expect(toControlsRequest({ requireSupplierApproval: false, threshold: "" }).adjustmentThresholdLitres).toBeNull()
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
