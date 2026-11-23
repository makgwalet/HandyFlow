import { describe, it, expect } from "vitest"
import {
  addDayError, failureText, holidayDate, multiplierError, multiplierText, multiplierValid, parseMultiplier, rateExample, rowAction, settingsError,
  sourceLabel, toSaveRequest, yearChoices, SAVE_FAILED,
} from "./payPremiumLogic"

describe("multipliers", () => {
  it("reads plain numbers and a decimal comma", () => {
    expect(parseMultiplier("1.5")).toBe(1.5)
    expect(parseMultiplier(" 2 ")).toBe(2)
    expect(parseMultiplier("1,75")).toBe(1.75)
  })
  it("refuses anything else", () => {
    for (const bad of ["", "abc", "1.505", "-2", "1.5x", "1..5", ".5", "1e2"]) expect(parseMultiplier(bad), bad).toBeNull()
  })
  it("accepts the two ends and refuses outside them", () => {
    expect(multiplierValid("1.5")).toBe(true)
    expect(multiplierValid("4")).toBe(true)
    expect(multiplierValid("1.49")).toBe(false)
    expect(multiplierValid("4.01")).toBe(false)
    expect(multiplierValid("15")).toBe(false)
  })
  it("names the field in the message", () => {
    expect(multiplierError("Sunday rate", "1.2")).toContain("Sunday rate")
    expect(multiplierError("Sunday rate", "1.2")).toContain("overtime rate")
    expect(multiplierError("Sunday rate", "x")).toContain("at most two decimals")
    expect(multiplierError("Sunday rate", "2")).toBeNull()
  })
  it("checks both, Sunday first", () => {
    expect(settingsError("1.5", "2")).toBeNull()
    expect(settingsError("1", "1")).toContain("Sunday rate")
    expect(settingsError("1.5", "9")).toContain("Public holiday rate")
  })
  it("builds the request with numbers", () => {
    expect(toSaveRequest(true, "1,5", "2")).toEqual({ enabled: true, sundayMultiplier: 1.5, holidayMultiplier: 2 })
  })
  it("shows two decimals", () => { expect(multiplierText(1.5)).toBe("1.50") })
})

describe("what it means in money", () => {
  it("rounds half up to the cent like the server", () => {
    expect(rateExample(10000, 1.5)).toBe("R 100.00 an hour becomes R 150.00")
    expect(rateExample(10000, 2.25)).toBe("R 100.00 an hour becomes R 225.00")
    expect(rateExample(1001, 1.5)).toBe("R 10.01 an hour becomes R 15.02")
  })
})

describe("holiday rows", () => {
  it("words the source", () => {
    expect(sourceLabel("NATIONAL")).toBe("National")
    expect(sourceLabel("ADDED")).toBe("Added by you")
    expect(sourceLabel("REMOVED")).toBe("Taken off by you")
  })
  it("gives each source its own action", () => {
    expect(rowAction("NATIONAL")).toEqual({ kind: "REMOVE", label: "Take off" })
    expect(rowAction("REMOVED")).toEqual({ kind: "RESTORE", label: "Put back" })
    expect(rowAction("ADDED")).toEqual({ kind: "CLEAR", label: "Delete" })
  })
  it("shows the date as written, not shifted by the viewer's time zone", () => {
    expect(holidayDate("2026-12-25")).toContain("25")
    expect(holidayDate("2026-12-25")).toContain("2026")
    expect(holidayDate("2026-01-01")).toContain("1")
    expect(holidayDate("junk")).toBe("junk")
  })
  it("offers last year to two years ahead", () => {
    expect(yearChoices(new Date(2026, 10, 23))).toEqual([2025, 2026, 2027, 2028])
  })
})

describe("adding a day", () => {
  it("needs a date and a name of up to 100 characters", () => {
    expect(addDayError("", "x")).toBe("Pick a date.")
    expect(addDayError("2026-12-30", "  ")).toBe("Give the day a name.")
    expect(addDayError("2026-12-30", "x".repeat(101))).toContain("100")
    expect(addDayError("2026-12-30", "x".repeat(100))).toBeNull()
  })
})

describe("failures", () => {
  it("uses the server's words when it sends some", () => {
    expect(failureText({ response: { data: { message: "Sunday multiplier must be between 1.50 and 4.00." } } }, SAVE_FAILED)).toContain("between")
  })
  it("falls back when there is nothing usable", () => {
    expect(failureText(new Error("x"), SAVE_FAILED)).toBe(SAVE_FAILED)
    expect(failureText({ response: { data: { message: "  " } } }, SAVE_FAILED)).toBe(SAVE_FAILED)
    expect(failureText(null, SAVE_FAILED)).toBe(SAVE_FAILED)
  })
})
