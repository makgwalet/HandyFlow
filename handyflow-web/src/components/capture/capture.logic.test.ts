import { describe, it, expect } from "vitest"
import { billFields, claimFields, isReadable, type Capture } from "./capture.logic"

const base: Capture = { textFound: true, supplierName: "ACME", documentNumber: "INV-1", documentDate: "2026-10-01", dueDate: "2026-10-31",
  total: 920, vatAmount: 120, supplierVatNumber: null, notes: [] }

describe("capture fields", () => {
  it("splits the total into amount excluding VAT and VAT for a bill", () => {
    expect(billFields(base)).toEqual({ supplierName: "ACME", billNumber: "INV-1", billDate: "2026-10-01", dueDate: "2026-10-31", amount: "800.00", vatAmount: "120.00" })
  })
  it("uses the total when no sensible VAT was read", () => {
    expect(billFields({ ...base, vatAmount: null }).amount).toBe("920.00")
    expect(billFields({ ...base, vatAmount: 920 }).vatAmount).toBeUndefined()
  })
  it("leaves out what was not found", () => {
    expect(billFields({ ...base, supplierName: null, dueDate: null, total: null })).not.toHaveProperty("supplierName")
    expect(claimFields({ ...base, total: null })).toEqual({ claimDate: "2026-10-01", description: "ACME" })
  })
  it("fills a claim with the total", () => {
    expect(claimFields(base).amount).toBe("920.00")
  })
  it("accepts pdf and photos", () => {
    expect(isReadable("a.PDF", "")).toBe(true)
    expect(isReadable("a.jpg", "image/jpeg")).toBe(true)
    expect(isReadable("a.docx", "")).toBe(false)
  })
})
