import { describe, it, expect } from "vitest"
import { completionLine, emptyOutcome, needsOverride, outcomeBody, outcomeProblem, outcomeTone, readinessSummary, stateTone, withdrawProblem } from "./completionRules"

const today = "2027-06-01"
const ok = { ...emptyOutcome(today), outcome: "COMPLETED", basis: "All required assessments were completed and moderated" }

describe("outcome form", () => {
  it("needs an outcome, a sensible day and a basis", () => {
    expect(outcomeProblem(emptyOutcome(today), 0, today, null)).toContain("Choose")
    expect(outcomeProblem({ ...ok, outcomeOn: "2027-06-02" }, 0, today, null)).toContain("future")
    expect(outcomeProblem({ ...ok, outcomeOn: "2027-01-01" }, 0, today, "2027-02-01")).toContain("before the enrolment")
    expect(outcomeProblem({ ...ok, basis: "short" }, 0, today, null)).toContain("basis")
    expect(outcomeProblem(ok, 0, today, "2027-02-01")).toBeNull()
  })
  it("needs a written reason to complete with open items, but not to record did not complete", () => {
    expect(needsOverride(ok, 2)).toBe(true)
    expect(needsOverride(ok, 0)).toBe(false)
    expect(needsOverride({ outcome: "DID_NOT_COMPLETE" }, 3)).toBe(false)
    expect(outcomeProblem(ok, 2, today, null)).toContain("2 assessments are not yet competent")
    expect(outcomeProblem(ok, 1, today, null)).toContain("1 assessment is")
    expect(outcomeProblem({ ...ok, overrideReason: "short" }, 1, today, null)).toContain("say why")
    expect(outcomeProblem({ ...ok, overrideReason: "Remaining task waived by the programme lead" }, 1, today, null)).toBeNull()
    expect(outcomeProblem({ ...ok, outcome: "DID_NOT_COMPLETE" }, 3, today, null)).toBeNull()
  })
  it("sends the reason only when it is needed", () => {
    expect(outcomeBody("e1", { ...ok, overrideReason: "Remaining task waived by the programme lead" }, 0).overrideReason).toBeNull()
    expect(outcomeBody("e1", { ...ok, overrideReason: " Remaining task waived by the programme lead " }, 2).overrideReason).toBe("Remaining task waived by the programme lead")
  })
  it("needs a reason to withdraw", () => {
    expect(withdrawProblem("short")).toContain("10")
    expect(withdrawProblem("Recorded against the wrong enrolment")).toBeNull()
  })
})
describe("wording", () => {
  it("summarises readiness without calling anyone complete", () => {
    expect(readinessSummary({ rows: [], openGaps: 0 })).toContain("nothing to check")
    expect(readinessSummary({ rows: [{} as never], openGaps: 0 })).toContain("competent")
    expect(readinessSummary({ rows: [{} as never, {} as never], openGaps: 1 })).toBe("1 of 2 instruments is not yet competent and moderated.")
    expect(readinessSummary({ rows: [{} as never, {} as never, {} as never], openGaps: 2 })).toBe("2 of 3 instruments are not yet competent and moderated.")
  })
  it("tones states and outcomes", () => {
    expect(stateTone("COMPETENT")).toBe("good")
    expect(stateTone("NOT_YET_COMPETENT")).toBe("bad")
    expect(stateTone("WAITING_MODERATION")).toBe("warn")
    expect(outcomeTone("COMPLETED", "RECORDED")).toBe("good")
    expect(outcomeTone("COMPLETED", "WITHDRAWN")).toBe("neutral")
  })
  it("keeps the reason on the line when completed with items open", () => {
    expect(completionLine({ outcome: "COMPLETED", outcomeOn: "2027-06-01", status: "RECORDED", openGaps: 2, gapsOverrideReason: "Task waived by the lead" })).toContain("2 open at the time")
    expect(completionLine({ outcome: "DID_NOT_COMPLETE", outcomeOn: "2027-06-01", status: "WITHDRAWN", openGaps: 0, gapsOverrideReason: null })).toContain("withdrawn")
  })
})
