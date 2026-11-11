import { describe, it, expect } from "vitest"
import { validateAccount, validateHold, validateSite, deliverable, typeLabel, fmtR, type CustomerAccount } from "./customerOps"

describe("customerOps", () => {
  it("validates account fields", () => {
    expect(validateAccount("", "OTHER", "0", "30", true)).toMatch(/Choose the customer/)
    expect(validateAccount("c", "", "0", "30", false)).toMatch(/type/)
    expect(validateAccount("c", "OTHER", "-1", "30", false)).toMatch(/Credit limit/)
    expect(validateAccount("c", "OTHER", "", "30", false)).toMatch(/Credit limit/)
    expect(validateAccount("c", "OTHER", "100", "400", false)).toMatch(/terms/)
    expect(validateAccount("c", "OTHER", "100", "1.5", false)).toMatch(/terms/)
    expect(validateAccount("", "OTHER", "100", "30", false)).toBeNull()
    expect(validateAccount("c", "OTHER", "100", "30", true)).toBeNull()
  })
  it("needs a reason for a hold and a name for a site", () => {
    expect(validateHold("no")).not.toBeNull()
    expect(validateHold("Overdue 60 days")).toBeNull()
    expect(validateSite("  ")).not.toBeNull()
    expect(validateSite("Pit 1")).toBeNull()
  })
  it("offers only accounts not on hold for scheduling", () => {
    const a = (id: string, h: boolean) => ({ id, creditHold: h } as CustomerAccount)
    expect(deliverable([a("1", false), a("2", true)]).map(x => x.id)).toEqual(["1"])
  })
  it("formats", () => { expect(typeLabel("MINING_COMPANY")).toBe("Mining company"); expect(typeLabel("X")).toBe("X"); expect(fmtR(null)).toBe("–") })
})
