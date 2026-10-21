import { describe, it, expect } from "vitest"
import { stationShares, type StationRow } from "./fleetBoards.logic"

const st = (station: string, fuelType: string, totalSpend: number): StationRow => ({ station, fuelType, fillups: 1, litres: 10, totalSpend, avgPricePerLitre: 20, premiumPercent: null })
describe("station shares", () => {
  it("adds a station's fuel types together, biggest first, with percentages of the total", () => {
    const s = stationShares([st("Shell", "DIESEL", 2000), st("Engen", "DIESEL", 6000), st("Engen", "PETROL", 2000)])
    expect(s.map(x => [x.label, x.percent])).toEqual([["Engen", 80], ["Shell", 20]]); expect(s[0].fraction).toBeCloseTo(0.8)
  })
  it("folds stations past the limit into Other, and gives nothing when there is no spend", () => {
    const s = stationShares([st("A", "D", 50), st("B", "D", 30), st("C", "D", 10), st("D", "D", 6), st("E", "D", 4)], 4)
    expect(s.map(x => x.label)).toEqual(["A", "B", "C", "D", "Other"]); expect(s[4].percent).toBe(4); expect(s.reduce((a, x) => a + x.fraction, 0)).toBeCloseTo(1)
    expect(stationShares([])).toEqual([]); expect(stationShares([st("A", "D", 0)])).toEqual([])
  })
})
