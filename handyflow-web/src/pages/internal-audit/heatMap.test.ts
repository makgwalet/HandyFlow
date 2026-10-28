import { describe, it, expect } from "vitest"
import { cellBand, cellLabel, gridRows, highShare, rowCounts } from "./heatMap.logic"
import type { HeatCell, HeatRow } from "../../api/auditHeatMap.api"

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
