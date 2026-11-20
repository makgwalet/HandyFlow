import { describe, it, expect } from "vitest"
import { metric, pct, pctText, greeting, statCards, donut, arcs, riskBars, deadlines, monthGrid, activityRows, daysBetween, isoDay, sourceColour, type CalendarEntry } from "./dashboardLogic"

const M = { "obligations.total": 48, "obligations.compliant": 36, "obligations.dueSoon": 2, "obligations.nonCompliant": 4, "obligations.overdue": 6, "findings.open": 3, "risks.open": 20, "risks.critical": 2, "risks.high": 8 }

describe("numbers", () => {
  it("metric reads missing, negative and non-finite values as 0", () => {
    expect(metric(M, "obligations.total")).toBe(48); expect(metric(M, "nope")).toBe(0); expect(metric(undefined, "x")).toBe(0)
    expect(metric({ a: -3 }, "a")).toBe(0); expect(metric({ a: NaN }, "a")).toBe(0)
  })
  it("pct rounds and is null with nothing to measure", () => {
    expect(pct(36, 48)).toBe(75); expect(pct(1, 3)).toBe(33); expect(pct(2, 3)).toBe(67); expect(pct(0, 5)).toBe(0); expect(pct(0, 0)).toBeNull(); expect(pct(3, 0)).toBeNull()
    expect(pctText(null)).toBe("—"); expect(pctText(75)).toBe("75%"); expect(pctText(0)).toBe("0%")
  })
  it("greets by the hour", () => {
    expect(greeting(0)).toBe("Good morning"); expect(greeting(11)).toBe("Good morning"); expect(greeting(12)).toBe("Good afternoon"); expect(greeting(17)).toBe("Good afternoon"); expect(greeting(18)).toBe("Good evening"); expect(greeting(23)).toBe("Good evening")
  })
})

describe("statCards", () => {
  it("shows the five figures with notes, and at risk is non-compliant plus due soon", () => {
    const c = statCards(M)
    expect(c.map(x => x.value)).toEqual([48, 36, 6, 6, 3])
    expect(c[0].note).toBe("6 overdue"); expect(c[1].note).toBe("75% of obligations"); expect(c[2].note).toBe("13% of obligations"); expect(c[4].note).toBe("Requires action")
    expect(c.map(x => x.tone)).toEqual(["neutral", "good", "warn", "bad", "warn"])
    expect(c.map(x => x.tab)).toEqual(["obligations", "obligations", "obligations", "obligations", "findings"])
  })
  it("with nothing recorded, shows zeros, dashes and calm tones rather than 0% or 100%", () => {
    const c = statCards({})
    expect(c.map(x => x.value)).toEqual([0, 0, 0, 0, 0]); expect(c[1].note).toBe("— of obligations"); expect(c[0].note).toBe("None overdue"); expect(c[4].note).toBe("None open")
    expect(c.map(x => x.tone)).toEqual(["neutral", "good", "good", "good", "good"])
  })
})

describe("donut", () => {
  it("splits obligations by status and the parts add up to the total", () => {
    const d = donut(M)
    expect(d.segments.map(s => s.count)).toEqual([36, 6, 6, 0]); expect(d.total).toBe(48); expect(d.compliantPercent).toBe(75)
    expect(d.segments.reduce((s, x) => s + x.count, 0)).toBe(48)
  })
  it("puts statuses outside the three groups into Other", () => {
    const d = donut({ "obligations.total": 10, "obligations.compliant": 4, "obligations.overdue": 1 })
    expect(d.segments.find(s => s.key === "other")?.count).toBe(5)
  })
  it("never shows a total smaller than its parts, and nothing at all when there are none", () => {
    expect(donut({ "obligations.total": 2, "obligations.compliant": 5 }).total).toBe(5)
    const e = donut({}); expect(e.total).toBe(0); expect(e.compliantPercent).toBeNull(); expect(arcs(e)).toEqual([])
  })
  it("arcs run on from each other around a 100-long circle and skip empty segments", () => {
    const a = arcs(donut({ "obligations.total": 4, "obligations.compliant": 2, "obligations.overdue": 1, "obligations.nonCompliant": 1 }))
    expect(a.map(x => x.key)).toEqual(["compliant", "atRisk", "overdue"])
    expect(a.map(x => x.dash)).toEqual([50, 25, 25]); expect(a.map(x => x.offset)).toEqual([-0, -50, -75])
    expect(arcs(donut(M)).map(x => x.key)).toEqual(["compliant", "atRisk", "overdue"])
  })
})

describe("riskBars", () => {
  it("shows critical, high and every other open risk, scaled to the largest", () => {
    const b = riskBars(M)
    expect(b.map(x => x.count)).toEqual([2, 8, 10]); expect(b.map(x => x.heightPercent)).toEqual([20, 80, 100])
  })
  it("a small non-zero bar is still visible, and zero is flat", () => {
    expect(riskBars({ "risks.open": 101, "risks.critical": 1, "risks.high": 0 }).map(x => x.heightPercent)).toEqual([6, 0, 100])
    expect(riskBars({}).map(x => x.heightPercent)).toEqual([0, 0, 0])
  })
  it("never goes negative if critical and high exceed open", () => { expect(riskBars({ "risks.open": 1, "risks.critical": 2, "risks.high": 3 })[2].count).toBe(0) })
})

describe("deadlines", () => {
  const e = (date: string, title: string, t = "OBLIGATION"): CalendarEntry => ({ date, sourceType: t, sourceId: title, title, detail: "d" })
  it("orders by date, labels by distance from today and limits the list", () => {
    const d = deadlines([e("2026-12-01", "C"), e("2026-11-18", "A"), e("2026-11-20", "B"), e("2026-11-27", "D"), e("2026-11-28", "E")], "2026-11-20", 4)
    expect(d.map(x => x.title)).toEqual(["A", "B", "D", "E"])
    expect(d.map(x => x.days)).toEqual([-2, 0, 7, 8]); expect(d.map(x => x.label)).toEqual(["Overdue", "Due today", "Due soon", "Upcoming"]); expect(d.map(x => x.tone)).toEqual(["bad", "warn", "warn", "info"])
  })
  it("does not change its input and copes with timestamps and an empty list", () => {
    const input = [e("2026-11-22T10:00:00Z", "B"), e("2026-11-21", "A")]
    expect(deadlines(input, "2026-11-20", 5)[0].title).toBe("A"); expect(input[0].title).toBe("B"); expect(deadlines(input, "2026-11-20", 5)[1].date).toBe("2026-11-22"); expect(deadlines([], "2026-11-20", 5)).toEqual([])
  })
  it("day arithmetic is calendar-day exact across month ends and leap years", () => {
    expect(daysBetween("2026-02-28", "2026-03-01")).toBe(1); expect(daysBetween("2028-02-28", "2028-03-01")).toBe(2); expect(daysBetween("2026-12-31", "2027-01-01")).toBe(1); expect(daysBetween("2026-11-20", "2026-11-20")).toBe(0)
    expect(isoDay(new Date(2026, 10, 5))).toBe("2026-11-05")
  })
})

describe("monthGrid", () => {
  it("lays November 2026 out in Monday-first weeks (1 Nov is a Sunday)", () => {
    const g = monthGrid(2026, 10, [], "2026-11-20")
    expect(g.length).toBe(6); expect(g.every(w => w.length === 7)).toBe(true)
    expect(g[0].slice(0, 6).every(c => c === null)).toBe(true); expect(g[0][6]?.day).toBe(1); expect(g[4][0]?.day).toBe(23); expect(g[4][1]?.day).toBe(24); expect(g[5][0]?.day).toBe(30); expect(g[5].slice(1).every(c => c === null)).toBe(true)
    expect(g.flat().filter(Boolean).length).toBe(30); expect(g.flat().find(c => c?.today)?.day).toBe(20)
  })
  it("starts on the right weekday: Feb 2027 starts on a Monday and has 28 days in 4 weeks", () => {
    const g = monthGrid(2027, 1, [], "2026-11-20"); expect(g.length).toBe(4); expect(g[0][0]?.day).toBe(1); expect(g.flat().filter(Boolean).length).toBe(28); expect(g.flat().some(c => c?.today)).toBe(false)
  })
  it("February in a leap year has 29 days", () => { expect(monthGrid(2028, 1, [], "x").flat().filter(Boolean).length).toBe(29) })
  it("marks the days that have entries with their distinct types, and ignores other months", () => {
    const es: CalendarEntry[] = [{ date: "2026-11-24", sourceType: "LITIGATION", sourceId: "1", title: "t", detail: "" }, { date: "2026-11-24T08:00:00Z", sourceType: "OBLIGATION", sourceId: "2", title: "t", detail: "" },
      { date: "2026-11-24", sourceType: "OBLIGATION", sourceId: "3", title: "t", detail: "" }, { date: "2026-12-01", sourceType: "OBLIGATION", sourceId: "4", title: "t", detail: "" }]
    const cells = monthGrid(2026, 10, es, "2026-11-20").flat().filter(Boolean)
    expect(cells.find(c => c!.day === 24)!.types).toEqual(["LITIGATION", "OBLIGATION"]); expect(cells.find(c => c!.day === 25)!.types).toEqual([]); expect(cells.length).toBe(30)
  })
})

describe("activity and colours", () => {
  it("takes the first rows, skips malformed ones and tolerates a missing 'by'", () => {
    const rows = [["2026-11-20 09:00", "Risks", "Risk updated", "Thabang"], ["bad"], ["2026-11-19 10:00", "Controls", "Evidence uploaded"], ["2026-11-18", "A", "B", "C"]]
    expect(activityRows(rows, 2)).toEqual([{ when: "2026-11-20 09:00", area: "Risks", event: "Risk updated", by: "Thabang" }, { when: "2026-11-19 10:00", area: "Controls", event: "Evidence uploaded", by: "" }])
    expect(activityRows(undefined, 5)).toEqual([])
  })
  it("knows the calendar source colours and has a fallback", () => { expect(sourceColour("LITIGATION").label).toBe("Litigation"); expect(sourceColour("OTHER").label).toBe("OTHER") })
})
