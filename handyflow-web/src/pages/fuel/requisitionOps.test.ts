import { describe, it, expect } from "vitest"
import { validateRequisition, toRequisitionRequest, actionsFor, canSubmit, validateConvert, emptyReq, fromRequisition, type Requisition } from "./requisitionOps"

const req = (o: Partial<Requisition> = {}): Requisition => ({ id: "r1", reqNumber: "REQ-2026-00001", status: "DRAFT", fuelType: "DIESEL", litres: 10000, tankId: null, supplierId: null, supplierName: null,
  estimatedPrice: 20, estimatedValue: 200000, neededBy: null, reason: "Tank 2 low", requestedBy: "alice", approvalNote: null, purchaseOrderId: null, purchaseOrderNumber: null, cancelReason: null, createdAt: "2026-11-12T08:00:00Z", ...o })

describe("requisitionOps (B05)", () => {
  it("checks a requisition before it is sent", () => {
    const ok = { ...emptyReq(), litres: "10000", reason: "Tank 2 is low" }
    expect(validateRequisition({ ...ok, litres: "" })).toMatch(/Litres/)
    expect(validateRequisition({ ...ok, litres: "0" })).toMatch(/Litres/)
    expect(validateRequisition({ ...ok, estimatedPrice: "-1" })).toMatch(/negative/)
    expect(validateRequisition({ ...ok, reason: "no" })).toMatch(/why/i)
    expect(validateRequisition(ok)).toBeNull()
  })
  it("sends numbers as numbers and blanks as null", () => {
    expect(toRequisitionRequest({ ...emptyReq(), litres: "500", estimatedPrice: "", reason: " Needed now " })).toEqual({ fuelType: "DIESEL", litres: 500, tankId: null, supplierId: null, estimatedPrice: null, neededBy: null, reason: "Needed now" })
    expect(fromRequisition(req({ supplierId: "s1", neededBy: "2026-11-20" }))).toMatchObject({ litres: "10000", estimatedPrice: "20", supplierId: "s1", neededBy: "2026-11-20" })
  })
  it("offers only what the status allows", () => {
    expect(actionsFor(req())).toEqual(["edit", "submit", "cancel"])
    expect(actionsFor(req({ status: "PENDING_APPROVAL" }))).toEqual(["cancel"])
    expect(actionsFor(req({ status: "APPROVED" }))).toEqual(["convert", "cancel"])
    expect(actionsFor(req({ status: "CONVERTED" }))).toEqual([]); expect(actionsFor(req({ status: "CANCELLED" }))).toEqual([])
  })
  it("will not submit without an estimated price", () => { expect(canSubmit(req())).toBe(true); expect(canSubmit(req({ estimatedPrice: null }))).toBe(false) })
  it("needs a supplier and a price to raise an order, from the requisition or given", () => {
    expect(validateConvert(req(), "", "")).toMatch(/supplier/)
    expect(validateConvert(req({ supplierId: "s1" }), "", "")).toBeNull()
    expect(validateConvert(req({ supplierId: "s1", estimatedPrice: null }), "", "")).toMatch(/price/)
    expect(validateConvert(req({ estimatedPrice: null }), "s2", "19")).toBeNull()
    expect(validateConvert(req({ supplierId: "s1" }), "", "-2")).toMatch(/negative/)
  })
})
