import { describe, it, expect } from "vitest"
import { lastMonthEnd, monthStart, recentMonthEnds, remaining } from "./monthEnd.logic"

describe("month helpers", () => {
  it("last month end", () => {
    expect(lastMonthEnd(new Date(2026, 9, 14))).toBe("2026-09-30")
    expect(lastMonthEnd(new Date(2026, 0, 1))).toBe("2025-12-31")
    expect(lastMonthEnd(new Date(2028, 2, 3))).toBe("2028-02-29")
  })
  it("month start", () => expect(monthStart("2026-09-30")).toBe("2026-09-01"))
  it("lists recent month ends, latest first", () => {
    const m = recentMonthEnds(new Date(2026, 9, 14), 3)
    expect(m.map(x => x.value)).toEqual(["2026-09-30", "2026-08-31", "2026-07-31"])
    expect(m[0].label).toContain("2026")
  })
  it("counts blocking items not done", () => {
    expect(remaining([{ blocking: true, status: "DONE" }, { blocking: true, status: "BLOCKED" },
      { blocking: false, status: "WARNING" }, { blocking: true, status: "WAITING" }])).toBe(2)
  })
})
