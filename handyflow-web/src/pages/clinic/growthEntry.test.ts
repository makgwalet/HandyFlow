import { describe, it, expect } from "vitest"
import { entryProblem, takenAtFor, todayZA } from "./growthEntry"

const now = new Date("2026-10-08T10:00:00Z")
describe("growth back-entry", () => {
  it("takes noon South African time for a past day and now for today", () => {
    expect(takenAtFor("2025-01-10", now)).toBe("2025-01-10T10:00:00.000Z")
    expect(takenAtFor("2026-10-08", now)).toBe(now.toISOString())
    expect(todayZA(now)).toBe("2026-10-08")
  })
  it("needs a positive number and a real date that is not in the future, and applies no clinical limits", () => {
    expect(entryProblem("", "2025-01-10", now)).toMatch(/number above zero/)
    expect(entryProblem("0", "2025-01-10", now)).toMatch(/number above zero/)
    expect(entryProblem("abc", "2025-01-10", now)).toMatch(/number above zero/)
    expect(entryProblem("9,5", "", now)).toMatch(/Choose the date/)
    expect(entryProblem("9.5", "2026-10-09", now)).toMatch(/future/)
    expect(entryProblem("9,5", "2025-01-10", now)).toBeNull()
    expect(entryProblem("250", "2025-01-10", now)).toBeNull()
  })
})
