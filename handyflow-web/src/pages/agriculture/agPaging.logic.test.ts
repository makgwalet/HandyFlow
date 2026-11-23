import { describe, it, expect } from "vitest"
import { searchParam, lastPage, clampPage, rangeLabel, fetchAllPages, truncationNote, LIST_MAX_ROWS, PAGE_SIZE, MAX_SEARCH } from "./agPaging.logic"

describe("searchParam", () => {
  it("is undefined when blank so no q is sent", () => {
    expect(searchParam("")).toBeUndefined()
    expect(searchParam("   ")).toBeUndefined()
  })
  it("trims", () => expect(searchParam("  A12 ")).toBe("A12"))
  it("is cut to what the server accepts", () => {
    expect(searchParam("a".repeat(MAX_SEARCH))).toHaveLength(MAX_SEARCH)
    expect(searchParam("a".repeat(MAX_SEARCH + 20))).toHaveLength(MAX_SEARCH)
  })
})

describe("lastPage and clampPage", () => {
  it("counts zero-based pages", () => {
    expect(lastPage(0)).toBe(0)
    expect(lastPage(1)).toBe(0)
    expect(lastPage(PAGE_SIZE)).toBe(0)
    expect(lastPage(PAGE_SIZE + 1)).toBe(1)
    expect(lastPage(120)).toBe(2)
  })
  it("pulls a page past the end back to the last one", () => {
    expect(clampPage(5, 120)).toBe(2)
    expect(clampPage(2, 120)).toBe(2)
    expect(clampPage(-1, 120)).toBe(0)
    expect(clampPage(3, 0)).toBe(0)
  })
})

describe("rangeLabel", () => {
  it("shows the rows on a page of a longer list", () => {
    expect(rangeLabel(0, 120, "animal")).toBe("Showing 1–50 of 120 animals")
    expect(rangeLabel(1, 120, "animal")).toBe("Showing 51–100 of 120 animals")
    expect(rangeLabel(2, 120, "animal")).toBe("Showing 101–120 of 120 animals")
  })
  it("says just the count when everything fits on one page", () => {
    expect(rangeLabel(0, 1, "animal")).toBe("1 animal")
    expect(rangeLabel(0, 50, "group")).toBe("50 groups")
    expect(rangeLabel(0, 51, "group")).toBe("Showing 1–50 of 51 groups")
  })
  it("says what was searched for", () => {
    expect(rangeLabel(0, 0, "animal", "ox")).toBe("No animals match “ox”")
    expect(rangeLabel(0, 0, "animal")).toBe("No animals found")
    expect(rangeLabel(0, 3, "animal", "ox")).toBe("3 animals match “ox”")
    expect(rangeLabel(1, 120, "animal", "ox")).toBe("Showing 51–100 of 120 animals matching “ox”")
  })
  it("does not show a range past the end", () => expect(rangeLabel(9, 120, "animal")).toBe("120 animals"))
})

const pager = (total: number, calls: number[] = []) => async (page: number, size: number) => {
  calls.push(page)
  const from = page * size
  return { content: Array.from({ length: Math.max(0, Math.min(size, total - from)) }, (_, i) => from + i), totalElements: total }
}

describe("fetchAllPages", () => {
  it("is one call for a short list", async () => {
    const calls: number[] = []
    const r = await fetchAllPages(pager(30, calls))
    expect(r.items).toHaveLength(30)
    expect(r.truncated).toBe(false)
    expect(calls).toEqual([0])
  })
  it("does not ask for another page after exactly 200", async () => {
    const calls: number[] = []
    const r = await fetchAllPages(pager(200, calls))
    expect(r.items).toHaveLength(200)
    expect(calls).toEqual([0])
  })
  it("keeps going past 200 rows, in order and without repeats", async () => {
    const calls: number[] = []
    const r = await fetchAllPages(pager(450, calls))
    expect(r.items).toHaveLength(450)
    expect(r.items.slice(0, 3)).toEqual([0, 1, 2])
    expect(new Set(r.items).size).toBe(450)
    expect(r.truncated).toBe(false)
    expect(calls).toEqual([0, 1, 2])
  })
  it("stops at 2,000 rows and says it was cut", async () => {
    const calls: number[] = []
    const r = await fetchAllPages(pager(2600, calls))
    expect(r.items).toHaveLength(2000)
    expect(r.total).toBe(2600)
    expect(r.truncated).toBe(true)
    expect(calls).toHaveLength(10)
  })
  it("stops when a page comes back empty even if the total says more", async () => {
    const r = await fetchAllPages(async (page) => ({ content: page === 0 ? [1, 2] : [], totalElements: 999 }))
    expect(r.items).toEqual([1, 2])
    expect(r.truncated).toBe(true)
  })
  it("handles no rows", async () => {
    const r = await fetchAllPages(pager(0))
    expect(r).toEqual({ items: [], total: 0, truncated: false })
  })
})

describe("truncationNote", () => {
  it("says nothing when everything is shown", () => {
    expect(truncationNote(150, 150, "sales")).toBeNull()
  })
  it("says how many of how many are shown", () => {
    const n = truncationNote(2000, 2750, "ledger rows")
    expect(n).toContain("2")
    expect(n).toContain("ledger rows")
    expect(n).toContain("filter")
    expect(n).toMatch(/2\D?000/)
    expect(n).toMatch(/2\D?750/)
  })
  it("the list limit matches what fetchAllPages can load", () => {
    expect(LIST_MAX_ROWS).toBe(2000)
  })
})
