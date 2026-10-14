import { describe, it, expect } from "vitest"
import { emptyLetter, emptySchedule, fmtR, letterActions, letterProblem, letterRequest, overBudget, realisationText, scheduleProblem, scheduleRequest } from "./practice.logic"

describe("letters", () => {
  it("offers the right actions in each status", () => {
    expect(letterActions("DRAFT")).toEqual(["edit", "send", "view"])
    expect(letterActions("SENT")).toEqual(["accept", "decline", "view"])
    expect(letterActions("ACCEPTED")).toEqual(["end", "view"])
    expect(letterActions("SUPERSEDED")).toEqual(["view"])
  })
  it("needs scope and, for a retainer, an amount", () => {
    const f = emptyLetter("2026-11-01")
    expect(letterProblem(f)).toMatch(/services/)
    expect(letterProblem({ ...f, scope: "Bookkeeping" })).toMatch(/amount/)
    expect(letterProblem({ ...f, scope: "Bookkeeping", feeAmount: "1500" })).toBeNull()
    expect(letterProblem({ ...f, scope: "x", feeBasis: "HOURLY" })).toBeNull()
    expect(letterProblem({ ...f, scope: "x", feeAmount: "1", endDate: "2026-10-01" })).toMatch(/end date/)
  })
  it("builds the request with blanks as null", () => {
    expect(letterRequest({ ...emptyLetter("2026-11-01"), scope: " Books ", feeAmount: "1 500".replace(" ", "") })).toMatchObject({ scope: "Books", exclusions: null, feeAmount: 1500, endDate: null })
  })
})

describe("schedules", () => {
  it("needs a client and builds the request", () => {
    const f = { ...emptySchedule("2026-11-01"), feeAmount: "1500,50", budgetHours: "6" }
    expect(scheduleProblem(f)).toMatch(/client/)
    const ok = { ...f, clientId: "c1" }
    expect(scheduleProblem(ok)).toBeNull()
    expect(scheduleRequest(ok)).toMatchObject({ serviceType: "BOOKKEEPING", frequency: "MONTHLY", feeAmount: 1500.5, budgetHours: 6, ownerUserId: null, dueDays: 30 })
  })
})

describe("wording", () => {
  it("formats money, budget use and realisation", () => {
    expect(fmtR(-2500)).toMatch(/^-R .*2.?500[.,]00$/)
    expect(overBudget(120)).toBe(true); expect(overBudget(100)).toBe(false); expect(overBudget(null)).toBe(false)
    expect(realisationText(80)).toBe("80% of standard value"); expect(realisationText(null)).toBe("no time recorded")
  })
})
