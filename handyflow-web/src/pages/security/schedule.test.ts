import { describe, it, expect } from "vitest"
import { addDays, buildCells, dayKey, detectConflicts, fetchWindow, gridGuards, mondayOf, overHours, toInstants, weekDays, weeklyHours, type GridGuard, type GridShift } from "./schedule.logic"

const g = (id: string, over: Partial<GridGuard> = {}): GridGuard => ({ id, firstName: id.toUpperCase(), lastName: "Guard", status: "ACTIVE", psiraExpiryDate: "2030-01-01", ...over })
// Times are written in South African time (+02:00).
const sh = (id: string, guardId: string, start: string, end: string, over: Partial<GridShift> = {}): GridShift =>
  ({ id, siteId: "s1", guardId, startAt: new Date(`${start}+02:00`).toISOString(), endAt: new Date(`${end}+02:00`).toISOString(), status: "SCHEDULED", ...over })

describe("weeks and days", () => {
  it("finds Monday, including from a Sunday and a Monday", () => {
    expect(mondayOf(new Date("2026-10-07T10:00:00+02:00"))).toBe("2026-10-05") // Wednesday
    expect(mondayOf(new Date("2026-10-11T23:30:00+02:00"))).toBe("2026-10-05") // Sunday night
    expect(mondayOf(new Date("2026-10-05T00:10:00+02:00"))).toBe("2026-10-05")
  })
  it("uses South African days, not UTC days", () => {
    expect(dayKey("2026-10-05T22:30:00Z")).toBe("2026-10-06") // 00:30 SAST next day
    expect(dayKey("2026-10-05T21:59:00Z")).toBe("2026-10-05")
  })
  it("lists seven days and a window that includes the evening before", () => {
    expect(weekDays("2026-10-05")).toEqual(["2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"])
    const w = fetchWindow("2026-10-05")
    expect(w.from.toISOString()).toBe("2026-10-03T22:00:00.000Z")
    expect(w.to.toISOString()).toBe("2026-10-11T22:00:00.000Z")
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01")
  })
})

describe("conflicts", () => {
  it("flags an overlap on the later shift", () => {
    const m = detectConflicts([sh("a", "g1", "2026-10-05T06:00:00", "2026-10-05T18:00:00"), sh("b", "g1", "2026-10-05T17:00:00", "2026-10-06T02:00:00")], [g("g1")])
    expect(m.get("b")?.[0].kind).toBe("OVERLAP"); expect(m.has("a")).toBe(false)
  })
  it("catches one long shift overlapping two later ones", () => {
    const m = detectConflicts([sh("a", "g1", "2026-10-05T06:00:00", "2026-10-05T22:00:00"), sh("b", "g1", "2026-10-05T08:00:00", "2026-10-05T09:00:00"), sh("c", "g1", "2026-10-05T10:00:00", "2026-10-05T11:00:00")], [g("g1")])
    expect(m.get("b")?.[0].kind).toBe("OVERLAP"); expect(m.get("c")?.[0].kind).toBe("OVERLAP")
  })
  it("warns about short rest but not a full rest", () => {
    const short = detectConflicts([sh("a", "g1", "2026-10-05T06:00:00", "2026-10-05T18:00:00"), sh("b", "g1", "2026-10-06T00:00:00", "2026-10-06T06:00:00")], [g("g1")])
    expect(short.get("b")?.[0]).toMatchObject({ kind: "SHORT_REST", tone: "warn" })
    const ok = detectConflicts([sh("a", "g1", "2026-10-05T06:00:00", "2026-10-05T18:00:00"), sh("b", "g1", "2026-10-06T02:00:00", "2026-10-06T10:00:00")], [g("g1")])
    expect(ok.size).toBe(0)
  })
  it("never compares different guards, and ignores cancelled and missed shifts", () => {
    expect(detectConflicts([sh("a", "g1", "2026-10-05T06:00:00", "2026-10-05T18:00:00"), sh("b", "g2", "2026-10-05T06:00:00", "2026-10-05T18:00:00")], [g("g1"), g("g2")]).size).toBe(0)
    expect(detectConflicts([sh("a", "g1", "2026-10-05T06:00:00", "2026-10-05T18:00:00"), sh("b", "g1", "2026-10-05T07:00:00", "2026-10-05T09:00:00", { status: "CANCELLED" }), sh("c", "g1", "2026-10-05T07:00:00", "2026-10-05T09:00:00", { status: "MISSED" })], [g("g1")]).size).toBe(0)
  })
  it("flags a scheduled shift after PSiRA expiry or for a guard who is not active", () => {
    const m = detectConflicts([sh("a", "g1", "2026-10-20T06:00:00", "2026-10-20T14:00:00"), sh("b", "g2", "2026-10-06T06:00:00", "2026-10-06T14:00:00")],
      [g("g1", { psiraExpiryDate: "2026-10-19" }), g("g2", { status: "ON_LEAVE" })])
    expect(m.get("a")?.[0]).toMatchObject({ kind: "PSIRA_EXPIRED", tone: "bad" })
    expect(m.get("b")?.[0]).toMatchObject({ kind: "GUARD_NOT_ACTIVE" })
    expect(m.get("b")?.[0].text).toBe("Guard is on leave")
  })
  it("does not flag PSiRA on the expiry day itself, nor on finished shifts", () => {
    expect(detectConflicts([sh("a", "g1", "2026-10-19T06:00:00", "2026-10-19T14:00:00")], [g("g1", { psiraExpiryDate: "2026-10-19" })]).size).toBe(0)
    expect(detectConflicts([sh("a", "g1", "2026-10-20T06:00:00", "2026-10-20T14:00:00", { status: "COMPLETED" })], [g("g1", { psiraExpiryDate: "2026-10-19" })]).size).toBe(0)
  })
})

describe("hours and cells", () => {
  const days = weekDays("2026-10-05")
  it("adds up scheduled hours for shifts starting this week and flags over 45", () => {
    const list = [0, 1, 2, 3, 4].map(i => sh(`s${i}`, "g1", `2026-10-0${5 + i}T06:00:00`, `2026-10-0${5 + i}T16:00:00`)) // 5 x 10h
    const h = weeklyHours(list, days)
    expect(h.get("g1")).toBe(50); expect(overHours(h.get("g1"))).toBe(true)
    expect(overHours(45)).toBe(false); expect(overHours(undefined)).toBe(false)
  })
  it("leaves cancelled shifts out of hours and out of the grid", () => {
    const list = [sh("a", "g1", "2026-10-05T06:00:00", "2026-10-05T14:00:00", { status: "CANCELLED" })]
    expect(weeklyHours(list, days).size).toBe(0); expect(buildCells(list, days).size).toBe(0)
  })
  it("puts a night shift on the day it starts and drops other weeks", () => {
    const cells = buildCells([sh("a", "g1", "2026-10-05T22:00:00", "2026-10-06T06:00:00"), sh("b", "g1", "2026-10-12T06:00:00", "2026-10-12T14:00:00")], days)
    expect(cells.get("g1")?.get("2026-10-05")?.map(s => s.id)).toEqual(["a"]); expect(cells.get("g1")?.has("2026-10-06")).toBe(false)
    expect([...(cells.get("g1")?.keys() ?? [])]).toEqual(["2026-10-05"])
  })
})

describe("grid rows", () => {
  const cells = buildCells([sh("a", "g2", "2026-10-06T06:00:00", "2026-10-06T14:00:00")], weekDays("2026-10-05"))
  const guards = [g("g1"), g("g2", { status: "ON_LEAVE" }), g("g3", { status: "TERMINATED" })]
  it("shows active guards and anyone with a shift this week", () => {
    expect(gridGuards(guards, cells, { search: "", siteId: "" }).map(x => x.id)).toEqual(["g1", "g2"])
  })
  it("filters by name and by site", () => {
    expect(gridGuards(guards, cells, { search: "g1", siteId: "" }).map(x => x.id)).toEqual(["g1"])
    expect(gridGuards(guards, cells, { search: "", siteId: "s1" }).map(x => x.id)).toEqual(["g2"])
    expect(gridGuards(guards, cells, { search: "", siteId: "other" })).toEqual([])
  })
})

describe("toInstants", () => {
  it("builds South African times and rolls a night shift to the next day", () => {
    expect(toInstants("2026-10-05", "06:00", "14:00")).toEqual({ startAt: "2026-10-05T04:00:00.000Z", endAt: "2026-10-05T12:00:00.000Z" })
    expect(toInstants("2026-10-05", "22:00", "06:00")).toEqual({ startAt: "2026-10-05T20:00:00.000Z", endAt: "2026-10-06T04:00:00.000Z" })
  })
  it("rejects blank, malformed and zero-length times", () => {
    expect(toInstants("2026-10-05", "", "14:00")).toBeNull(); expect(toInstants("2026-10-05", "6:00", "14:00")).toBeNull(); expect(toInstants("2026-10-05", "06:00", "06:00")).toBeNull()
  })
})
