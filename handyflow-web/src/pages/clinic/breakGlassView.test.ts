import { describe, it, expect } from "vitest"
import { activity, headline, isReviewed, needsCloserLook, waiting, type BreakGlassRow } from "./breakGlassView"

const row = (o: Partial<BreakGlassRow> = {}): BreakGlassRow => ({ id: "1", patientId: "p", userId: "u", reason: "Emergency", startedAt: "2026-10-13T08:00:00Z", expiresAt: "2026-10-13T09:00:00Z", itemsViewed: 0, documents: 0, ...o })

describe("breakGlassView", () => {
  it("counts those waiting for a reviewer", () => { expect(waiting([row(), row({ acknowledgedAt: "2026-10-13T10:00:00Z" }), row()])).toBe(2) })
  it("says what was done during the access", () => {
    expect(activity(row())).toBe("Nothing opened after the record itself")
    expect(activity(row({ itemsViewed: 3 }))).toBe("3 viewed")
    expect(activity(row({ itemsViewed: 3, documents: 1 }))).toBe("3 viewed · 1 document printed or exported")
    expect(activity(row({ documents: 2 }))).toBe("2 documents printed or exported")
  })
  it("marks documents taken out of the record for a closer look", () => { expect(needsCloserLook(row({ documents: 1 }))).toBe(true); expect(needsCloserLook(row({ itemsViewed: 9 }))).toBe(false) })
  it("knows what has been reviewed", () => { expect(isReviewed(row())).toBe(false); expect(isReviewed(row({ acknowledgedAt: "x" }))).toBe(true) })
  it("writes the headline", () => {
    expect(headline([])).toBe("Nothing is waiting for review.")
    expect(headline([row()])).toBe("1 break-glass access is waiting for review.")
    expect(headline([row(), row()])).toBe("2 break-glass accesses are waiting for review.")
  })
})
