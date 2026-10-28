import { describe, it, expect } from "vitest"
import { byControl, byResidual, cellBand, cellLabel, controlByProcessArea, effectiveness, effectivenessCounts, gridRows, highShare, residualBand, residualCounts, rowCounts } from "./heatMap.logic"
import type { HeatArea, HeatCell, HeatRow } from "../../api/auditHeatMap.api"

describe("heat map", () => {
  it("bands a square by the product of its scores", () => {
    expect(cellBand(1, 1)).toBe("low")
    expect(cellBand(2, 2)).toBe("low")
    expect(cellBand(2, 3)).toBe("medium")
    expect(cellBand(3, 3)).toBe("medium")
    expect(cellBand(3, 4)).toBe("high")
    expect(cellBand(3, 5)).toBe("high")
    expect(cellBand(4, 4)).toBe("critical")
    expect(cellBand(5, 5)).toBe("critical")
  })
  it("describes a square in words", () => {
    expect(cellLabel({ inherent: 5, control: 4, count: 2, areas: ["Payroll", "Treasury"] })).toBe("Inherent risk very high, control risk high: 2 areas: Payroll, Treasury")
    expect(cellLabel({ inherent: 1, control: 1, count: 1, areas: ["Stores"] })).toContain("1 area: Stores")
    expect(cellLabel({ inherent: 3, control: 2, count: 0, areas: [] })).toBe("Inherent risk moderate, control risk low: no areas")
  })
  it("lays the squares out as five rows of five", () => {
    const cells: HeatCell[] = []
    for (let i = 5; i >= 1; i--) for (let c = 1; c <= 5; c++) cells.push({ inherent: i, control: c, count: 0, areas: [] })
    const rows = gridRows(cells)
    expect(rows).toHaveLength(5)
    expect(rows[0].map(c => c.control)).toEqual([1, 2, 3, 4, 5])
    expect(rows[0][0].inherent).toBe(5)
    expect(rows[4][0].inherent).toBe(1)
    expect(gridRows([])).toEqual([])
  })
  it("reads a process-area row and the share rated high or critical", () => {
    const r = (o: Partial<HeatRow>): HeatRow => ({ processArea: "x", low: 0, medium: 0, high: 0, critical: 0, notAssessed: 0, total: 0, ...o })
    expect(rowCounts(r({ low: 1, high: 2 })).map(x => x.n)).toEqual([1, 0, 2, 0])
    expect(highShare([])).toBe(0)
    expect(highShare([r({ low: 1, medium: 1, high: 1, critical: 1 })])).toBe(50)
    expect(highShare([r({ low: 3, critical: 1 }), r({ high: 1, notAssessed: 5 })])).toBe(40)
  })
})

const area = (name: string, inherent: number, control: number, processArea = "HR"): HeatArea => ({ name, processArea, inherent, control, finalRisk: null })

describe("residual risk and control effectiveness", () => {
  it("reads residual risk from the product of the two scores", () => {
    expect(residualBand(area("a", 1, 4))).toBe("low")
    expect(residualBand(area("a", 3, 3))).toBe("medium")
    expect(residualBand(area("a", 4, 4))).toBe("critical")
    expect(residualBand(area("a", 5, 3))).toBe("high")
  })
  it("groups control risk into effective, partly effective and weak", () => {
    expect([1, 2, 3, 4, 5].map(effectiveness)).toEqual(["effective", "effective", "partly", "weak", "weak"])
  })
  it("orders worst first", () => {
    const list = [area("b", 2, 2), area("a", 5, 5), area("c", 5, 2), area("d", 3, 3)]
    expect(byResidual(list).map(x => x.name)).toEqual(["a", "c", "d", "b"])
    expect(byControl([area("x", 2, 2), area("y", 1, 5), area("z", 4, 5)]).map(x => x.name)).toEqual(["z", "y", "x"])
  })
  it("counts areas", () => {
    const list = [area("a", 5, 5), area("b", 1, 1), area("c", 3, 3), area("d", 4, 4)]
    expect(residualCounts(list)).toEqual({ low: 1, medium: 1, high: 0, critical: 2 })
    expect(effectivenessCounts(list)).toEqual({ effective: 1, partly: 1, weak: 2 })
    expect(effectivenessCounts([])).toEqual({ effective: 0, partly: 0, weak: 0 })
  })
  it("sums control by process area, ignoring case, weakest first", () => {
    const rows = controlByProcessArea([area("a", 3, 5, "Payroll"), area("b", 3, 1, "payroll"), area("c", 3, 1, "Fleet"), area("d", 3, 4, "Stores"), area("e", 3, 5, "Stores")])
    expect(rows.map(r => [r.processArea, r.total, r.counts.weak])).toEqual([["Stores", 2, 2], ["Payroll", 2, 1], ["Fleet", 1, 0]])
  })
})
