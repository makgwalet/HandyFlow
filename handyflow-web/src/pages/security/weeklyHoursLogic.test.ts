import { describe, it, expect } from "vitest"
import { addDays, canGoNextWeek, limitsText, mondayOf, problems, restText, stateLabel, summaryText, todaySast, weekTitle, type HoursReport, type HoursRow } from "./weeklyHoursLogic"

const T = { warnAt: 40, limit: 45, ceiling: 55, minRestHours: 12 }
const row = (o: Partial<HoursRow> = {}): HoursRow => ({ guardId: "g", guardName: "Thabo", hours: 48, state: "OVER", shifts: 4, overlaps: false, restBreaches: [], ...o })
const rep = (o: Partial<HoursReport> = {}): HoursReport => ({ weekStart: "2026-11-16", guardsRostered: 10, flagged: 0, thresholds: T, truncated: false, rows: [], ...o })

describe("weeklyHoursLogic", () => {
  it("finds the Monday of any day in the week", () => {
    for (const d of ["2026-11-16", "2026-11-18", "2026-11-22"]) expect(mondayOf(d)).toBe("2026-11-16")
    expect(mondayOf("2026-11-23")).toBe("2026-11-23")
    expect(mondayOf("2026-01-01")).toBe("2025-12-29")
  })
  it("moves days across month and year ends", () => {
    expect(addDays("2026-12-28", 7)).toBe("2027-01-04")
    expect(addDays("2026-03-02", -7)).toBe("2026-02-23")
  })
  it("today is the South African day", () => { expect(todaySast(new Date("2026-11-20T23:30:00Z"))).toBe("2026-11-21") })
  it("does not go past the current week", () => {
    const now = new Date("2026-11-20T10:00:00Z") // Fri; this week starts 2026-11-16
    expect(canGoNextWeek("2026-11-16", now)).toBe(false)
    expect(canGoNextWeek("2026-11-09", now)).toBe(true)
  })
  it("titles the week", () => { expect(weekTitle("2026-11-16")).toBe("16 Nov to 22 Nov") })
  it("labels states with the real limits", () => {
    expect(stateLabel("NEAR", T)).toBe("Near the 45 h limit")
    expect(stateLabel("OVER", T)).toBe("Over 45 h")
    expect(stateLabel("EXCEEDS", { ...T, ceiling: 60 })).toBe("Over 60 h")
    expect(stateLabel("OVER", { ...T, limit: 48 })).toBe("Over 48 h")
    expect(stateLabel("NEAR", { ...T, limit: 48 })).toBe("Near the 48 h limit")
    expect(stateLabel("OK", T)).toBe("Within limits")
    expect(stateLabel("X", T)).toBe("X")
  })
  it("words a short rest in South African time", () => {
    expect(restText({ from: "2026-11-16T16:00:00Z", to: "2026-11-16T20:00:00Z", hours: 4 })).toBe("Only 4 h rest (Mon 18:00 to Mon 22:00)")
  })
  it("lists problems", () => {
    expect(problems(row())).toEqual([])
    expect(problems(row({ overlaps: true }))).toEqual(["Double booked: two shifts overlap"])
    expect(problems(row({ overlaps: true, restBreaches: [{ from: "2026-11-16T16:00:00Z", to: "2026-11-16T20:00:00Z", hours: 4 }] }))).toHaveLength(2)
  })
  it("summarises", () => {
    expect(summaryText(rep({ guardsRostered: 0 }))).toBe("No guards are rostered this week.")
    expect(summaryText(rep())).toBe("None of the 10 rostered guards needs attention.")
    expect(summaryText(rep({ flagged: 1 }))).toBe("1 of 10 rostered guards needs attention.")
    expect(summaryText(rep({ flagged: 3 }))).toBe("3 of 10 rostered guards need attention.")
  })
  it("states the limits and that they are rostered hours", () => {
    const t = limitsText(T)
    expect(t).toContain("45 h ordinary limit")
    expect(t).toContain("rostered hours")
  })
})
