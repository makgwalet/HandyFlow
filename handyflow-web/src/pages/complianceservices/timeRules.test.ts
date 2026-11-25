import { describe, it, expect } from "vitest"
import { entryProblem, formatMinutes, isoDay, kindLabel, marginNote, monthToDate, parseDuration, reasonProblem, type EntryForm } from "./timeRules"

const form = (over: Partial<EntryForm> = {}): EntryForm => ({ clientId: "c1", workDate: "2026-11-25", duration: "1h 30m", serviceCode: "", kind: "PLANNED", billable: true, note: "", ...over })

describe("timeRules", () => {
  it("reads the ways people write a duration", () => {
    expect(parseDuration("1:30")).toBe(90)
    expect(parseDuration("1h 30m")).toBe(90)
    expect(parseDuration("1h30")).toBe(90)
    expect(parseDuration("45m")).toBe(45)
    expect(parseDuration("1.5h")).toBe(90)
    expect(parseDuration("2h")).toBe(120)
    expect(parseDuration("90")).toBe(90)
  })
  it("refuses what it cannot read or is out of range", () => {
    expect(parseDuration("")).toBeNull()
    expect(parseDuration("abc")).toBeNull()
    expect(parseDuration("0")).toBeNull()
    expect(parseDuration("13h")).toBeNull()
    expect(parseDuration("-5m")).toBeNull()
    expect(parseDuration("12h")).toBe(720)
  })
  it("writes minutes back in words", () => {
    expect(formatMinutes(90)).toBe("1h 30m")
    expect(formatMinutes(45)).toBe("45m")
    expect(formatMinutes(120)).toBe("2h")
  })
  it("checks the form before sending", () => {
    expect(entryProblem(form(), "2026-11-25")).toBe("")
    expect(entryProblem(form({ clientId: "" }), "2026-11-25")).not.toBe("")
    expect(entryProblem(form({ workDate: "2026-11-26" }), "2026-11-25")).toMatch(/not happened/)
    expect(entryProblem(form({ duration: "lots" }), "2026-11-25")).toMatch(/how long/)
  })
  it("wants a real reason", () => {
    expect(reasonProblem("oops")).not.toBe("")
    expect(reasonProblem("Entered against the wrong client")).toBe("")
  })
  it("starts the report at the first of the month", () => {
    expect(monthToDate("2026-11-25")).toEqual({ from: "2026-11-01", to: "2026-11-25" })
    expect(isoDay(new Date(2026, 10, 5))).toBe("2026-11-05")
  })
  it("flags a margin that cannot be trusted or is a loss", () => {
    expect(marginNote({ costComplete: false, margin: 10, invoiced: 100 })).toMatch(/no cost rate/)
    expect(marginNote({ costComplete: true, margin: 0, invoiced: 0 })).toBe("Nothing invoiced")
    expect(marginNote({ costComplete: true, margin: -5, invoiced: 100 })).toBe("Loss")
    expect(marginNote({ costComplete: true, margin: 50, invoiced: 100 })).toBe("")
    expect(kindLabel("REWORK")).toBe("Rework")
  })
})
