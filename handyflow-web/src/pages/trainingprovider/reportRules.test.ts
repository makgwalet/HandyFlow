import { describe, it, expect } from "vitest"
import {
  acknowledgeProblem, computedDue, emptyObligation, emptyPeriod, obligationBody, obligationProblem, periodBody, periodLine, periodProblem, stateTone, submitProblem,
} from "./reportRules"

const ob = { ...emptyObligation, authorityId: "a1", name: "Quarterly report", dueDays: "30", sourceNote: "Taken from the letter of 3 May" }

describe("report rules", () => {
  it("needs a source note and sane settings for an obligation", () => {
    expect(obligationProblem(ob, true)).toBeNull()
    expect(obligationProblem({ ...ob, authorityId: "" }, true)).toMatch(/authority/)
    expect(obligationProblem({ ...ob, authorityId: "" }, false)).toBeNull()
    expect(obligationProblem({ ...ob, dueDays: "" }, true)).toMatch(/0 to 366/)
    expect(obligationProblem({ ...ob, dueDays: "400" }, true)).toMatch(/0 to 366/)
    expect(obligationProblem({ ...ob, sourceNote: "short" }, true)).toMatch(/where this rule came from/)
    expect(obligationBody({ ...ob, programmeId: "" })).toMatchObject({ programmeId: null, dueDaysAfterEnd: 30 })
  })
  it("computes the due day and asks for a reason to change it", () => {
    expect(computedDue("2027-03-31", 30)).toBe("2027-04-30")
    expect(computedDue("2027-12-15", 30)).toBe("2028-01-14")
    const p = { ...emptyPeriod, obligationId: "o1", periodStart: "2027-01-01", periodEnd: "2027-03-31" }
    expect(periodProblem(p, 30)).toBeNull()
    expect(periodProblem({ ...p, dueOn: "2027-04-30" }, 30)).toBeNull()
    expect(periodProblem({ ...p, dueOn: "2027-05-31" }, 30)).toMatch(/differs/)
    expect(periodProblem({ ...p, dueOn: "2027-05-31", overrideReason: "Authority extended the deadline" }, 30)).toBeNull()
    expect(periodProblem({ ...p, periodEnd: "2026-12-31" }, 30)).toMatch(/cannot end before/)
  })
  it("sends the due day only when it differs from the rule", () => {
    const p = { ...emptyPeriod, obligationId: "o1", periodStart: "2027-01-01", periodEnd: "2027-03-31" }
    expect(periodBody({ ...p, dueOn: "2027-04-30" }, 30)).toMatchObject({ dueOn: null, dueOverrideReason: null })
    expect(periodBody({ ...p, dueOn: "2027-05-31", overrideReason: " Authority extended the deadline " }, 30)).toMatchObject({ dueOn: "2027-05-31", dueOverrideReason: "Authority extended the deadline" })
  })
  it("checks submit and acknowledge days", () => {
    expect(submitProblem("2027-06-10", "2027-01-01", "2027-06-10")).toBeNull()
    expect(submitProblem("2027-06-11", "2027-01-01", "2027-06-10")).toMatch(/future/)
    expect(submitProblem("2026-12-31", "2027-01-01", "2027-06-10")).toMatch(/before the period/)
    expect(acknowledgeProblem("2027-06-09", "2027-06-10", "2027-06-10")).toMatch(/before it was submitted/)
    expect(acknowledgeProblem("2027-06-10", "2027-06-10", "2027-06-10")).toBeNull()
  })
  it("describes a period without claiming acceptance or compliance", () => {
    const base = { status: "SUBMITTED", dueOn: "2027-04-30", submittedOn: "2027-04-20", submissionRef: "SUB-1", acknowledgedOn: null, acknowledgementRef: null, waivedReason: null, voidReason: null }
    expect(periodLine(base)).toMatch(/Recorded by staff as submitted/)
    expect(periodLine(base)).toMatch(/No acknowledgement recorded yet/)
    expect(periodLine({ ...base, acknowledgedOn: "2027-04-25", acknowledgementRef: "ACK-1" })).toMatch(/Acknowledged by the authority on 2027-04-25 \(ACK-1\)/)
    expect(periodLine({ ...base, status: "OPEN" })).toBe("Due 2027-04-30")
    expect(stateTone("OVERDUE")).toBe("bad")
  })
})
