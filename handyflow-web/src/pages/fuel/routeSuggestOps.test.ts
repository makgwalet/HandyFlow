import { describe, it, expect } from "vitest"
import { canSuggest, movedCount, suggestedIds, suggestionHeadline } from "./routeSuggestOps"

describe("routeSuggestOps", () => {
  it("says how much shorter a straight-line suggestion is", () => {
    expect(suggestionHeadline({ method: "STRAIGHT_LINE", changed: true, savingKm: 11.54 })).toBe("This order is about 11.5 km shorter in a straight line.")
  })
  it("says so plainly when the order does not change", () => {
    expect(suggestionHeadline({ method: "STRAIGHT_LINE", changed: false, savingKm: 0 })).toMatch(/already the shortest/)
    expect(suggestionHeadline({ method: "WINDOWS_AND_DISTANCE", changed: false, savingKm: null })).toMatch(/already follows the delivery windows/)
  })
  it("describes the fallback and the window-keeping case", () => {
    expect(suggestionHeadline({ method: "WINDOWS_AND_DISTANCE", changed: true, savingKm: null })).toMatch(/windows first, then the nearest sites/)
    expect(suggestionHeadline({ method: "STRAIGHT_LINE", changed: true, savingKm: 0 })).toMatch(/keeps the delivery windows in order/)
  })
  it("needs two scheduled stops before a suggestion is worth asking for", () => {
    expect(canSuggest([{ status: "SCHEDULED" }, { status: "SCHEDULED" }])).toBe(true)
    expect(canSuggest([{ status: "DELIVERED" }, { status: "SCHEDULED" }])).toBe(false)
    expect(canSuggest([])).toBe(false)
  })
  it("builds the id list and counts the moved stops", () => {
    const o = { stops: [{ deliveryId: "b", moved: true }, { deliveryId: "a", moved: false }, { deliveryId: "c", moved: true }] } as any
    expect(suggestedIds(o)).toEqual(["b", "a", "c"])
    expect(movedCount(o)).toBe(2)
  })
})
