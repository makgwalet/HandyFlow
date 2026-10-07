import { describe, it, expect } from "vitest"
import { cleanAnswers, scaleRange, toggleInList, toNumber, unitOptions, withUnit } from "./questionForm.logic"

describe("questionForm.logic", () => {
  it("parses numbers and treats blanks and junk as no answer", () => {
    expect(toNumber("12")).toBe(12)
    expect(toNumber(" 12.5 ")).toBe(12.5)
    expect(toNumber("")).toBeNull()
    expect(toNumber("abc")).toBeNull()
  })

  it("toggles a value in and out of a multi-select list without duplicates", () => {
    expect(toggleInList(undefined, "A")).toEqual(["A"])
    expect(toggleInList(["A"], "B")).toEqual(["A", "B"])
    expect(toggleInList(["A", "B"], "A")).toEqual(["B"])
  })

  it("builds {value, unit} answers and clears them when the number is blank", () => {
    expect(withUnit(undefined, { value: "3" }, "DAYS")).toEqual({ value: 3, unit: "DAYS" })
    expect(withUnit({ value: 3, unit: "DAYS" }, { unit: "WEEKS" }, "DAYS")).toEqual({ value: 3, unit: "WEEKS" })
    expect(withUnit({ value: 3, unit: "DAYS" }, { value: "" }, "DAYS")).toBeNull()
    expect(withUnit(undefined, { unit: "HOURS" }, "DAYS")).toBeNull()
  })

  it("drops empty answers but keeps false and zero", () => {
    expect(cleanAnswers({ a: "", b: "  ", c: [], d: null, e: false, f: 0, g: "x", h: ["y"] }))
      .toEqual({ e: false, f: 0, g: "x", h: ["y"] })
  })

  it("uses the question's own units when it has them", () => {
    expect(unitOptions([{ value: "kg" }, { value: "g" }], ["x"])).toEqual(["kg", "g"])
    expect(unitOptions([], ["x"])).toEqual(["x"])
  })

  it("scale defaults to 0 to 10 and honours min and max", () => {
    expect(scaleRange(null, null)).toHaveLength(11)
    expect(scaleRange(1, 5)).toEqual([1, 2, 3, 4, 5])
  })
})
