import { describe, it, expect } from "vitest"
import { adjustMessage, canDecide, canWithdraw, controlsToForm, toControlsRequest, validateControls, type AdjustmentRequest } from "./wholesaleOps"

const req = (o: Partial<AdjustmentRequest> = {}): AdjustmentRequest => ({ id: "r1", tankId: "t1", tankName: "D1", litres: -800, reason: "Loss", status: "PENDING", requestedBy: "alice", requestedAt: "2026-11-11T10:00:00Z", ...o })

describe("wholesaleOps", () => {
  it("blank threshold means no limit; otherwise it must be above zero", () => {
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "5", separateCloser: false })).toBeNull()
    expect(validateControls({ requireSupplierApproval: false, threshold: "0", variance: "", cashTol: "5", separateCloser: false })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "abc", variance: "", cashTol: "5", separateCloser: false })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "500", variance: "0.5", cashTol: "5", separateCloser: false })).toBeNull()
  })
  it("round-trips the controls", () => {
    expect(controlsToForm({ requireSupplierApproval: true, adjustmentThresholdLitres: null })).toEqual({ requireSupplierApproval: true, threshold: "", variance: "", cashTol: "5", separateCloser: false })
    expect(toControlsRequest({ requireSupplierApproval: true, threshold: " 750 ", variance: "0.5", cashTol: "5", separateCloser: false })).toEqual({ requireSupplierApproval: true, adjustmentThresholdLitres: 750, varianceTolerancePct: 0.5, cashVarianceTolerance: 5, requireSeparateCloser: false })
    expect(toControlsRequest({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "5", separateCloser: false }).adjustmentThresholdLitres).toBeNull()
  })
  it("rejects a variance tolerance outside 0 to 100", () => {
    for (const v of ["-1", "101", "x"]) expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: v, cashTol: "5", separateCloser: false })).toMatch(/0 and 100/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "0", cashTol: "5", separateCloser: false })).toBeNull()
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
  it("cash tolerance and separate-closer round-trip, and a bad tolerance is refused", () => {
    expect(controlsToForm({ requireSupplierApproval: false, adjustmentThresholdLitres: null, cashVarianceTolerance: 12.5, requireSeparateCloser: true }).cashTol).toBe("12.5")
    expect(toControlsRequest({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "12.5", separateCloser: true })).toMatchObject({ cashVarianceTolerance: 12.5, requireSeparateCloser: true })
    for (const v of ["", "-1", "x"]) expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: v, separateCloser: false })).toMatch(/cash tolerance/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "0", separateCloser: false })).toBeNull()
  })
})
