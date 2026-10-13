import { describe, expect, it } from "vitest"
import { gridHours, offsetFor } from "./scheduleHours"

// Local-time ISO strings (no zone), so the tests do not depend on the machine's time zone.
describe("gridHours", () => {
  it("shows 07:00 to 19:00 when everything falls inside it", () => {
    const g = gridHours([{ scheduledAt: "2026-10-13T09:00:00", durationMinutes: 30 }])
    expect([g.start, g.end, g.hours.length]).toEqual([7, 19, 12])
  })
  it("stretches to an early or late appointment instead of hiding it", () => {
    const g = gridHours([{ scheduledAt: "2026-10-13T06:15:00", durationMinutes: 30 }, { scheduledAt: "2026-10-13T19:30:00", durationMinutes: 45 }])
    expect([g.start, g.end]).toEqual([6, 21])
  })
  it("never runs past midnight and ignores unreadable times", () => {
    const g = gridHours([{ scheduledAt: "2026-10-13T23:30:00", durationMinutes: 120 }, { scheduledAt: "nonsense" }])
    expect([g.start, g.end]).toEqual([7, 24])
  })
})

describe("offsetFor", () => {
  it("measures from the grid's first hour", () => {
    expect(offsetFor("2026-10-13T07:30:00", 7, 64)).toBe(32)
    expect(offsetFor("2026-10-13T06:00:00", 6, 64)).toBe(0)
  })
})
