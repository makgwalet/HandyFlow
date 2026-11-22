import { describe, it, expect } from "vitest"
import { dayLabel, gapText, hhmm, postBody, postProblem, rangeText, runsPastMidnight, stateLabel, summaryText, timeOnly, toggleDay, weekdaysText, type CoverageBoard } from "./coverageLogic"

const board = (o: Partial<CoverageBoard> = {}): CoverageBoard => ({ from: "2026-11-20", days: 7, postCount: 2, shortCount: 0, uncoveredCount: 0, rows: [], ...o })

describe("coverageLogic", () => {
  it("shows times in South African time", () => {
    expect(hhmm("2026-11-20T04:00:00Z")).toBe("06:00")
    expect(rangeText("2026-11-20T16:00:00Z", "2026-11-21T04:00:00Z")).toBe("18:00–06:00")
  })
  it("words a gap", () => { expect(gapText({ start: "2026-11-20T08:00:00Z", end: "2026-11-20T10:00:00Z", have: 1, need: 2 })).toBe("10:00–12:00: 1 of 2 guards") })
  it("labels a day without drifting", () => { expect(dayLabel("2026-11-20")).toContain("20 Nov") })
  it("words states with a fallback", () => {
    expect(stateLabel("UNCOVERED")).toBe("No one rostered")
    expect(stateLabel("X")).toBe("X")
  })
  it("says there is nothing to measure when there are no posts, never 'covered'", () => {
    const t = summaryText(board({ postCount: 0 }))
    expect(t).toContain("nothing to measure")
    expect(t).not.toContain("covered")
  })
  it("summarises a covered, a short and an uncovered board", () => {
    expect(summaryText(board())).toBe("Every post is covered for the next 7 days.")
    expect(summaryText(board({ shortCount: 1 }))).toBe("1 post-day is short over the next 7 days.")
    expect(summaryText(board({ shortCount: 2, uncoveredCount: 1 }))).toBe("3 post-days are short over the next 7 days, 1 with no one rostered.")
  })
  it("words weekdays", () => {
    expect(weekdaysText([1, 2, 3, 4, 5])).toBe("Mon–Fri")
    expect(weekdaysText([1, 2, 3, 4, 5, 6, 7])).toBe("Every day")
    expect(weekdaysText([1, 3, 6])).toBe("Mon, Wed, Sat")
    expect(weekdaysText([6, 7])).toBe("Sat, Sun")
    expect(weekdaysText([5, 1, 1])).toBe("Mon, Fri")
  })
  it("detects posts that run past midnight", () => {
    expect(runsPastMidnight("18:00:00", "06:00:00")).toBe(true)
    expect(runsPastMidnight("06:00", "18:00")).toBe(false)
    expect(runsPastMidnight("00:00", "00:00")).toBe(true)
    expect(timeOnly("18:00:00")).toBe("18:00")
  })
  it("checks a new post", () => {
    expect(postProblem("", [1], "06:00", "18:00", "1")).toMatch(/name/)
    expect(postProblem("x".repeat(81), [1], "06:00", "18:00", "1")).toMatch(/80/)
    expect(postProblem("G", [], "06:00", "18:00", "1")).toMatch(/weekday/)
    expect(postProblem("G", [1], "", "18:00", "1")).toMatch(/time/)
    expect(postProblem("G", [1], "06:00", "18:00", "0")).toMatch(/1 to 50/)
    expect(postProblem("G", [1], "06:00", "18:00", "51")).toMatch(/1 to 50/)
    expect(postProblem("G", [1], "06:00", "18:00", "1.5")).toMatch(/1 to 50/)
    expect(postProblem("G", [1], "06:00", "18:00", "")).toMatch(/1 to 50/)
    expect(postProblem("G", [1], "06:00", "18:00", "50")).toBeNull()
  })
  it("builds the request body", () => {
    expect(postBody(" Gate ", [5, 1, 1], "18:00:00", "06:00", "2")).toEqual({ name: "Gate", weekdays: [1, 5], start: "18:00", end: "06:00", minGuards: 2 })
  })
  it("toggles days and keeps them sorted", () => {
    expect(toggleDay([1, 3], 2)).toEqual([1, 2, 3])
    expect(toggleDay([1, 2, 3], 2)).toEqual([1, 3])
  })
})
