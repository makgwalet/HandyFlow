import { describe, it, expect } from "vitest"
import {
  attemptBody, attemptProblem, attemptStateText, attemptTone, correctBody, correctProblem, decisionProblem, emptyAttempt, emptyDecision, emptyModeration, markProblem, markText,
  moderationProblem, moderationText, parseMark, reasonProblem, resolveProblem,
} from "./attemptRules"

const today = "2027-03-10"
const ok = { ...emptyAttempt(today), memberId: "m", instrumentId: "i", assessorId: "a", mark: "30" }

describe("mark", () => {
  it("parses blanks and numbers", () => {
    expect(parseMark("")).toBeNull()
    expect(parseMark(" 12.5 ")).toBe(12.5)
    expect(parseMark("x")).toBeUndefined()
  })
  it("follows the instrument scale", () => {
    expect(markProblem("30", 50)).toBeNull()
    expect(markProblem("0", 50)).toBeNull()
    expect(markProblem("51", 50)).toContain("maximum")
    expect(markProblem("-1", 50)).toContain("below 0")
    expect(markProblem("", 50)).toContain("Enter the mark")
    expect(markProblem("abc", 50)).toContain("number")
    expect(markProblem("", null)).toBeNull()
    expect(markProblem("5", null)).toContain("not marked")
  })
  it("says how a mark reads", () => {
    expect(markText({ mark: 30, maxMark: 50 })).toBe("30 out of 50")
    expect(markText({ mark: null, maxMark: null })).toBe("Not marked")
  })
})
describe("attempt form", () => {
  it("needs a learner, instrument, assessor and a day that is not in the future", () => {
    expect(attemptProblem(emptyAttempt(today), 50, today)).toContain("learner")
    expect(attemptProblem({ ...ok, instrumentId: "" }, 50, today)).toContain("instrument")
    expect(attemptProblem({ ...ok, assessorId: "" }, 50, today)).toContain("assessor")
    expect(attemptProblem({ ...ok, assessedOn: "2027-03-11" }, 50, today)).toContain("future")
    expect(attemptProblem(ok, 50, today)).toBeNull()
  })
  it("sends a number for the mark and null for blanks", () => {
    expect(attemptBody(ok)).toMatchObject({ mark: 30, notes: null })
    expect(attemptBody({ ...ok, mark: "" }).mark).toBeNull()
  })
})
describe("correction", () => {
  it("needs a different valid mark and a reason", () => {
    expect(correctProblem({ mark: "30", reason: "Added up wrongly the first time" }, 50, 30)).toContain("already")
    expect(correctProblem({ mark: "35", reason: "short" }, 50, 30)).toContain("10")
    expect(correctProblem({ mark: "60", reason: "Added up wrongly the first time" }, 50, 30)).toContain("maximum")
    expect(correctProblem({ mark: "35", reason: "Added up wrongly the first time" }, 50, 30)).toBeNull()
    expect(correctBody({ mark: "35", reason: " Added up wrongly the first time " })).toEqual({ mark: 35, reason: "Added up wrongly the first time" })
  })
})
describe("decision", () => {
  it("needs a choice and a basis", () => {
    expect(decisionProblem(emptyDecision)).toContain("Choose")
    expect(decisionProblem({ decision: "COMPETENT", basis: "short" })).toContain("basis")
    expect(decisionProblem({ decision: "PASS", basis: "Met every criterion on the checklist" })).toContain("Choose")
    expect(decisionProblem({ decision: "NOT_YET_COMPETENT", basis: "Missed two critical steps" })).toBeNull()
  })
})
describe("moderation", () => {
  const f = { ...emptyModeration(today), moderatorId: "p", outcome: "CONFIRMED", basis: "Sampled the evidence fully" }
  it("needs a moderator, a sensible day, an outcome and a basis", () => {
    expect(moderationProblem(emptyModeration(today), today, null)).toContain("moderator")
    expect(moderationProblem({ ...f, moderatedOn: "2027-03-11" }, today, null)).toContain("future")
    expect(moderationProblem({ ...f, moderatedOn: "2027-03-01" }, today, "2027-03-05")).toContain("before")
    expect(moderationProblem({ ...f, outcome: "" }, today, null)).toContain("confirmed or queried")
    expect(moderationProblem({ ...f, basis: "short" }, today, null)).toContain("basis")
    expect(moderationProblem(f, today, "2027-03-05")).toBeNull()
  })
  it("needs a written answer to a query", () => {
    expect(resolveProblem("short")).toContain("10")
    expect(resolveProblem("Attempt voided and the learner reassessed")).toBeNull()
    expect(reasonProblem("short")).toContain("10")
  })
})
describe("wording", () => {
  const a = { status: "RECORDED", decision: null }
  it("shows an undecided attempt as waiting, not as a result", () => {
    expect(attemptStateText(a)).toBe("Waiting for a decision")
    expect(attemptTone(a)).toBe("warn")
    expect(attemptStateText({ status: "RECORDED", decision: { decision: "COMPETENT", basis: "x", decidedBy: null, decidedOn: "d" } })).toBe("Competent")
    expect(attemptStateText({ status: "VOID", decision: null })).toBe("Void")
  })
  it("says where moderation stands", () => {
    expect(moderationText({ outcome: "CONFIRMED", resolvedAt: null })).toContain("Confirmed")
    expect(moderationText({ outcome: "QUERIED", resolvedAt: null })).toContain("waiting")
    expect(moderationText({ outcome: "QUERIED", resolvedAt: "2027-03-12" })).toContain("answered")
  })
})
