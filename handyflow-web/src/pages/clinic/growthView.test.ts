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

import { bandCount, bandRows, chartTitle, latestOf, mergedTable, tabLabel, trendLine, type GrowthChart } from "./growthView"

const m2 = (over: Partial<MeasureChart> = {}): MeasureChart => ({ code: "WEIGHT", label: "Weight", unit: "kg", banner: null, curves: null, measurements: [
  { observationId: "a", takenAt: "2026-06-10T08:00:00Z", ageMonths: 70, value: 22.4, zScore: null, percentile: null },
  { observationId: "b", takenAt: "2026-08-12T08:00:00Z", ageMonths: 72, value: 23.1, zScore: 0.5, percentile: 69.1 },
  { observationId: "c", takenAt: "2026-10-08T08:00:00Z", ageMonths: 84, value: 24.3, zScore: 0.6, percentile: 72 }], ...over })

describe("growth page helpers", () => {
  it("names the tabs and the chart, with the sex and the age range only when a reference covers one", () => {
    expect(tabLabel({ code: "HEAD_CIRCUMFERENCE", label: "x" })).toBe("Head circumference-for-age"); expect(tabLabel({ code: "ZZZ", label: "Odd" })).toBe("Odd")
    expect(chartTitle(m2(), "MALE")).toBe("Weight-for-age (Boys)")
    expect(chartTitle(m2(), null)).toBe("Weight-for-age")
    const withCurves = m2({ curves: { setId: "s", source: "S", sourceVersion: "1", zLines: [-2, 0, 2], points: [{ ageMonths: 24, values: [1, 2, 3] }, { ageMonths: 240, values: [2, 3, 4] }] } })
    expect(chartTitle(withCurves, "FEMALE")).toBe("Weight-for-age (Girls 2–20 years)")
    expect(chartTitle(m2({ curves: { setId: "s", source: "S", sourceVersion: "1", zLines: [0], points: [{ ageMonths: 0, values: [3] }, { ageMonths: 12, values: [9] }] } }), "MALE")).toBe("Weight-for-age (Boys 0–12 months)")
  })
  it("builds stacked band rows from the reference lines, never negative", () => {
    const m = m2({ curves: { setId: "s", source: "S", sourceVersion: "1", zLines: [-2, 0, 2], points: [{ ageMonths: 0, values: [2, 3, 5] }, { ageMonths: 12, values: [4, 4, 3] }] } })
    expect(bandRows(m)).toEqual([{ age: 0, base: 2, band1: 1, band2: 2 }, { age: 12, base: 4, band1: 0, band2: 0 }])
    expect(bandCount(m)).toBe(2); expect(bandRows(m2())).toEqual([]); expect(bandCount(m2())).toBe(0)
  })
  it("gives the latest value, its difference from the one before, and no verdict", () => {
    const l = latestOf(m2())!
    expect(l.value).toBe("24.3"); expect(l.change).toBe("+1.2 kg"); expect(l.since).toMatch(/12 Aug 2026/); expect(l.z).toBe("+0.60 SD"); expect(l.percentile).toBe("72")
    expect(latestOf(m2({ measurements: [] }))).toBeNull()
    expect(latestOf(m2({ measurements: [m2().measurements[0]] }))!.change).toBeNull()
  })
  it("describes the trend from first to last measurement", () => {
    expect(trendLine(m2())).toBe("Up 1.9 kg over 14 m")
    expect(trendLine(m2({ measurements: [m2().measurements[0]] }))).toBeNull()
    const down = m2({ measurements: [{ ...m2().measurements[0], value: 25 }, m2().measurements[2]] })
    expect(trendLine(down)).toBe("Down 0.7 kg over 14 m")
  })
  it("merges the measures into one row per day, newest first, with the chart measure's percentile", () => {
    const g: GrowthChart = { patientId: "p", sexAtBirth: "MALE", currentAgeMonths: 84, notes: [], measures: [m2(), { code: "HEIGHT", label: "Height", unit: "cm", banner: null, curves: null,
      measurements: [{ observationId: "h", takenAt: "2026-10-08T09:00:00Z", ageMonths: 84, value: 121, zScore: null, percentile: null }] }] }
    const t = mergedTable(g, "WEIGHT")
    expect(t.map(r => r.day)).toEqual(["2026-10-08", "2026-08-12", "2026-06-10"])
    expect(t[0].cells).toEqual({ WEIGHT: "24.3 kg", HEIGHT: "121 cm" }); expect(t[0].percentile).toBe("72"); expect(t[0].z).toBe("+0.60 SD"); expect(t[2].percentile).toBe("—")
  })
})
