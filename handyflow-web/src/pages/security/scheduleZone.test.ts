import { afterEach, describe, it, expect } from "vitest"
import { setCompanyZone } from "./companyZone"
import { clock, dayKey, dayStart, fetchWindow, mondayOf, toInstants, weekDays } from "./schedule.logic"

afterEach(() => setCompanyZone(undefined))

describe("the scheduler works in the company zone", () => {
  it("is unchanged for South Africa", () => {
    expect(dayKey("2026-11-22T21:59:00Z")).toBe("2026-11-22")
    expect(dayKey("2026-11-22T22:00:00Z")).toBe("2026-11-23")
    expect(dayStart("2026-11-23").toISOString()).toBe("2026-11-22T22:00:00.000Z")
    expect(toInstants("2026-11-23", "18:00", "06:00")).toEqual({ startAt: "2026-11-23T16:00:00.000Z", endAt: "2026-11-24T04:00:00.000Z" })
    expect(clock("2026-11-22T22:00:00Z")).toBe("00:00")
  })
  it("cuts days, weeks and shift times at the company's midnight", () => {
    setCompanyZone("Pacific/Auckland")
    expect(dayKey("2026-11-30T21:30:00Z")).toBe("2026-12-01")
    expect(dayStart("2026-12-01").toISOString()).toBe("2026-11-30T11:00:00.000Z")
    expect(mondayOf(new Date("2026-11-29T21:30:00Z"))).toBe("2026-11-30") // Monday morning in Auckland, still Sunday night in South Africa
    expect(toInstants("2026-12-01", "18:00", "06:00")).toEqual({ startAt: "2026-12-01T05:00:00.000Z", endAt: "2026-12-01T17:00:00.000Z" })
    expect(clock("2026-11-30T21:30:00Z")).toBe("10:30")
    const w = fetchWindow("2026-11-30")
    expect(w.from.toISOString()).toBe("2026-11-28T11:00:00.000Z")
    expect(w.to.toISOString()).toBe("2026-12-06T11:00:00.000Z")
  })
  it("a night shift across a clock change ends at the right hour", () => {
    setCompanyZone("Europe/London")
    // The clocks go back at 02:00 on 25 October: 22:00 to 06:00 is 9 hours, not 8.
    const t = toInstants("2026-10-24", "22:00", "06:00")!
    expect(t).toEqual({ startAt: "2026-10-24T21:00:00.000Z", endAt: "2026-10-25T06:00:00.000Z" })
    expect((Date.parse(t.endAt) - Date.parse(t.startAt)) / 3_600_000).toBe(9)
    // A week that contains the change is still seven calendar days.
    expect(weekDays("2026-10-19")).toHaveLength(7)
    expect(dayStart("2026-10-26").toISOString()).toBe("2026-10-26T00:00:00.000Z")
  })
  it("refuses bad times as before", () => {
    expect(toInstants("2026-11-23", "18:00", "18:00")).toBeNull()
    expect(toInstants("2026-11-23", "6:00", "18:00")).toBeNull()
  })
})
