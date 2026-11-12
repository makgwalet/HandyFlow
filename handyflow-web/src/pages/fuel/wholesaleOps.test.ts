import { describe, it, expect } from "vitest"
import { adjustMessage, canDecide, canWithdraw, controlsToForm, toControlsRequest, validateControls, type AdjustmentRequest } from "./wholesaleOps"

const req = (o: Partial<AdjustmentRequest> = {}): AdjustmentRequest => ({ id: "r1", tankId: "t1", tankName: "D1", litres: -800, reason: "Loss", status: "PENDING", requestedBy: "alice", requestedAt: "2026-11-11T10:00:00Z", ...o })

describe("wholesaleOps", () => {
  it("blank threshold means no limit; otherwise it must be above zero", () => {
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toBeNull()
    expect(validateControls({ requireSupplierApproval: false, threshold: "0", variance: "", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "abc", variance: "", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toMatch(/above 0/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "500", variance: "0.5", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toBeNull()
  })
  it("round-trips the controls", () => {
    expect(controlsToForm({ requireSupplierApproval: true, adjustmentThresholdLitres: null })).toEqual({ requireSupplierApproval: true, threshold: "", variance: "", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })
    expect(toControlsRequest({ requireSupplierApproval: true, threshold: " 750 ", variance: "0.5", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toEqual({ requireSupplierApproval: true, adjustmentThresholdLitres: 750, varianceTolerancePct: 0.5, cashVarianceTolerance: 5, requireSeparateCloser: false, priceChangeApprovalPct: 0, minMarginPct: 0, licenceBlockingActivities: [] })
    expect(toControlsRequest({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] }).adjustmentThresholdLitres).toBeNull()
  })
  it("rejects a variance tolerance outside 0 to 100", () => {
    for (const v of ["-1", "101", "x"]) expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: v, cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toMatch(/0 and 100/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "0", cashTol: "5", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toBeNull()
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
    expect(toControlsRequest({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "12.5", separateCloser: true, priceChange: "", minMargin: "", licenceBlocking: [] })).toMatchObject({ cashVarianceTolerance: 12.5, requireSeparateCloser: true })
    for (const v of ["", "-1", "x"]) expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: v, separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toMatch(/cash tolerance/)
    expect(validateControls({ requireSupplierApproval: false, threshold: "", variance: "", cashTol: "0", separateCloser: false, priceChange: "", minMargin: "", licenceBlocking: [] })).toBeNull()
  })

  it("price controls: blank sends 0 (off), values round-trip, bad values are refused", () => {
    const base = { requireSupplierApproval: false, threshold: "", variance: "", cashTol: "5", separateCloser: false, licenceBlocking: [] as string[] }
    expect(toControlsRequest({ ...base, priceChange: "", minMargin: "" })).toMatchObject({ priceChangeApprovalPct: 0, minMarginPct: 0, licenceBlockingActivities: [] })
    expect(toControlsRequest({ ...base, priceChange: "7.5", minMargin: "12" })).toMatchObject({ priceChangeApprovalPct: 7.5, minMarginPct: 12 })
    expect(controlsToForm({ requireSupplierApproval: false, adjustmentThresholdLitres: null, priceChangeApprovalPct: 7.5, minMarginPct: 12 })).toMatchObject({ priceChange: "7.5", minMargin: "12" })
    expect(validateControls({ ...base, priceChange: "-1", minMargin: "" })).toMatch(/price-change/)
    expect(validateControls({ ...base, priceChange: "", minMargin: "100" })).toMatch(/margin/)
    expect(validateControls({ ...base, priceChange: "5", minMargin: "10" })).toBeNull()
  })
})
