import { afterEach, describe, it, expect } from "vitest"
import { DEFAULT_COMPANY_ZONE, abbreviationIn, dateTimeIn, dayIn, getCompanyZone, instantOf, monthIn, offsetMs, setCompanyZone, timeIn, usableZone } from "./companyZone"
import { todaySast, canGoNextWeek, restText } from "./weeklyHoursLogic"
import { currentMonthSast, lastMonthSast, canGoNextMonth, printedAt as packPrinted } from "./monthlyPackLogic"
import { canGoNext, printedAt as bookPrinted, signedText, timeText, todaySast as bookToday } from "./occurrenceBookLogic"
import { todayIso } from "./guard360.logic"

// 23:30 on 30 Nov in South Africa; 10:30 on 1 Dec in Auckland; 21:30 on 30 Nov in UTC.
const NOW = new Date("2026-11-30T21:30:00Z")
afterEach(() => setCompanyZone(undefined))

describe("zone helpers", () => {
  it("reads the day and month in the given zone", () => {
    expect(dayIn("Africa/Johannesburg", NOW)).toBe("2026-11-30")
    expect(dayIn("Pacific/Auckland", NOW)).toBe("2026-12-01")
    expect(dayIn("UTC", NOW)).toBe("2026-11-30")
    expect(monthIn("Pacific/Auckland", NOW)).toBe("2026-12")
    expect(monthIn("Africa/Johannesburg", NOW)).toBe("2026-11")
  })
  it("names the zone and writes clock times in it", () => {
    expect(abbreviationIn("Africa/Johannesburg", NOW)).toBe("SAST")
    expect(abbreviationIn("UTC", NOW)).toBe("UTC")
    expect(timeIn("Africa/Johannesburg", NOW.toISOString())).toBe("23:30")
    expect(timeIn("Pacific/Auckland", NOW.toISOString())).toBe("10:30")
    expect(dateTimeIn("Pacific/Auckland", NOW)).toMatch(/1 Dec 2026/)
    expect(dateTimeIn("Pacific/Auckland", NOW, false)).not.toMatch(/2026/)
  })
  it("only usable zones are remembered; anything else is South Africa, never the browser's", () => {
    expect(getCompanyZone()).toBe(DEFAULT_COMPANY_ZONE)
    setCompanyZone("Pacific/Auckland"); expect(getCompanyZone()).toBe("Pacific/Auckland")
    setCompanyZone("Mars/Olympus"); expect(getCompanyZone()).toBe(DEFAULT_COMPANY_ZONE)
    setCompanyZone("Pacific/Auckland"); setCompanyZone(42); expect(getCompanyZone()).toBe(DEFAULT_COMPANY_ZONE)
    setCompanyZone("Pacific/Auckland"); setCompanyZone(""); expect(getCompanyZone()).toBe(DEFAULT_COMPANY_ZONE)
    expect(usableZone("a".repeat(61))).toBe(false)
    expect(usableZone(null)).toBe(false)
  })
})

describe("the existing date helpers follow the company zone", () => {
  it("all say South Africa's day before a zone is set", () => {
    expect(todaySast(NOW)).toBe("2026-11-30")
    expect(bookToday(NOW)).toBe("2026-11-30")
    expect(todayIso(NOW)).toBe("2026-11-30")
    expect(currentMonthSast(NOW)).toBe("2026-11")
    expect(lastMonthSast(NOW)).toBe("2026-10")
  })
  it("all say the company's day once it is set", () => {
    setCompanyZone("Pacific/Auckland")
    expect(todaySast(NOW)).toBe("2026-12-01")
    expect(bookToday(NOW)).toBe("2026-12-01")
    expect(todayIso(NOW)).toBe("2026-12-01")
    expect(currentMonthSast(NOW)).toBe("2026-12")
    expect(lastMonthSast(NOW)).toBe("2026-11")
  })
  it("next-week, next-month and next-day limits follow it", () => {
    expect(canGoNextMonth("2026-11", NOW)).toBe(false)
    expect(canGoNext("2026-11-30", NOW)).toBe(false)
    const SUN = new Date("2026-11-29T21:30:00Z") // Sunday 23:30 in South Africa, Monday 10:30 in Auckland
    expect(canGoNextWeek("2026-11-23", SUN)).toBe(false)
    setCompanyZone("Pacific/Auckland")
    expect(canGoNextMonth("2026-11", NOW)).toBe(true)
    expect(canGoNext("2026-11-30", NOW)).toBe(true)
    expect(canGoNextWeek("2026-11-23", SUN)).toBe(true)
  })
  it("wording of clock times and the printed stamp use the company zone and name it", () => {
    expect(timeText("2026-11-30T21:30:00Z")).toBe("23:30")
    expect(bookPrinted(NOW)).toMatch(/\(SAST\)$/)
    expect(packPrinted(NOW)).toMatch(/\(SAST\)$/)
    expect(restText({ from: "2026-11-30T21:30:00Z", to: "2026-12-01T01:30:00Z", hours: 4 })).toMatch(/Mon 23:30 to Tue 03:30/)
    expect(signedText({ signedBy: "u", signedByName: "Ann", signedAt: "2026-11-30T21:30:00Z", note: null })).toMatch(/^Signed off by Ann on 0?30 Nov 2026, 23:30\.$/)
    setCompanyZone("Pacific/Auckland")
    expect(signedText({ signedBy: "u", signedByName: "Ann", signedAt: "2026-11-30T21:30:00Z", note: null })).toMatch(/^Signed off by Ann on 0?1 Dec 2026, 10:30\.$/)
    expect(timeText("2026-11-30T21:30:00Z")).toBe("10:30")
    expect(bookPrinted(NOW)).toMatch(/1 Dec 2026.*10:30 \(GMT\+13\)$/)
    expect(packPrinted(NOW)).toMatch(/\(GMT\+13\)$/)
    expect(restText({ from: "2026-11-30T21:30:00Z", to: "2026-12-01T01:30:00Z", hours: 4 })).toMatch(/Tue 10:30 to Tue 14:30/)
  })
})

describe("instantOf and offsetMs", () => {
  const H = 3_600_000
  it("knows the offset of a zone at a moment", () => {
    expect(offsetMs("Africa/Johannesburg", Date.parse("2026-11-22T10:00:00Z"))).toBe(2 * H)
    expect(offsetMs("Pacific/Auckland", Date.parse("2026-11-22T10:00:00Z"))).toBe(13 * H)
    expect(offsetMs("Pacific/Auckland", Date.parse("2026-07-01T10:00:00Z"))).toBe(12 * H)
    expect(offsetMs("UTC", Date.parse("2026-07-01T10:00:00.500Z"))).toBe(0)
    expect(offsetMs("Asia/Kolkata", Date.parse("2026-07-01T10:00:00Z"))).toBe(5.5 * H)
  })
  it("finds the instant a clock reads a time", () => {
    expect(instantOf("Africa/Johannesburg", "2026-11-22").toISOString()).toBe("2026-11-21T22:00:00.000Z")
    expect(instantOf("Pacific/Auckland", "2026-11-30").toISOString()).toBe("2026-11-29T11:00:00.000Z")
    expect(instantOf("Pacific/Auckland", "2026-11-30", "18:30").toISOString()).toBe("2026-11-30T05:30:00.000Z")
    expect(instantOf("Asia/Kolkata", "2026-07-01", "09:15").toISOString()).toBe("2026-07-01T03:45:00.000Z")
    expect(instantOf("UTC", "2026-07-01", "23:59").toISOString()).toBe("2026-07-01T23:59:00.000Z")
  })
  it("is right either side of a clock change", () => {
    expect(instantOf("Europe/London", "2026-07-01").toISOString()).toBe("2026-06-30T23:00:00.000Z") // summer time
    expect(instantOf("Europe/London", "2026-12-01").toISOString()).toBe("2026-12-01T00:00:00.000Z")
    expect(instantOf("Europe/London", "2026-03-29").toISOString()).toBe("2026-03-29T00:00:00.000Z") // the day the clocks go forward at 01:00
    expect(instantOf("Europe/London", "2026-03-30").toISOString()).toBe("2026-03-29T23:00:00.000Z")
    expect(instantOf("Europe/London", "2026-10-26").toISOString()).toBe("2026-10-26T00:00:00.000Z")
    expect(instantOf("Europe/London", "2026-10-25").toISOString()).toBe("2026-10-24T23:00:00.000Z") // the day they go back
  })
  it("is right just after a clock change in a zone behind UTC (the first guess needs correcting)", () => {
    expect(instantOf("America/New_York", "2026-03-08", "03:30").toISOString()).toBe("2026-03-08T07:30:00.000Z") // 03:30 EDT, an hour after clocks went forward
    expect(instantOf("America/New_York", "2026-03-08", "01:30").toISOString()).toBe("2026-03-08T06:30:00.000Z") // still EST
    expect(instantOf("America/New_York", "2026-11-01", "03:30").toISOString()).toBe("2026-11-01T08:30:00.000Z") // 03:30 EST, after clocks went back
    expect(instantOf("America/New_York", "2026-11-01", "00:30").toISOString()).toBe("2026-11-01T04:30:00.000Z") // still EDT
  })
  it("a day with a clock change is 23 or 25 hours long", () => {
    const len = (z: string, d: string, n: string) => (instantOf(z, n).getTime() - instantOf(z, d).getTime()) / H
    expect(len("Europe/London", "2026-03-29", "2026-03-30")).toBe(23)
    expect(len("Europe/London", "2026-10-25", "2026-10-26")).toBe(25)
    expect(len("Africa/Johannesburg", "2026-03-29", "2026-03-30")).toBe(24)
  })
})
