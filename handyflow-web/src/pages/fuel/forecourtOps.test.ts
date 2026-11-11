import { describe, it, expect } from "vitest"
import { validatePump, validateOpen, validateClose, pumpTotals, expectedTotals, takings, needsReason, varianceText, type CloseRow } from "./forecourtOps"

const row = (o: Partial<CloseRow> = {}): CloseRow => ({ pumpId: "p1", name: "Pump 1", opening: 1000, price: 20, closing: "1210", test: "10", ...o })

describe("forecourtOps", () => {
  it("works out litres net of test litres and the value at the shift price", () => {
    expect(pumpTotals(row())).toEqual({ litres: 200, value: 4000 })
    expect(pumpTotals(row({ closing: "999" }))).toBeNull()
    expect(pumpTotals(row({ test: "999" }))).toBeNull()
    expect(expectedTotals([row(), row({ pumpId: "p2", opening: 5000, closing: "5100", test: "" })])).toEqual({ litres: 300, value: 6000 })
  })
  it("adds the tenders, ignoring blanks", () => {
    expect(takings("4000", "", "1999.5", "0.5")).toBe(6000)
  })
  it("needs a reason only beyond R5, and words the variance", () => {
    expect(needsReason(-5)).toBe(false)
    expect(needsReason(5.01)).toBe(true)
    expect(varianceText(-12.5)).toMatch(/short/)
    expect(varianceText(3)).toMatch(/over/)
    expect(varianceText(0)).toBe("Balanced")
  })
  it("validates a pump", () => {
    expect(validatePump("", "t", "20")).toMatch(/name/)
    expect(validatePump("P1", "", "20")).toMatch(/tank/)
    expect(validatePump("P1", "t", "0")).toMatch(/greater than zero/)
    expect(validatePump("P1", "t", "21.5")).toBeNull()
  })
  it("validates opening a shift", () => {
    expect(validateOpen("", [{ pumpId: "p", meter: "1", name: "P" }])).toMatch(/attendant/)
    expect(validateOpen("A", [])).toMatch(/at least one/)
    expect(validateOpen("A", [{ pumpId: "p", meter: "", name: "Pump 1" }])).toMatch(/Pump 1/)
    expect(validateOpen("A", [{ pumpId: "p", meter: "0", name: "P" }])).toBeNull()
  })
  it("validates closing: meters, test litres and the reason rule", () => {
    const ok = { cash: "4000", card: "0", account: "0", other: "0" }
    expect(validateClose([row({ closing: "" })], ok, "")).toMatch(/closing meter/)
    expect(validateClose([row({ closing: "900" })], ok, "")).toMatch(/below the opening/)
    expect(validateClose([row({ test: "500" })], ok, "")).toMatch(/more than the meter moved/)
    expect(validateClose([row()], { ...ok, cash: "-1" }, "")).toMatch(/zero or more/)
    expect(validateClose([row()], ok, "")).toBeNull()
    expect(validateClose([row()], { ...ok, cash: "3000" }, "")).toMatch(/Give a reason/)
    expect(validateClose([row()], { ...ok, cash: "3000" }, "Drive-off reported")).toBeNull()
  })
})
