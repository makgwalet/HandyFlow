import { describe, it, expect } from "vitest"
import { weekStart, windowFor, step, dayLabel, windowTitle, groupByDay, filterItems, kindLabel, summaryLine, type CalendarItem } from "./agCalendar.logic"

const item = (o: Partial<CalendarItem> = {}): CalendarItem =>
  ({ date: "2026-11-23", kind: "HEALTH_DUE", title: "Vaccination due: 1234", detail: null, targetType: "ANIMAL", targetId: "a1", overdue: false, ...o })

describe("weeks run Monday to Sunday", () => {
  it("finds the Monday on or before a day", () => {
    expect(weekStart("2026-11-23")).toBe("2026-11-23")   // a Monday
    expect(weekStart("2026-11-25")).toBe("2026-11-23")
    expect(weekStart("2026-11-29")).toBe("2026-11-23")   // the Sunday
    expect(weekStart("2026-11-30")).toBe("2026-11-30")
    expect(weekStart("2027-01-01")).toBe("2026-12-28")   // across a year end
  })
  it("asks for one day or seven", () => {
    expect(windowFor("day", "2026-11-25")).toEqual({ from: "2026-11-25", to: "2026-11-25" })
    expect(windowFor("week", "2026-11-25")).toEqual({ from: "2026-11-23", to: "2026-11-29" })
  })
  it("steps by a day or a week either way", () => {
    expect(step("day", "2026-11-30", 1)).toBe("2026-12-01")
    expect(step("day", "2026-11-01", -1)).toBe("2026-10-31")
    expect(step("week", "2026-11-23", 1)).toBe("2026-11-30")
    expect(step("week", "2026-11-23", -1)).toBe("2026-11-16")
  })
})

describe("headings", () => {
  it("labels a day", () => { expect(dayLabel("2026-11-23")).toBe("Mon 23 Nov") })
  it("titles a day and a week", () => {
    expect(windowTitle("day", "2026-11-23", "2026-11-23")).toBe("Mon 23 Nov 2026")
    expect(windowTitle("week", "2026-11-23", "2026-11-29")).toBe("23 Nov – 29 Nov 2026")
    expect(windowTitle("week", "2026-11-30", "2026-12-06")).toBe("30 Nov – 6 Dec 2026")
  })
})

describe("groupByDay", () => {
  it("keeps empty days and puts each item on its own day", () => {
    const g = groupByDay([item({ date: "2026-11-24" }), item({ date: "2026-11-24", title: "b" }), item({ date: "2026-11-26" })], "2026-11-23", "2026-11-29")
    expect(g).toHaveLength(7)
    expect(g.map(d => d.items.length)).toEqual([0, 2, 0, 1, 0, 0, 0])
    expect(g[1].items[1].title).toBe("b")
  })
  it("drops items outside the window", () => {
    expect(groupByDay([item({ date: "2026-12-30" })], "2026-11-23", "2026-11-29").every(d => d.items.length === 0)).toBe(true)
  })
})

describe("filters and labels", () => {
  const all = [item({ kind: "HEALTH_DUE" }), item({ kind: "HEALTH_SCHEDULED" }), item({ kind: "WITHDRAWAL_END" }), item({ kind: "BIRTH_DUE" }),
    item({ kind: "PLANTING" }), item({ kind: "HARVEST" }), item({ kind: "SCOUTING_FOLLOWUP" })]
  it("filters by group", () => {
    expect(filterItems(all, "all")).toHaveLength(7)
    expect(filterItems(all, "health").map(i => i.kind)).toEqual(["HEALTH_DUE", "HEALTH_SCHEDULED"])
    expect(filterItems(all, "withdrawals").map(i => i.kind)).toEqual(["WITHDRAWAL_END"])
    expect(filterItems(all, "breeding").map(i => i.kind)).toEqual(["BIRTH_DUE"])
    expect(filterItems(all, "crops").map(i => i.kind)).toEqual(["PLANTING", "HARVEST", "SCOUTING_FOLLOWUP"])
  })
  it("names every kind", () => {
    expect(kindLabel("WITHDRAWAL_END")).toBe("Withdrawal ends")
    expect(kindLabel("SOMETHING_NEW")).toBe("something new")
  })
  it("sums up the period", () => {
    expect(summaryLine([])).toBe("Nothing due in this period.")
    expect(summaryLine([item()])).toBe("1 item.")
    expect(summaryLine([item(), item({ overdue: true })])).toBe("2 items, 1 overdue.")
  })
})
