import { describe, it, expect } from "vitest"
import { recallsUrl, dateInDays, pageCount, contactSummary, snoozeProblem, PAGE_SIZE } from "./recalls"

describe("recalls helpers", () => {
  it("builds a server query and omits empty search / doctor", () => {
    expect(recallsUrl({ q: " ", filter: "ALL", practitionerId: "", page: 0 })).toBe(`/api/v1/clinic/recalls?filter=ALL&page=0&size=${PAGE_SIZE}`)
    const u = recallsUrl({ q: " ada ", filter: "OVERDUE", practitionerId: "d1", page: 2 })
    expect(u).toContain("q=ada"); expect(u).toContain("practitionerId=d1"); expect(u).toContain("page=2"); expect(u).toContain("filter=OVERDUE")
  })
  it("adds days across month ends", () => {
    expect(dateInDays(3, new Date(2026, 9, 30))).toBe("2026-11-02")
    expect(dateInDays(0, new Date(2026, 0, 5))).toBe("2026-01-05")
  })
  it("counts pages, at least one", () => {
    expect(pageCount(0)).toBe(1); expect(pageCount(25)).toBe(1); expect(pageCount(26)).toBe(2)
  })
  it("summarises calls", () => {
    expect(contactSummary({ contactAttempts: 0 })).toBe("")
    expect(contactSummary({ contactAttempts: 1, lastContactOutcome: "NO_ANSWER" })).toBe("1 call · last: No answer")
    expect(contactSummary({ contactAttempts: 3, lastContactOutcome: "REACHED" })).toBe("3 calls · last: Spoke to patient")
  })
  it("checks the snooze date", () => {
    const today = "2026-10-08"
    expect(snoozeProblem("", today)).toMatch(/date/)
    expect(snoozeProblem("2026-10-08", today)).toMatch(/after today/)
    expect(snoozeProblem("2026-10-09", today)).toBeNull()
    expect(snoozeProblem("2099-01-01", today)).toMatch(/180/)
  })
})
