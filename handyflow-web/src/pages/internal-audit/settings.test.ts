import { describe, it, expect } from "vitest"
import { bandsFrom, problems, sameValues, toNumber } from "./settings.logic"

const ok = { lowMax: 10, mediumMax: 15, highMax: 20, escalateAfterDays: 14, repeatEveryDays: 7, firstReviewReminderDay: 3 }

describe("settings form checks", () => {
  it("accepts the standard rules and the extremes", () => {
    expect(problems(ok)).toEqual([])
    expect(problems({ ...ok, lowMax: 5, mediumMax: 6, highMax: 24, escalateAfterDays: 2, repeatEveryDays: 1, firstReviewReminderDay: 1 })).toEqual([])
    expect(problems({ ...ok, escalateAfterDays: 90, repeatEveryDays: 30, firstReviewReminderDay: 30 })).toEqual([])
  })
  it("rejects bands that do not rise or leave no room for critical", () => {
    expect(problems({ ...ok, lowMax: 4 })).toHaveLength(1)
    expect(problems({ ...ok, mediumMax: 10 })).toHaveLength(1)
    expect(problems({ ...ok, highMax: 15 })).toHaveLength(1)
    expect(problems({ ...ok, highMax: 25 })).toHaveLength(1)
  })
  it("rejects days out of range and blank or fractional boxes", () => {
    expect(problems({ ...ok, escalateAfterDays: 1 })).toHaveLength(1)
    expect(problems({ ...ok, repeatEveryDays: 0 })).toHaveLength(1)
    expect(problems({ ...ok, firstReviewReminderDay: 31 })).toHaveLength(1)
    expect(problems({ ...ok, lowMax: toNumber("") })).toEqual(["Enter whole numbers in every box"])
    expect(problems({ ...ok, lowMax: 10.5 })).toEqual(["Enter whole numbers in every box"])
  })
  it("previews the four bands", () => {
    expect(bandsFrom({ ...ok, lowMax: 8, mediumMax: 13, highMax: 18 }).map(b => `${b.level} ${b.from}-${b.to}`)).toEqual(["LOW 5-8", "MEDIUM 9-13", "HIGH 14-18", "CRITICAL 19-25"])
  })
  it("compares values and reads boxes", () => {
    expect(sameValues(ok, { ...ok })).toBe(true)
    expect(sameValues(ok, { ...ok, repeatEveryDays: 5 })).toBe(false)
    expect(toNumber(" 12 ")).toBe(12)
    expect(Number.isNaN(toNumber("x"))).toBe(true)
  })
})
