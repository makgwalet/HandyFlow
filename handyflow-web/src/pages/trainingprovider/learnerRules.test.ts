import { describe, it, expect } from "vitest"
import {
  appTransitionBody, appTransitionProblem, applicationBody, applicationFormProblem, coverNote, emptyAppTransition, emptyApplication, emptyEnrol, emptyLearner,
  enrolBody, enrolFormProblem, enrolmentTransitionProblem, learnerBody, learnerProblem, luhnValid, reasonProblem,
} from "./learnerRules"

const today = "2026-11-25"
function withCheck(first12: string): string {
  for (let d = 0; d <= 9; d++) if (luhnValid(first12 + d)) return first12 + d
  throw new Error("none")
}
const goodId = withCheck("900101500008")
const ok = { ...emptyLearner, firstNames: "Test", surname: "Learner", idType: "PASSPORT", idNumber: "A1234567" }

describe("luhn", () => {
  it("accepts a valid number and rejects a changed digit", () => {
    expect(luhnValid(goodId)).toBe(true)
    const last = goodId[12] === "9" ? "0" : String(Number(goodId[12]) + 1)
    expect(luhnValid(goodId.slice(0, 12) + last)).toBe(false)
    expect(luhnValid("12a4")).toBe(false)
  })
})

describe("learner checks", () => {
  it("accepts a plain learner", () => { expect(learnerProblem(ok, today)).toBeNull() })
  it("needs names", () => { expect(learnerProblem({ ...ok, surname: "S" }, today)).toContain("surname") })
  it("a South African ID needs 13 digits and a check digit", () => {
    expect(learnerProblem({ ...ok, idType: "SA_ID", idNumber: "123" }, today)).toContain("13 digits")
    expect(learnerProblem({ ...ok, idType: "SA_ID", idNumber: "1234567890123" }, today)).toContain("check digit")
    expect(learnerProblem({ ...ok, idType: "SA_ID", idNumber: goodId }, today)).toBeNull()
  })
  it("date of birth must agree with the ID", () => {
    expect(learnerProblem({ ...ok, idType: "SA_ID", idNumber: goodId, dateOfBirth: "1990-01-01" }, today)).toBeNull()
    expect(learnerProblem({ ...ok, idType: "SA_ID", idNumber: goodId, dateOfBirth: "1990-02-01" }, today)).toContain("does not match")
  })
  it("no document means no number", () => {
    expect(learnerProblem({ ...ok, idType: "NONE", idNumber: "123" }, today)).toContain("blank")
    expect(learnerProblem({ ...ok, idType: "NONE", idNumber: "" }, today)).toBeNull()
    expect(learnerBody({ ...ok, idType: "NONE", idNumber: "" }).idNumber).toBeNull()
  })
  it("consent needs its date and the date needs consent", () => {
    expect(learnerProblem({ ...ok, consentGiven: true }, today)).toContain("date")
    expect(learnerProblem({ ...ok, consentOn: today }, today)).toContain("without consent")
    expect(learnerProblem({ ...ok, consentGiven: true, consentOn: "2026-12-01" }, today)).toContain("future")
    expect(learnerProblem({ ...ok, consentGiven: true, consentOn: today }, today)).toBeNull()
  })
  it("checks the email shape and a future birth date", () => {
    expect(learnerProblem({ ...ok, email: "nope" }, today)).toContain("email")
    expect(learnerProblem({ ...ok, dateOfBirth: "2027-01-01" }, today)).toContain("future")
  })
  it("drops the consent date when consent is not given", () => {
    expect(learnerBody({ ...ok, consentGiven: false, consentOn: "" }).consentOn).toBeNull()
    expect(learnerBody({ ...ok, firstNames: " Test ", consentGiven: true, consentOn: today }).firstNames).toBe("Test")
  })
})

describe("application checks", () => {
  it("needs a programme", () => {
    expect(applicationFormProblem(emptyApplication)).toContain("programme")
    expect(applicationBody("l1", { ...emptyApplication, programmeId: "p1" })).toMatchObject({ learnerId: "l1", programmeId: "p1", siteId: null })
  })
  it("a decline needs a reason", () => {
    expect(appTransitionProblem(emptyAppTransition("DECLINED"), ["DECLINED"])).toContain("at least 10")
    expect(appTransitionProblem({ ...emptyAppTransition("DECLINED"), reason: "Does not meet the entry" }, ["DECLINED"])).toBeNull()
  })
  it("accepting needs a person's entry check", () => {
    const f = emptyAppTransition("ACCEPTED")
    expect(appTransitionProblem(f, ["ACCEPTED"])).toContain("meets the entry")
    expect(appTransitionProblem({ ...f, entryMet: "yes", entryNote: "short" }, ["ACCEPTED"])).toContain("what was checked")
    expect(appTransitionProblem({ ...f, entryMet: "yes", entryNote: "Certificate sighted and copied" }, ["ACCEPTED"])).toBeNull()
  })
  it("accepting when entry is not met needs a written reason", () => {
    const f = { ...emptyAppTransition("ACCEPTED"), entryMet: "no" as const, entryNote: "Certificate sighted and copied" }
    expect(appTransitionProblem(f, ["ACCEPTED"])).toContain("not met")
    expect(appTransitionProblem({ ...f, overrideReason: "Recognised through experience" }, ["ACCEPTED"])).toBeNull()
  })
  it("sends the entry answer as a boolean and keeps the override only when entry is not met", () => {
    const b = appTransitionBody({ ...emptyAppTransition("ACCEPTED"), entryMet: "yes", entryNote: "Certificate sighted", overrideReason: "ignored" })
    expect(b).toMatchObject({ entryRequirementsMet: true, overrideReason: null })
    expect(appTransitionBody(emptyAppTransition("UNDER_REVIEW")).entryRequirementsMet).toBeNull()
  })
  it("refuses a status that is not offered", () => {
    expect(appTransitionProblem(emptyAppTransition("ACCEPTED"), ["DECLINED"])).toContain("not allowed")
  })
})

describe("enrolment checks", () => {
  it("needs programme, site and start date, and sane dates", () => {
    expect(enrolFormProblem(emptyEnrol)).toContain("programme")
    expect(enrolFormProblem({ ...emptyEnrol, programmeId: "p" })).toContain("site")
    expect(enrolFormProblem({ ...emptyEnrol, programmeId: "p", siteId: "s" })).toContain("start date")
    expect(enrolFormProblem({ ...emptyEnrol, programmeId: "p", siteId: "s", startDate: "2027-02-01", expectedEndDate: "2027-01-01" })).toContain("before")
    expect(enrolFormProblem({ ...emptyEnrol, programmeId: "p", siteId: "s", startDate: "2027-02-01" })).toBeNull()
    expect(enrolBody("l1", { ...emptyEnrol, programmeId: "p", siteId: "s", startDate: "2027-02-01" }, "a1")).toMatchObject({ applicationId: "a1", overrideReason: null })
  })
  it("holds, withdrawals and cancellations need a reason", () => {
    for (const s of ["ON_HOLD", "WITHDRAWN", "CANCELLED"]) {
      expect(enrolmentTransitionProblem(s, "short", [s])).toContain("at least 10")
      expect(enrolmentTransitionProblem(s, "Moved to the next intake", [s])).toBeNull()
    }
    expect(enrolmentTransitionProblem("IN_PROGRESS", "", ["IN_PROGRESS"])).toBeNull()
    expect(enrolmentTransitionProblem("IN_PROGRESS", "", ["ON_HOLD"])).toContain("not allowed")
    expect(reasonProblem("short")).toContain("at least 10")
  })
})

describe("what the enrolment says about accreditation", () => {
  it("says so plainly when covered, uncovered or not accredited", () => {
    expect(coverNote({ trainingActivity: "NON_ACCREDITED", coverOutcome: null, coverCheckedOn: null, coverOverrideReason: null })).toBe("Not an accredited programme")
    expect(coverNote({ trainingActivity: "ACCREDITED", coverOutcome: "COVERED", coverCheckedOn: "2026-11-25", coverOverrideReason: null })).toBe("Covered when enrolled on 2026-11-25")
    const n = coverNote({ trainingActivity: "ACCREDITED", coverOutcome: "EXPIRED", coverCheckedOn: "2026-11-25", coverOverrideReason: "Board approved in writing" })
    expect(n).toContain("Accreditation expired when enrolled")
    expect(n).toContain("Enrolled by an administrator: Board approved in writing")
  })
})
