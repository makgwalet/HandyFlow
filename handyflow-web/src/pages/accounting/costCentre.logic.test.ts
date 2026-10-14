import { describe, it, expect } from "vitest"
import { treeOrder, parentChoices, yearStart, marginPercent, kindLabel, labelOf, type CostCentre } from "./costCentre.logic"

const c = (id: string, code: string, parentId: string | null = null, active = true): CostCentre =>
  ({ id, code, name: code + " name", kind: "BRANCH", parentId, active, lineCount: 0 })

describe("costCentre.logic", () => {
  it("lists parents before children with depth", () => {
    const out = treeOrder([c("3", "DBN-S", "2"), c("1", "CPT"), c("2", "DBN")])
    expect(out.map(x => [x.c.code, x.depth])).toEqual([["CPT", 0], ["DBN", 0], ["DBN-S", 1]])
  })
  it("shows an orphan at the top", () => {
    expect(treeOrder([c("1", "A", "gone")]).map(x => x.depth)).toEqual([0])
  })
  it("does not hang on a loop", () => {
    expect(treeOrder([c("1", "A", "2"), c("2", "B", "1")]).length).toBe(0)
  })
  it("never offers itself or its children as a parent", () => {
    const list = [c("1", "A"), c("2", "B", "1"), c("3", "C", "2"), c("4", "D")]
    expect(parentChoices(list, "1").map(x => x.id)).toEqual(["4"])
    expect(parentChoices(list, null).length).toBe(4)
  })
  it("finds the financial year start", () => {
    expect(yearStart(new Date(2026, 9, 14))).toBe("2026-03-01")
    expect(yearStart(new Date(2026, 1, 10))).toBe("2025-03-01")
  })
  it("computes margin only with income", () => {
    expect(marginPercent(1000, 250)).toBe(25)
    expect(marginPercent(0, -50)).toBeNull()
  })
  it("labels", () => {
    expect(kindLabel("PROJECT")).toBe("Project")
    expect(labelOf({ code: "X", name: "Y" })).toBe("X — Y")
  })
})
