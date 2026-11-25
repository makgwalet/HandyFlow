import { describe, it, expect } from "vitest"
import { urlProblem, changeProblem, sourceProblem, confirmBlock, noteProblem, ordered, impactsOrdered, progress, blankForm, needsEffectiveDate, statusLabel, impactLabel, type Change, type Impact } from "./regulatoryRules"

const change = (o: Partial<Change> = {}): Change => ({ id: "x", sourceId: "s", sourceName: "Gazette", title: "T", changeType: "AMENDED", status: "DETECTED", origin: "MANUAL", publicationDate: null, effectiveDate: null, raisedAt: "2026-11-01T00:00:00Z", linkedObligations: 0, impactsOpen: 0, impactsTotal: 0, actions: [], ...o })
const impact = (o: Partial<Impact> = {}): Impact => ({ id: "i", changeId: "x", changeTitle: "B", clientId: "c", clientName: "Acme", obligationCode: "X", status: "OPEN", dueDate: null, note: null, handledAt: null, noticeGivenAt: null, actions: [], ...o })

describe("regulatoryRules", () => {
  it("accepts only http and https addresses, and an empty one", () => {
    expect(urlProblem("")).toBeNull()
    expect(urlProblem("https://example.org/a")).toBeNull()
    expect(urlProblem("javascript:alert(1)")).not.toBeNull()
    expect(urlProblem("https://a b.org")).not.toBeNull()
  })
  it("checks a change form", () => {
    const f = { ...blankForm(), sourceId: "s", title: "Fee change" }
    expect(changeProblem(blankForm(), "2026-11-25")).toMatch(/source/)
    expect(changeProblem({ ...f, title: " " }, "2026-11-25")).toMatch(/title/)
    expect(changeProblem({ ...f, publicationDate: "2026-12-01" }, "2026-11-25")).toMatch(/future/)
    expect(changeProblem({ ...f, referenceUrl: "ftp://x" }, "2026-11-25")).not.toBeNull()
    expect(changeProblem(f, "2026-11-25")).toBeNull()
  })
  it("checks a source", () => {
    expect(sourceProblem(" ", "")).toMatch(/name/)
    expect(sourceProblem("Gazette", "nope")).not.toBeNull()
    expect(sourceProblem("Gazette", "")).toBeNull()
  })
  it("blocks confirming a dated type without a date, but not a proposal or guidance", () => {
    expect(confirmBlock({ changeType: "AMENDED", effectiveDate: null })).toMatch(/takes effect/)
    expect(confirmBlock({ changeType: "AMENDED", effectiveDate: "2027-01-01" })).toBeNull()
    expect(confirmBlock({ changeType: "PROPOSED", effectiveDate: null })).toBeNull()
    expect(needsEffectiveDate("GUIDANCE")).toBe(false)
  })
  it("wants a note of 15 characters", () => {
    expect(noteProblem("too short")).not.toBeNull()
    expect(noteProblem("   fifteen chars!!   ")).toBeNull()
  })
  it("puts changes awaiting a decision first", () => {
    const rows = ordered([change({ id: "d", status: "DISMISSED" }), change({ id: "c", status: "CONFIRMED" }), change({ id: "f", status: "DETECTED", effectiveDate: "2027-05-01" }), change({ id: "r", status: "IN_REVIEW" }), change({ id: "g", status: "DETECTED", effectiveDate: "2027-02-01" })])
    expect(rows.map(r => r.id)).toEqual(["r", "g", "f", "c", "d"])
  })
  it("puts open impacts first, earliest date first", () => {
    const rows = impactsOrdered([impact({ id: "done", status: "DONE" }), impact({ id: "late", dueDate: "2027-03-01" }), impact({ id: "soon", status: "NOTICE_GIVEN", dueDate: "2027-01-01" })])
    expect(rows.map(r => r.id)).toEqual(["soon", "late", "done"])
  })
  it("words progress and statuses", () => {
    expect(progress({ impactsOpen: 0, impactsTotal: 0 })).toBe("No clients affected")
    expect(progress({ impactsOpen: 2, impactsTotal: 5 })).toBe("3 of 5 clients dealt with")
    expect(statusLabel("IN_REVIEW")).toBe("In review")
    expect(impactLabel("NOTICE_GIVEN")).toBe("Client told")
  })
})
