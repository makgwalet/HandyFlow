import { describe, it, expect } from "vitest"
import { claimStatus, isOwed, methodLabel } from "./accountView"

describe("account wording", () => {
  it("names each claim state in plain words and shows an unknown one as words", () => {
    expect(claimStatus("NOT_BILLED").label).toBe("Not billed yet")
    expect(claimStatus("PARTIAL").label).toBe("Scheme part-paid")
    expect(claimStatus("ON_HOLD").label).toBe("on hold")
  })
  it("does not count rejected, voided or unbilled visits as owed", () => {
    expect(isOwed("ACCEPTED")).toBe(true); expect(isOwed("DRAFT")).toBe(true)
    expect(isOwed("REJECTED")).toBe(false); expect(isOwed("VOIDED")).toBe(false); expect(isOwed("NOT_BILLED")).toBe(false)
  })
  it("labels payment methods", () => { expect(methodLabel("EFT")).toBe("EFT"); expect(methodLabel("CASH")).toBe("Cash"); expect(methodLabel("VOUCHER")).toBe("VOUCHER") })
})
