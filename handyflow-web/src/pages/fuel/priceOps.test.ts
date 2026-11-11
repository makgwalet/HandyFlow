import { describe, it, expect } from "vitest"
import { validatePrice, scopeLabel, breakLabel } from "./priceOps"

describe("priceOps", () => {
  it("validates a price", () => {
    expect(validatePrice("", "20", "", "2026-11-01", "")).toMatch(/fuel/)
    expect(validatePrice("DIESEL", "", "", "2026-11-01", "")).toMatch(/price/)
    expect(validatePrice("DIESEL", "-1", "", "2026-11-01", "")).toMatch(/price/)
    expect(validatePrice("DIESEL", "20", "-5", "2026-11-01", "")).toMatch(/volume/)
    expect(validatePrice("DIESEL", "20", "", "", "")).toMatch(/starts/)
    expect(validatePrice("DIESEL", "20", "", "2026-11-10", "2026-11-01")).toMatch(/before/)
    expect(validatePrice("DIESEL", "0", "10000", "2026-11-01", "2026-12-31")).toBeNull()
  })
  it("labels", () => {
    expect(scopeLabel({ accountId: null, customerName: null })).toBe("Standard list")
    expect(scopeLabel({ accountId: "a", customerName: "Gold Mine" })).toBe("Gold Mine")
    expect(breakLabel(0)).toBe("any volume")
    expect(breakLabel(10000)).toMatch(/from .*10.*000 L/)
  })
})
