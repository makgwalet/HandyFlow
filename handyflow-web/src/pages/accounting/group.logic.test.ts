import { describe, it, expect } from "vitest"
import { cellText, knownGroupLines, mapProblem, ownershipText, ruleProblem, yearStart, linesOf, type Report } from "./group.logic"

describe("group.logic", () => {
  it("words ownership", () => {
    expect(ownershipText(100)).toBe("100%")
    expect(ownershipText(60.5)).toBe("60.5%")
  })
  it("shows zero as a dash", () => {
    expect(cellText(0)).toBe("—")
    expect(cellText(0.001)).toBe("—")
    expect(cellText(12.5)).toMatch(/12[.,]50/)
  })
  it("checks an intercompany rule", () => {
    expect(ruleProblem("", { tenant: "a", code: "1" }, { tenant: "b", code: "2" })).toMatch(/Name/)
    expect(ruleProblem("x", { tenant: "a", code: "" }, { tenant: "b", code: "2" })).toMatch(/both/)
    expect(ruleProblem("x", { tenant: "a", code: "1" }, { tenant: "a", code: "2" })).toMatch(/different/)
    expect(ruleProblem("x", { tenant: "a", code: "1" }, { tenant: "b", code: "2" })).toBeNull()
  })
  it("checks a mapping", () => {
    expect(mapProblem("", "1000", "1000")).toMatch(/company/)
    expect(mapProblem("t", "1010", "")).toMatch(/code/)
    expect(mapProblem("t", "1010", "bad code!")).toMatch(/letters/)
    expect(mapProblem("t", "1010", "1000")).toBeNull()
  })
  it("lists group lines once", () => {
    const m = (id: string, g: string, n: string | null) => ({ id, tenantId: "t", companyName: "c", accountCode: id, groupCode: g, groupName: n })
    expect(knownGroupLines([m("1", "2000", "Bank"), m("2", "2000", "Bank"), m("3", "1000", null)]).map(x => x.code)).toEqual(["1000", "2000"])
  })
  it("finds the year start", () => { expect(yearStart(new Date(2026, 9, 14))).toBe("2026-03-01") })
  it("filters lines by type", () => {
    const r = { lines: [{ type: "ASSET" }, { type: "INCOME" }, { type: "ASSET" }] } as unknown as Report
    expect(linesOf(r, "ASSET")).toHaveLength(2)
  })
})
