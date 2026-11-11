import { describe, it, expect } from "vitest"
import { invoiceTotal, validateCapture, validateDecision, needsReason, type CaptureForm } from "./supplierInvoiceOps"

const ok: CaptureForm = { supplierId: "s1", invoiceNumber: "INV-1", invoiceDate: "2026-11-10", dueDate: "", litres: "10000", price: "20", other: "", vat: "" }

describe("supplierInvoiceOps", () => {
  it("totals litres x price plus charges and VAT", () => {
    expect(invoiceTotal(ok)).toBe(200000)
    expect(invoiceTotal({ ...ok, other: "500", vat: "30075" })).toBe(230575)
    expect(invoiceTotal({ ...ok, litres: "" })).toBeNull()
  })
  it("validates the capture form", () => {
    expect(validateCapture({ ...ok, supplierId: "" })).toMatch(/supplier/)
    expect(validateCapture({ ...ok, invoiceNumber: " " })).toMatch(/invoice number/)
    expect(validateCapture({ ...ok, dueDate: "2026-11-01" })).toMatch(/due date/)
    expect(validateCapture({ ...ok, litres: "0" })).toMatch(/more than zero/)
    expect(validateCapture({ ...ok, price: "" })).toMatch(/price/)
    expect(validateCapture({ ...ok, vat: "-1" })).toMatch(/VAT/)
    expect(validateCapture(ok)).toBeNull()
  })
  it("needs a reason for anything but a clean match, and always for a rejection", () => {
    expect(needsReason("MATCHED")).toBe(false)
    expect(needsReason("VARIANCE")).toBe(true)
    expect(needsReason("UNMATCHED")).toBe(true)
    expect(validateDecision("ok", true)).toMatch(/5 characters/)
    expect(validateDecision("", false)).toBeNull()
  })
})
