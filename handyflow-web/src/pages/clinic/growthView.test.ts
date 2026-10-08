import { describe, it, expect } from "vitest"
import { ageLabel, chartRows, NOT_APPROVED, percentileText, sourceLine, zKey, zText, type MeasureChart } from "./growthView"

// Synthetic numbers: they exercise the formatting only and are not growth reference data.
const meas = (over: Partial<MeasureChart> = {}): MeasureChart => ({ code: "WEIGHT", label: "Weight", unit: "kg", banner: NOT_APPROVED,
  measurements: [{ observationId: "o1", takenAt: "2026-07-01T06:00:00Z", ageMonths: 6, value: "6.5", zScore: null, percentile: null }], curves: null, ...over })

describe("growthView", () => {
  it("words ages", () => {
    expect(ageLabel(0.4)).toBe("0 m"); expect(ageLabel(18)).toBe("18 m"); expect(ageLabel(24)).toBe("2 y"); expect(ageLabel(30)).toBe("2 y 6 m")
  })
  it("shows z-scores signed and never judges them", () => {
    expect(zText(1.234)).toBe("+1.23 SD"); expect(zText(-2)).toBe("−2.00 SD"); expect(zText(null)).toBe("—")
  })
  it("words percentiles", () => {
    expect(percentileText(null)).toBe("—"); expect(percentileText(0.02)).toBe("<0.1"); expect(percentileText(99.95)).toBe(">99.9")
    expect(percentileText(50.4)).toBe("50"); expect(percentileText(97.26)).toBe("97.3"); expect(percentileText(3.04)).toBe("3.0")
  })
  it("without an approved set there are no curves and the banner reason shows", () => {
    const m = meas()
    expect(chartRows(m)).toEqual([{ age: 6, value: 6.5 }])
    expect(sourceLine(m)).toContain("DATA NOT CLINICALLY APPROVED")
  })
  it("merges curves and the child's points on one age axis", () => {
    const m = meas({ banner: null, curves: { setId: "s", source: "Test source", sourceVersion: "1", zLines: [-1, 0, 1],
      points: [{ ageMonths: 0, values: [8, 10, 12] }, { ageMonths: 12, values: [14, 20, 26] }] } })
    const rows = chartRows(m)
    expect(rows.map(r => r.age)).toEqual([0, 6, 12])
    expect(rows[0][zKey(0)]).toBe(10); expect(rows[1].value).toBe(6.5)
    expect(sourceLine(m)).toBe("Reference: Test source, version 1")
  })
})
