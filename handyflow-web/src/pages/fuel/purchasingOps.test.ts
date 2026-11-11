import { describe, it, expect } from "vitest"
import { validatePo, validateCloseReason, STATUS_LABEL, litresVarianceLabel } from "./purchasingOps"

describe("purchasingOps", () => {
  it("validates a purchase order", () => {
    expect(validatePo("", "DIESEL", "10", "1")).toMatch(/supplier/)
    expect(validatePo("s", "", "10", "1")).toMatch(/fuel/)
    expect(validatePo("s", "DIESEL", "0", "1")).toMatch(/Litres/)
    expect(validatePo("s", "DIESEL", "10", "")).toMatch(/price/)
    expect(validatePo("s", "DIESEL", "10", "-1")).toMatch(/price/)
    expect(validatePo("s", "DIESEL", "10", "0")).toBeNull()
  })
  it("describes litres variance", () => {
    expect(litresVarianceLabel(0)).toBe("exact")
    expect(litresVarianceLabel(-500)).toMatch(/500 L short/)
    expect(litresVarianceLabel(20)).toMatch(/20 L over/)
  })
  it("closing short needs a real reason and has a label", () => {
    expect(validateCloseReason("  ")).toMatch(/why/); expect(validateCloseReason("no")).toMatch(/why/); expect(validateCloseReason("Supplier out of stock")).toBeNull()
    expect(STATUS_LABEL.CLOSED_SHORT).toBe("Closed short")
  })
})
