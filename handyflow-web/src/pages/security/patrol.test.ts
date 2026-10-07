import { describe, it, expect } from "vitest"
import { canAcknowledge, filterRounds, needsAttention, presetWindow, progress, summarise, unscanned, type PatrolRound } from "./patrol.logic"

const r = (over: Partial<PatrolRound> = {}): PatrolRound => ({
  id: "r", shiftId: "s", siteId: "x", siteName: "Centurion Mall", routeName: "Perimeter", guardName: "Nomsa Dlamini", roundNumber: 1, status: "COMPLETE",
  expectedStartAt: null, expectedEndAt: null, startedAt: null, completedAt: null, checkpointsExpected: 4, checkpointsScanned: 4,
  offSchedule: false, offScheduleReason: null, acknowledged: false, ...over,
})

describe("progress", () => {
  it("is a capped percentage and 0 for a route with no checkpoints", () => {
    expect(progress({ checkpointsScanned: 3, checkpointsExpected: 4 })).toBe(75)
    expect(progress({ checkpointsScanned: 5, checkpointsExpected: 4 })).toBe(100)
    expect(progress({ checkpointsScanned: 0, checkpointsExpected: 0 })).toBe(0)
  })
})

describe("summarise", () => {
  it("counts by status and rates completion over finished rounds only", () => {
    const s = summarise([r(), r(), r({ status: "PARTIAL" }), r({ status: "MISSED" }), r({ status: "EXPECTED" }), r({ status: "IN_PROGRESS" })])
    expect(s).toMatchObject({ total: 6, complete: 2, partial: 1, missed: 1, inProgress: 1, notStarted: 1, completionRate: 50, needAttention: 2 })
  })
  it("has no rate when nothing has finished", () => {
    expect(summarise([r({ status: "EXPECTED" })]).completionRate).toBeNull()
    expect(summarise([]).completionRate).toBeNull()
  })
  it("counts off-schedule rounds and leaves acknowledged ones out of attention", () => {
    const s = summarise([r({ offSchedule: true }), r({ status: "MISSED", acknowledged: true })])
    expect(s.offSchedule).toBe(1); expect(s.needAttention).toBe(0)
  })
})

describe("attention and acknowledgement", () => {
  it("applies to missed or partial rounds not yet acknowledged", () => {
    expect(needsAttention(r({ status: "MISSED" }))).toBe(true)
    expect(needsAttention(r({ status: "PARTIAL", acknowledged: true }))).toBe(false)
    expect(canAcknowledge(r({ status: "COMPLETE" }))).toBe(false)
    expect(canAcknowledge(r({ status: "PARTIAL" }))).toBe(true)
  })
})

describe("filterRounds", () => {
  const list = [r({ id: "a", guardName: "Nomsa Dlamini" }), r({ id: "b", guardName: "Bafana Khumalo", status: "MISSED", siteName: "Sandton Estate" })]
  it("searches guard, site and route", () => {
    expect(filterRounds(list, { search: "bafana", attention: false }).map(x => x.id)).toEqual(["b"])
    expect(filterRounds(list, { search: "sandton", attention: false }).map(x => x.id)).toEqual(["b"])
    expect(filterRounds(list, { search: "perimeter", attention: false }).length).toBe(2)
  })
  it("can show only what needs attention", () => { expect(filterRounds(list, { search: "", attention: true }).map(x => x.id)).toEqual(["b"]) })
})

describe("presetWindow", () => {
  const now = new Date("2026-10-07T10:00:00+02:00")
  it("covers today for 'Today' and seven days ending today", () => {
    expect(presetWindow(1, now).from.toISOString()).toBe("2026-10-06T22:00:00.000Z")
    expect(presetWindow(1, now).to.toISOString()).toBe("2026-10-07T22:00:00.000Z")
    expect(presetWindow(7, now).from.toISOString()).toBe("2026-09-30T22:00:00.000Z")
  })
  it("stays inside the server's 31-day limit for 30 days", () => {
    const w = presetWindow(30, now); expect((w.to.getTime() - w.from.getTime()) / 86400000).toBe(30)
  })
})

describe("unscanned", () => {
  it("lists the checkpoints with no scan, in order", () => {
    const cps = [{ id: "1", name: "A", sequence: 1, scannedAt: "x", method: "QR", scannedBy: "N" }, { id: "2", name: "B", sequence: 2, scannedAt: null, method: null, scannedBy: null }]
    expect(unscanned(cps).map(c => c.name)).toEqual(["B"])
  })
})
