import { describe, it, expect } from "vitest"
import { PRINT_CSS, printSignLine, printTitle, printedAt, allowedKinds, canSign, effectiveKind, signBody, signWarning, signedText, canGoNext, dayTitle, emptyText, entryBody, entryProblem, kindLabel, newClientRef, shiftDay, timeText, todaySast } from "./occurrenceBookLogic"

describe("occurrenceBookLogic", () => {
  it("today is the South African day, not the UTC day", () => {
    expect(todaySast(new Date("2026-11-20T23:30:00Z"))).toBe("2026-11-21")
    expect(todaySast(new Date("2026-11-20T21:30:00Z"))).toBe("2026-11-20")
  })
  it("moves a day across month and year ends", () => {
    expect(shiftDay("2026-03-01", -1)).toBe("2026-02-28")
    expect(shiftDay("2026-12-31", 1)).toBe("2027-01-01")
  })
  it("cannot go past today", () => {
    const now = new Date("2026-11-21T10:00:00Z")
    expect(canGoNext("2026-11-21", now)).toBe(false)
    expect(canGoNext("2026-11-20", now)).toBe(true)
  })
  it("shows times in South African time", () => {
    expect(timeText("2026-11-20T04:01:00Z")).toBe("06:01")
  })
  it("titles the day", () => { expect(dayTitle("2026-11-20")).toContain("20 November 2026") })
  it("checks entry text", () => {
    expect(entryProblem("ab")).not.toBeNull()
    expect(entryProblem("   ")).not.toBeNull()
    expect(entryProblem("abc")).toBeNull()
    expect(entryProblem("x".repeat(2000))).toBeNull()
    expect(entryProblem("x".repeat(2001))).not.toBeNull()
  })
  it("words kinds, falling back to the code", () => {
    expect(kindLabel("GATE_IN")).toBe("Gate in")
    expect(kindLabel("NEW_THING")).toBe("NEW_THING")
  })
  it("empty text differs for today", () => {
    expect(emptyText("2026-11-21", "2026-11-21")).toContain("yet today")
    expect(emptyText("2026-11-20", "2026-11-21")).toContain("on this day")
  })
  it("builds a trimmed body and unique refs", () => {
    expect(entryBody("NOTE", "  hi there ", "r")).toEqual({ kind: "NOTE", text: "hi there", clientRef: "r" })
    expect(newClientRef()).not.toBe(newClientRef())
  })

  it("a signed day allows corrections only", () => {
    expect(allowedKinds(true)).toEqual(["CORRECTION"])
    expect(allowedKinds(false)).toEqual(["NOTE", "HANDOVER", "CORRECTION"])
    expect(effectiveKind("NOTE", true)).toBe("CORRECTION")
    expect(effectiveKind("HANDOVER", false)).toBe("HANDOVER")
  })
  it("only managers may sign, and only an unsigned day", () => {
    expect(canSign(true, false)).toBe(true)
    expect(canSign(true, true)).toBe(false)
    expect(canSign(false, false)).toBe(false)
  })
  it("words the signature, with a fallback name", () => {
    expect(signedText({ signedBy: "u", signedByName: "Ann Smith", signedAt: "2026-11-20T20:10:00Z", note: null })).toBe("Signed off by Ann Smith on 20 Nov 2026, 22:10.")
    expect(signedText({ signedBy: null, signedByName: null, signedAt: "2026-11-20T20:10:00Z", note: null })).toContain("a supervisor")
  })
  it("warns differently for today", () => {
    expect(signWarning("2026-11-21", "2026-11-21")).toContain("Today is not over")
    expect(signWarning("2026-11-20", "2026-11-21")).not.toContain("Today")
  })
  it("sends the note only when there is one", () => {
    expect(signBody("  ")).toEqual({})
    expect(signBody(" quiet night ")).toEqual({ note: "quiet night" })
  })

  it("titles a printed day", () => {
    expect(printTitle("Mall", "2026-11-20")).toBe("Daily Occurrence Book — Mall — Friday, 20 November 2026")
  })
  it("an unsigned day says so on paper", () => {
    expect(printSignLine(null)).toBe("NOT SIGNED OFF.")
    expect(printSignLine(undefined)).toBe("NOT SIGNED OFF.")
    expect(printSignLine({ signedBy: "u", signedByName: "Ann", signedAt: "2026-11-20T20:10:00Z", note: "quiet" })).toContain("Signed off by Ann")
    expect(printSignLine({ signedBy: "u", signedByName: "Ann", signedAt: "2026-11-20T20:10:00Z", note: "quiet" })).toContain("Note: quiet")
  })
  it("print time is in South African time", () => {
    expect(printedAt(new Date("2026-11-20T20:10:00Z"))).toBe("Printed 20 Nov 2026, 22:10 (SAST)")
  })
  it("the print rules hide the screen copy and show only the book on paper", () => {
    expect(PRINT_CSS).toContain("@media screen { .ob-print { display: none; } }")
    expect(PRINT_CSS).toContain('body[data-printing="ob"] * { visibility: hidden !important; }')
    expect(PRINT_CSS).toContain('body[data-printing="ob"] .ob-print, body[data-printing="ob"] .ob-print * { visibility: visible !important; }')
  })
})
