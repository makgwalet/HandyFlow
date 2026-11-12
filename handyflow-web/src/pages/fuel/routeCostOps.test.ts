import { describe, it, expect } from "vitest"
import { flagText, varianceText, validateCost, periodDates, categoryLabel } from "./routeCostOps"

const r = (n: number) => `R${n}`

describe("routeCostOps (F02)", () => {
  it("writes a variance with its sign and percentage, or a dash when nothing is compared", () => {
    expect(varianceText(300, 30, r)).toBe("+R300 (+30.0%)")
    expect(varianceText(-50, -5, r)).toBe("-R50 (-5.0%)")
    expect(varianceText(0, 0, r)).toBe("R0 (0.0%)")
    expect(varianceText(120, null, r)).toBe("+R120")
    expect(varianceText(null, null, r)).toBe("–")
  })
  it("explains each flag and passes an unknown one through", () => {
    expect(flagText("NO_DISTANCE")).toMatch(/distance/); expect(flagText("RATES_NOT_SET")).toMatch(/Settings/); expect(flagText("X")).toBe("X")
  })
  it("checks a cost entry before it is sent", () => {
    expect(validateCost("", "10", "", "")).toMatch(/type/)
    expect(validateCost("VEHICLE", "", "", "")).toMatch(/amount/)
    expect(validateCost("VEHICLE", "-1", "", "")).toMatch(/amount/)
    expect(validateCost("VEHICLE", "10", "-2", "")).toMatch(/quantity/)
    expect(validateCost("OTHER", "10", "", " ")).toMatch(/what the cost was for/)
    expect(validateCost("OTHER", "10", "", "ferry")).toBeNull()
    expect(validateCost("TOLLS", "0", "", "")).toBeNull()
  })
  it("builds the period ending on a day", () => {
    expect(periodDates(30, new Date("2026-11-12T10:00:00Z"))).toEqual({ from: "2026-10-14", to: "2026-11-12" })
    expect(periodDates(1, new Date("2026-11-12T10:00:00Z"))).toEqual({ from: "2026-11-12", to: "2026-11-12" })
  })
  it("labels categories", () => { expect(categoryLabel("TOLLS")).toBe("Tolls"); expect(categoryLabel("Z")).toBe("Z") })
})
