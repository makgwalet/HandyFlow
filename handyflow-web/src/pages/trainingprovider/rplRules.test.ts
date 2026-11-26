import { describe, it, expect } from "vitest"
import { applicationBody, applicationProblem, confirmProblem, decisionBody, decisionProblem, emptyApplication, emptyDecision, emptyEvidence, evidenceBody, evidenceProblem, reviewProblem, RPL_NOTE } from "./rplRules"

const today = "2027-06-10"
const facts = { liveEvidence: 2, unreviewedEvidence: 0, acceptedEvidence: 1, appliedOn: "2027-06-01" }
const dec = { ...emptyDecision(today), outcome: "RECOGNISED", basis: "Evidence matches the outcomes", assessorId: "x1" }

describe("rpl rules", () => {
  it("checks an application", () => {
    const f = { ...emptyApplication(today), claim: "Ten years running stores" }
    expect(applicationProblem(f, "2027-06-01", today)).toBeNull()
    expect(applicationProblem({ ...f, claim: "short" }, null, today)).toMatch(/10 characters/)
    expect(applicationProblem({ ...f, appliedOn: "2027-06-11" }, null, today)).toMatch(/future/)
    expect(applicationProblem({ ...f, appliedOn: "2027-05-01" }, "2027-06-01", today)).toMatch(/before the enrolment/)
    expect(applicationBody("e1", f)).toEqual({ enrolmentId: "e1", moduleId: null, claim: "Ten years running stores", appliedOn: today })
  })
  it("checks evidence and its review", () => {
    const f = { ...emptyEvidence(today), kind: "DOCUMENT", description: "Reference letter from employer" }
    expect(evidenceProblem(f, "2027-06-01", today)).toBeNull()
    expect(evidenceProblem({ ...f, kind: "" }, "2027-06-01", today)).toMatch(/kind/)
    expect(evidenceProblem({ ...f, description: "short" }, "2027-06-01", today)).toMatch(/10 characters/)
    expect(evidenceProblem({ ...f, receivedOn: "2027-05-31" }, "2027-06-01", today)).toMatch(/before the application/)
    expect(evidenceBody(f).sourceRef).toBeNull()
    expect(reviewProblem("", "Checked with the employer", today, "2027-06-01", today)).toMatch(/accepted or queried/)
    expect(reviewProblem("ACCEPTED", "short", today, "2027-06-01", today)).toMatch(/10 characters/)
    expect(reviewProblem("ACCEPTED", "Checked with the employer", "2027-05-01", "2027-06-01", today)).toMatch(/before the evidence/)
    expect(reviewProblem("QUERIED", "Checked with the employer", today, "2027-06-01", today)).toBeNull()
  })
  it("will not decide without reviewed evidence, and recognition needs accepted evidence", () => {
    expect(decisionProblem(dec, facts, today)).toBeNull()
    expect(decisionProblem(dec, { ...facts, liveEvidence: 0 }, today)).toMatch(/no evidence/)
    expect(decisionProblem(dec, { ...facts, unreviewedEvidence: 1 }, today)).toMatch(/1 piece of evidence has not been reviewed/)
    expect(decisionProblem(dec, { ...facts, acceptedEvidence: 0 }, today)).toMatch(/accepted evidence/)
    expect(decisionProblem({ ...dec, outcome: "NOT_RECOGNISED" }, { ...facts, acceptedEvidence: 0 }, today)).toBeNull()
    expect(decisionProblem({ ...dec, basis: "short" }, facts, today)).toMatch(/basis/)
    expect(decisionProblem({ ...dec, assessorId: "" }, facts, today)).toMatch(/Choose the RPL assessor/)
    expect(decisionProblem(dec, facts, today, false)).toMatch(/no login/)
    expect(decisionProblem({ ...dec, recordedForAssessor: "Signed sheet handed over" }, facts, today, false)).toBeNull()
    expect(decisionBody({ ...dec, recordedForAssessor: " " }).recordedForAssessor).toBeNull()
    expect(decisionProblem({ ...dec, decidedOn: "2027-05-31" }, facts, today)).toMatch(/before the application/)
  })
  it("needs a written reason to confirm alone", () => {
    expect(decisionProblem({ ...dec, confirmAlone: true, singleReason: "short" }, facts, today)).toMatch(/without a second person/)
    const alone = { ...dec, confirmAlone: true, singleReason: "Only one qualified assessor on site" }
    expect(decisionProblem(alone, facts, today)).toBeNull()
    expect(decisionBody(alone).singleReason).toBe("Only one qualified assessor on site")
    expect(decisionBody(dec).singleReason).toBeNull()
    expect(confirmProblem("", null, today)).toMatch(/Enter/)
    expect(confirmProblem("2027-06-01", "2027-06-05", today)).toMatch(/before it was decided/)
    expect(confirmProblem(today, "2027-06-05", today)).toBeNull()
  })
  it("never says the learner is competent or certified", () => {
    expect(RPL_NOTE).toMatch(/not a completion and not a certificate/)
    expect(RPL_NOTE.toLowerCase()).not.toContain("competent")
  })
})
