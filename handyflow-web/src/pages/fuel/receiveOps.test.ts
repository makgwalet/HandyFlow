import { describe, it, expect } from "vitest"
import { freightPerLitre, validateFreight, landedText } from "./receiveOps"

describe("receiveOps", () => {
  it("spreads freight over the litres to four places and ignores blank, zero or bad input", () => {
    expect(freightPerLitre("5000", "1500")).toBe(0.3)
    expect(freightPerLitre("3000", "1000")).toBe(0.3333)
    expect(freightPerLitre("5000", "")).toBeNull(); expect(freightPerLitre("5000", "0")).toBeNull(); expect(freightPerLitre("0", "100")).toBeNull(); expect(freightPerLitre("5000", "x")).toBeNull()
  })
  it("a blank freight box is fine; a negative or non-number is not", () => {
    expect(validateFreight("")).toBeNull(); expect(validateFreight("0")).toBeNull(); expect(validateFreight("1500")).toBeNull()
    expect(validateFreight("-1")).toMatch(/zero or more/); expect(validateFreight("abc")).toMatch(/zero or more/)
  })
  it("says what the tank's cost is built from, only when there is freight", () => {
    expect(landedText("5000", "20", "1500")).toBe("R20.3000 a litre with freight (R0.3000 of it freight)")
    expect(landedText("5000", "20", "")).toBeNull(); expect(landedText("5000", "", "1500")).toBeNull()
  })
})
