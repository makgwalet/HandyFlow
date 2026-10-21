import { describe, it, expect } from "vitest"
import { fuelLastDays, insuranceLine, keyFacts } from "./vehicleOverview.logic"

const today = new Date(2026, 9, 21)
describe("fuel over the last 30 days", () => {
  it("totals only the window and gives one bar per day", () => {
    const f = fuelLastDays([
      { filledAt: "2026-10-21", litres: 50, totalCost: 1000 }, { filledAt: "2026-10-01", litres: "25.5", totalCost: "510" },
      { filledAt: "2026-09-22", litres: 10, totalCost: 200 }, { filledAt: "2026-09-21", litres: 99, totalCost: 9999 }, // one day too early
    ], today)
    expect(f.bars).toHaveLength(30); expect(f.bars[0].date).toBe("2026-09-22"); expect(f.bars[29].date).toBe("2026-10-21")
    expect(f.litres).toBeCloseTo(85.5); expect(f.cost).toBe(1710); expect(f.fillups).toBe(3)
    expect(f.bars[29].fraction).toBe(1); expect(f.bars[0].fraction).toBeCloseTo(0.2); expect(f.bars[5].fraction).toBe(0)
  })
  it("adds up several fills on one day and copes with none", () => {
    expect(fuelLastDays([{ filledAt: "2026-10-20", litres: 10 }, { filledAt: "2026-10-20", litres: 15 }], today).bars[28].litres).toBe(25)
    const none = fuelLastDays([], today); expect(none.litres).toBe(0); expect(none.bars.every(b => b.fraction === 0)).toBe(true)
  })
})
describe("key facts and insurance", () => {
  it("shows gaps as Not recorded", () => {
    const rows = keyFacts({ status: "ON_TRIP", assignedDriverName: "John Mokoena", currentOdometer: 86340, dailyRate: 850 })
    expect(rows.find(r => r.label === "Operational status")?.value).toBe("On Trip"); expect(rows.find(r => r.label === "Assigned driver")?.value).toBe("John Mokoena")
    expect(rows.find(r => r.label === "Colour")).toMatchObject({ value: "Not recorded", missing: true }); expect(rows.find(r => r.label === "Odometer")?.value).toMatch(/^86\s340 km$/)
  })
  it("words the policy", () => {
    expect(insuranceLine({ insurer: "Santam", policyNumber: "ST123" })).toBe("Santam · Policy ST123"); expect(insuranceLine({ insurer: null, policyNumber: "P9" })).toBe("Policy P9")
    expect(insuranceLine({ insurer: null, policyNumber: null })).toBeNull(); expect(insuranceLine(null)).toBeNull()
  })
})
