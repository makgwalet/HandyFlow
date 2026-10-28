import { describe, it, expect } from "vitest"
import {
  change, checkBars, comparisonPeriod, comparisonRows, formatChange, groupBars, movementBars, payrollMetrics, runBars, workforceMetrics, type Metric,
} from "./reports.compare"
import type { PayrollReport, Workforce } from "./reports.shared"

const metric = (current: number | null, before: number | null, good: Metric["good"] = "down", unit: Metric["unit"] = "count"): Metric =>
  ({ label: "x", unit, good, current, before })

describe("the period to compare with", () => {
  it("takes the same number of days straight before", () => {
    expect(comparisonPeriod({ from: "2026-10-01", to: "2026-10-31" }, "previous")).toEqual({ from: "2026-08-31", to: "2026-09-30" })
  })
  it("crosses a year end", () => {
    expect(comparisonPeriod({ from: "2026-01-01", to: "2026-01-10" }, "previous")).toEqual({ from: "2025-12-22", to: "2025-12-31" })
  })
  it("takes the same dates a year earlier", () => {
    expect(comparisonPeriod({ from: "2026-03-01", to: "2026-10-28" }, "year")).toEqual({ from: "2025-03-01", to: "2025-10-28" })
  })
  it("moves 29 February to 28 February", () => {
    expect(comparisonPeriod({ from: "2028-02-29", to: "2028-03-31" }, "year")).toEqual({ from: "2027-02-28", to: "2027-03-31" })
  })
  it("gives nothing when no comparison is asked for or the period is incomplete", () => {
    expect(comparisonPeriod({ from: "2026-10-01", to: "2026-10-31" }, "none")).toBeNull()
    expect(comparisonPeriod({ from: "", to: "2026-10-31" }, "previous")).toBeNull()
  })
})

describe("how a figure moved", () => {
  it("judges a fall in leavers as good and a rise as bad", () => {
    expect(change(metric(2, 5)).tone).toBe("good")
    expect(change(metric(8, 5)).tone).toBe("bad")
  })
  it("gives no verdict for a figure with no better direction, or no change", () => {
    expect(change(metric(8, 5, "none")).tone).toBe("neutral")
    expect(change(metric(5, 5)).tone).toBe("neutral")
    expect(change(metric(5, 5)).direction).toBe("same")
  })
  it("gives a percentage only from a starting figure above nil", () => {
    expect(change(metric(15, 10)).percent).toBe(50)
    expect(change(metric(3, 0)).percent).toBeNull()
  })
  it("gives no change when either side is missing", () => {
    expect(change(metric(null, 4)).direction).toBe("none")
    expect(formatChange(metric(4, null))).toBe("–")
  })
  it("words the change", () => {
    expect(formatChange(metric(5, 5))).toBe("No change")
    expect(formatChange(metric(15, 10, "none"))).toBe("+5 (+50.0%)")
    expect(formatChange(metric(7.5, 10, "down", "percent"))).toBe("−2.5 points")
  })
})

const wf = (o: Partial<Workforce>): Workforce => ({
  from: "", to: "", opening: 10, closing: 12, joiners: 3, leavers: 1, turnoverPercent: null, annualisedTurnoverPercent: 8,
  byDepartment: [], byEmploymentType: [], tenure: [], age: [], leaversByReason: [], demographicsShown: false, gender: [], race: [], disability: [], notes: [], ...o,
})

describe("report figures lined up", () => {
  it("compares workforce headline figures", () => {
    const ms = workforceMetrics(wf({ leavers: 1 }), wf({ leavers: 4, closing: 11 }))
    expect(ms.find(m => m.label === "Leavers")).toMatchObject({ current: 1, before: 4, good: "down" })
    expect(ms.find(m => m.label === "Headcount at the end")).toMatchObject({ current: 12, before: 11, good: "none" })
  })
  it("leaves out a small group from a chart rather than showing a number that is not known", () => {
    expect(groupBars([{ label: "Sales", count: 9, hidden: false }, { label: "Legal", count: null, hidden: true }])).toEqual([{ label: "Sales", value: 9 }])
  })
  it("draws headcount movement in order", () => {
    expect(movementBars(wf({})).map(b => b.label)).toEqual(["Start", "Joiners", "Leavers", "End"])
  })
  it("draws only the runs the totals count, oldest first", () => {
    const run = (number: string, payDate: string, counted: boolean) =>
      ({ number, periodStart: "", periodEnd: "", payDate, status: "FINALISED", employees: 1, gross: 100, paye: 0, uifEmployee: 0, uifEmployer: 0, sdl: 0, net: 80, employerCost: 110, counted })
    const p = { from: "", to: "", runs: [run("B", "2026-02-25", true), run("X", "2026-02-26", false), run("A", "2026-01-25", true)],
      totals: { gross: 200, overtime: 0, paye: 0, uifEmployee: 0, uifEmployer: 0, sdl: 0, net: 160, employerCost: 220, runs: 2 }, byDepartment: [], emp201: [], notes: [] } as PayrollReport
    expect(runBars(p).map(r => r.label)).toEqual(["A", "B"])
    expect(payrollMetrics(p, p).find(m => m.label === "Employer cost")).toMatchObject({ current: 220, good: "down" })
  })
  it("charts only the checks that found something", () => {
    expect(checkBars({ from: "", to: "", checks: [{ code: "a", title: "A", count: 0, people: [], why: "" }, { code: "b", title: "B", count: 3, people: [], why: "" }], employeeRelations: null, notes: [] }))
      .toEqual([{ label: "B", value: 3 }])
  })
  it("adds the comparison under the report in the spreadsheet", () => {
    const rows = comparisonRows([metric(2, 5)], { from: "2026-10-01", to: "2026-10-31" }, { from: "2026-08-31", to: "2026-09-30" }, "The period just before")
    expect(rows.at(-1)).toEqual(["x", 2, 5, -3])
  })
})
