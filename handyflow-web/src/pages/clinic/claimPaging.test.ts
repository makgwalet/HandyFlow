import { describe, it, expect } from "vitest"
import { pageRange, pageCount, hasPrevious, hasNext, clampPage } from "./claimPaging"

describe("claim paging", () => {
  it("describes the range on screen", () => {
    expect(pageRange(0, 25, 61, 25)).toBe("Showing 1-25 of 61")
    expect(pageRange(2, 25, 61, 11)).toBe("Showing 51-61 of 61")
    expect(pageRange(0, 25, 0, 0)).toBe("")
  })
  it("counts pages, at least one", () => {
    expect(pageCount(0, 25)).toBe(1)
    expect(pageCount(25, 25)).toBe(1)
    expect(pageCount(26, 25)).toBe(2)
  })
  it("knows when there is a previous or next page", () => {
    expect(hasPrevious(0)).toBe(false)
    expect(hasPrevious(1)).toBe(true)
    expect(hasNext(0, 25, 25)).toBe(false)
    expect(hasNext(0, 25, 26)).toBe(true)
    expect(hasNext(1, 25, 50)).toBe(false)
  })
  it("steps back when the last page empties", () => {
    expect(clampPage(2, 25, 50)).toBe(1)
    expect(clampPage(0, 25, 0)).toBe(0)
    expect(clampPage(1, 25, 60)).toBe(1)
  })
})
