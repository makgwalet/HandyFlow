import { describe, it, expect } from "vitest"
import { editorProblem, moveItem, OPS, MAX_FILTERS, type FilterSpec } from "./customReportLogic"

const f = (column: string, op: FilterSpec["op"], value = ""): FilterSpec => ({ column, op, value })

describe("editorProblem", () => {
  it("accepts a complete definition", () => { expect(editorProblem("Top risks", "RISKS", ["Title"], [f("Score", "GT", "5")])).toBeNull() })
  it("needs a name of 3 characters, ignoring spaces", () => { expect(editorProblem("  ab ", "RISKS", ["Title"], [])).not.toBeNull(); expect(editorProblem(" abc ", "RISKS", ["Title"], [])).toBeNull() })
  it("needs a standard report and at least one column", () => { expect(editorProblem("Top risks", "", ["Title"], [])).not.toBeNull(); expect(editorProblem("Top risks", "RISKS", [], [])).not.toBeNull() })
  it("a filter needs a column, and a value unless the test is about emptiness", () => {
    expect(editorProblem("Top risks", "RISKS", ["Title"], [f("", "EQUALS", "x")])).not.toBeNull()
    expect(editorProblem("Top risks", "RISKS", ["Title"], [f("Owner", "EQUALS", "  ")])).toContain("Owner")
    expect(editorProblem("Top risks", "RISKS", ["Title"], [f("Owner", "EMPTY")])).toBeNull()
    expect(editorProblem("Top risks", "RISKS", ["Title"], [f("Owner", "NOT_EMPTY")])).toBeNull()
  })
})

describe("moveItem", () => {
  it("moves up and down", () => { expect(moveItem(["a", "b", "c"], 1, -1)).toEqual(["b", "a", "c"]); expect(moveItem(["a", "b", "c"], 1, 1)).toEqual(["a", "c", "b"]) })
  it("does nothing at the ends or out of range, and never changes the input", () => {
    const l = ["a", "b"]
    expect(moveItem(l, 0, -1)).toEqual(["a", "b"]); expect(moveItem(l, 1, 1)).toEqual(["a", "b"]); expect(moveItem(l, 5, -1)).toEqual(["a", "b"])
    moveItem(l, 0, 1); expect(l).toEqual(["a", "b"])
  })
})

describe("operators", () => {
  it("match the server's list, and only the two emptiness tests take no value", () => {
    expect(OPS.map(o => o.value)).toEqual(["EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "STARTS_WITH", "GT", "GTE", "LT", "LTE", "EMPTY", "NOT_EMPTY"])
    expect(OPS.filter(o => !o.needsValue).map(o => o.value)).toEqual(["EMPTY", "NOT_EMPTY"])
    expect(MAX_FILTERS).toBe(10)
  })
})
