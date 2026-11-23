import { describe, it, expect } from "vitest"
import { SOIL_TYPES, STOCK_UNITS, withCurrent, breedSuggestions, feedSuggestions } from "./agLookups"
import { canonicalUnit } from "./agUnits"

describe("withCurrent", () => {
  it("leaves the list alone when the saved value is in it, whatever its case", () => {
    expect(withCurrent(SOIL_TYPES, "clay")).toBe(SOIL_TYPES)
    expect(withCurrent(SOIL_TYPES, "")).toBe(SOIL_TYPES)
    expect(withCurrent(SOIL_TYPES, null)).toBe(SOIL_TYPES)
  })
  it("keeps an old value that is not in the list", () => {
    expect(withCurrent(SOIL_TYPES, "Sticky red stuff")).toEqual([...SOIL_TYPES, "Sticky red stuff"])
  })
})

describe("lists", () => {
  it("have no duplicates", () => {
    for (const l of [SOIL_TYPES, STOCK_UNITS]) expect(new Set(l.map(x => x.toLowerCase())).size).toBe(l.length)
  })
  it("offer units the server understands as themselves, so none is folded into another", () => {
    for (const u of STOCK_UNITS) expect(canonicalUnit(u)).toBe(u.toLowerCase())
  })
})

describe("suggestions", () => {
  it("match the species name, ignoring case and spacing", () => {
    expect(breedSuggestions(" Cattle ")).toContain("Bonsmara")
    expect(breedSuggestions("SHEEP")).toContain("Dorper")
    expect(feedSuggestions("broilers")).toContain("Broiler starter")
  })
  it("give breeds nothing for an unknown species and a general feed list", () => {
    expect(breedSuggestions("Alpacas")).toEqual([])
    expect(breedSuggestions(null)).toEqual([])
    expect(feedSuggestions("Alpacas")).toContain("Lucerne hay")
  })
})
