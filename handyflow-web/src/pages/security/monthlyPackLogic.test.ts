import { describe, it, expect } from "vitest"
import { PRINT_CSS, bookText, canGoNextMonth, coverDetail, coverText, currentMonthSast, incidentText, lastMonthSast, monthTitle, patrolText, printTitle, shiftLines, shiftMonth, visitorText, type Pack, type PackCover } from "./monthlyPackLogic"

const cover = (o: Partial<PackCover> = {}): PackCover => ({ posts: 1, measured: true, postDays: 4, requiredGuardHours: 48, coveredGuardHours: 47, coveragePct: 97.9, postDaysShort: 1, postDaysUncovered: 0, ...o })

describe("monthlyPackLogic", () => {
  it("the current month is the South African month", () => {
    expect(currentMonthSast(new Date("2026-11-30T23:30:00Z"))).toBe("2026-12")
    expect(currentMonthSast(new Date("2026-11-30T21:30:00Z"))).toBe("2026-11")
  })
  it("last month crosses the year end", () => {
    expect(lastMonthSast(new Date("2026-11-21T10:00:00Z"))).toBe("2026-10")
    expect(lastMonthSast(new Date("2026-01-10T10:00:00Z"))).toBe("2025-12")
  })
  it("shifts months both ways", () => {
    expect(shiftMonth("2026-12", 1)).toBe("2027-01")
    expect(shiftMonth("2026-01", -1)).toBe("2025-12")
    expect(shiftMonth("2026-03", -14)).toBe("2025-01")
  })
  it("cannot go past the current month", () => {
    const now = new Date("2026-11-21T10:00:00Z")
    expect(canGoNextMonth("2026-11", now)).toBe(false)
    expect(canGoNextMonth("2026-10", now)).toBe(true)
  })
  it("titles a month", () => { expect(monthTitle("2026-10")).toBe("October 2026") })

  it("words cover, and never claims coverage when there are no posts", () => {
    expect(coverText(cover())).toBe("47 of 48 required guard-hours covered (97.9%).")
    expect(coverText(cover({ measured: false, postDays: 0 }))).toContain("not measured")
    expect(coverText(cover({ postDays: 0 }))).toBe("No post-days had finished in this period.")
    expect(coverText(cover({ coveragePct: null }))).toBe("47 of 48 required guard-hours covered.")
  })
  it("details short and unrostered post-days only when there are some", () => {
    expect(coverDetail(cover())).toBe("4 post-days measured, 1 short.")
    expect(coverDetail(cover({ postDaysShort: 0 }))).toBe("4 post-days measured.")
    expect(coverDetail(cover({ postDaysUncovered: 2 }))).toBe("4 post-days measured, 1 short, 2 with no one rostered.")
    expect(coverDetail(cover({ measured: false }))).toBe("")
  })
  it("words patrols", () => {
    expect(patrolText({ rounds: 0, complete: 0, partial: 0, missed: 0, checkpointsExpected: 0, checkpointsScanned: 0, checkpointPct: null })).toBe("No patrol rounds were due.")
    expect(patrolText({ rounds: 3, complete: 1, partial: 1, missed: 1, checkpointsExpected: 15, checkpointsScanned: 8, checkpointPct: 53.3 })).toBe("1 of 3 rounds complete, 1 partial, 1 missed. 8 of 15 checkpoints scanned (53.3%).")
  })
  it("words incidents in severity order", () => {
    expect(incidentText({ total: 0, resolved: 0, bySeverity: {} })).toBe("No incidents.")
    expect(incidentText({ total: 3, resolved: 1, bySeverity: { LOW: 1, HIGH: 2 } })).toBe("3 incidents (2 High, 1 Low), 1 resolved.")
    expect(incidentText({ total: 1, resolved: 0, bySeverity: { CRITICAL: 1 } })).toBe("1 incident (1 Critical), 0 resolved.")
  })
  it("words visitors", () => {
    expect(visitorText({ total: 0, withVehicle: 0, byType: {} })).toBe("No visitors or contractors logged.")
    expect(visitorText({ total: 3, withVehicle: 1, byType: { VISITOR: 2, CONTRACTOR: 1 } })).toBe("3 logged (1 Contractor, 2 Visitor), 1 with a vehicle.")
  })
  it("words the Occurrence Book and shifts", () => {
    expect(bookText({ daysInPeriod: 31, daysSigned: 28 })).toBe("Occurrence Book signed off on 28 of 31 days.")
    expect(shiftLines({ COMPLETED: 20, MISSED: 2, CANCELLED: 0 })).toEqual([{ label: "Completed", n: 20 }, { label: "Missed", n: 2 }])
  })
  it("titles the printout with the site and month", () => {
    expect(printTitle({ siteName: "Mall", month: "2026-10" } as Pack)).toBe("Monthly service report — Mall — October 2026")
  })
  it("uses its own print key", () => { expect(PRINT_CSS).toContain('body[data-printing="mp"]') })
})
