import { describe, it, expect } from "vitest"
import { COMMON_ZONES, errorText, shiftText, validEffect, validView, zoneProblem } from "./companyZoneLogic"

describe("zoneProblem", () => {
  it("accepts region names and UTC", () => {
    for (const z of ["Africa/Windhoek", "Pacific/Auckland", "UTC", "America/Argentina/Buenos_Aires"]) expect(zoneProblem(z, "Africa/Johannesburg")).toBeNull()
  })
  it("asks for a zone when empty", () => {
    expect(zoneProblem("", null)).toBe("Choose a time zone.")
    expect(zoneProblem("   ", null)).toBe("Choose a time zone.")
  })
  it("refuses unknown names, odd characters and over-long text", () => {
    expect(zoneProblem("Mars/Olympus", null)).toMatch(/not a known time zone/)
    expect(zoneProblem("Africa/Johannesburg'; DROP", null)).toMatch(/not a known time zone/)
    expect(zoneProblem("a".repeat(61), null)).toMatch(/not a known time zone/)
  })
  it("refuses offsets", () => {
    expect(zoneProblem("+02:00", null)).toBeTruthy()
    expect(zoneProblem("GMT+2", null)).toBeTruthy()
    expect(zoneProblem("Etc/GMT-2", null)).toMatch(/region name/)
  })
  it("refuses the zone the company already has", () => {
    expect(zoneProblem("Africa/Johannesburg", "Africa/Johannesburg")).toBe("The company is already on that time zone.")
    expect(zoneProblem(" Africa/Johannesburg ", "Africa/Johannesburg")).toBe("The company is already on that time zone.")
  })
  it("every suggested zone is acceptable", () => {
    for (const z of COMMON_ZONES) expect(zoneProblem(z, null), z).toBeNull()
  })
})

describe("shiftText", () => {
  it("words the move", () => {
    expect(shiftText(660)).toBe("11 hours forward")
    expect(shiftText(60)).toBe("1 hour forward")
    expect(shiftText(-120)).toBe("2 hours back")
    expect(shiftText(330)).toBe("5 h 30 min forward")
    expect(shiftText(-45)).toBe("45 minutes back")
    expect(shiftText(0)).toBe("no change in the clock offset right now")
  })
})

describe("validView / validEffect", () => {
  it("keeps a good view and drops a bad one", () => {
    expect(validView({ timeZone: "UTC", abbreviation: "UTC", now: "x", today: "2026-11-22" })?.timeZone).toBe("UTC")
    expect(validView({ timeZone: 3 })).toBeNull()
    expect(validView(null)).toBeNull()
    expect(validView("x")).toBeNull()
  })
  it("keeps a good effect and drops a bad one", () => {
    expect(validEffect({ from: "a", to: "b", offsetChangeMinutes: 60, todayChangesNow: true, warning: "w" })?.todayChangesNow).toBe(true)
    expect(validEffect({ from: "a", to: "b", offsetChangeMinutes: "60", warning: "w" })).toBeNull()
    expect(validEffect(undefined)).toBeNull()
  })
})

describe("errorText", () => {
  it("uses a short server message, else the fallback", () => {
    expect(errorText({ response: { data: { message: "No." } } }, "f")).toBe("No.")
    expect(errorText({ response: { data: { message: "x".repeat(300) } } }, "f")).toBe("f")
    expect(errorText(new Error("boom"), "f")).toBe("f")
  })
})
