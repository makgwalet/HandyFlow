import { describe, it, expect } from "vitest"
import { buildTextPatch } from "./agPatch"

describe("buildTextPatch", () => {
  it("returns null when nothing changed, so no request is sent", () => {
    expect(buildTextPatch({ name: "Bella", notes: null }, { name: "Bella", notes: "" })).toBeNull()
  })
  it("sends only the changed field", () => {
    expect(buildTextPatch({ name: "Bella", breed: "Boer", notes: "x" }, { name: "Bella", breed: "Boer", notes: "y" })).toEqual({ notes: "y" })
  })
  it("never sends a field the screen does not edit", () => {
    const patch = buildTextPatch({ name: "a", breed: "b", notes: "c" }, { name: "a2", breed: "b", notes: "c" })!
    for (const k of ["sireId", "damId", "dateOfBirth", "productionAreaId", "enterpriseId", "estimatedAge"]) expect(k in patch).toBe(false)
  })
  it("names an emptied field in clear instead of sending null", () => {
    expect(buildTextPatch({ name: "Bella", notes: "old" }, { name: "Bella", notes: "   " })).toEqual({ clear: ["notes"] })
  })
  it("sets one field and clears another in the same save", () => {
    expect(buildTextPatch({ breed: "Boer", notes: "old" }, { breed: "Dorper", notes: "" })).toEqual({ breed: "Dorper", clear: ["notes"] })
  })
  it("trims what it sends and ignores whitespace-only differences", () => {
    expect(buildTextPatch({ breed: "Boer" }, { breed: " Boer " })).toBeNull()
    expect(buildTextPatch({ breed: "Boer" }, { breed: "  Dorper " })).toEqual({ breed: "Dorper" })
  })
  it("treats an empty original and an empty edit as unchanged", () => {
    expect(buildTextPatch({ notes: undefined }, { notes: "" })).toBeNull()
  })
})
