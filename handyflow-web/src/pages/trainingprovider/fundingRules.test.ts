import { describe, it, expect } from "vitest"
import { FUNDING_NOTE, emptyFunder, emptyLink, endProblem, funderBody, funderProblem, linkBody, linkLine, linkProblem } from "./fundingRules"

const today = "2027-06-10"

describe("funding rules", () => {
  it("checks a funder", () => {
    const f = { ...emptyFunder, name: "Acme Stores", funderType: "EMPLOYER" }
    expect(funderProblem(f)).toBeNull()
    expect(funderProblem({ ...f, funderType: "" })).toMatch(/kind/)
    expect(funderProblem({ ...f, name: "A" })).toMatch(/name/)
    expect(funderProblem({ ...f, contactEmail: "nope" })).toMatch(/email/)
    expect(funderProblem({ ...f, contactEmail: "hr@acme.co.za" })).toBeNull()
    expect(funderBody(f)).toMatchObject({ clientId: null, contactEmail: null, note: null, name: "Acme Stores" })
  })
  it("checks a link", () => {
    const f = { ...emptyLink(today), funderId: "f1" }
    expect(linkProblem(f, "2027-06-01", [], today)).toBeNull()
    expect(linkProblem({ ...f, funderId: "" }, null, [], today)).toMatch(/Choose the funder/)
    expect(linkProblem({ ...f, fundedFrom: "" }, null, [], today)).toMatch(/starts/)
    expect(linkProblem({ ...f, fundedFrom: "2029-01-01" }, null, [], today)).toMatch(/ahead/)
    expect(linkProblem({ ...f, fundedFrom: "2025-01-01" }, "2027-06-01", [], today)).toMatch(/before the enrolment/)
    expect(linkProblem(f, null, ["f1"], today)).toMatch(/already linked/)
    expect(linkBody("e1", f)).toEqual({ enrolmentId: "e1", funderId: "f1", fundedFrom: today, agreementRef: null, funderReference: null, note: null })
  })
  it("needs a day and a reason to end a link", () => {
    expect(endProblem("", "2027-06-01", "Employer withdrew sponsorship", today)).toMatch(/Enter/)
    expect(endProblem("2027-06-11", "2027-06-01", "Employer withdrew sponsorship", today)).toMatch(/future/)
    expect(endProblem("2027-05-01", "2027-06-01", "Employer withdrew sponsorship", today)).toMatch(/before it started/)
    expect(endProblem(today, "2027-06-01", "short", today)).toMatch(/10 characters/)
    expect(endProblem(today, "2027-06-01", "Employer withdrew sponsorship", today)).toBeNull()
  })
  it("describes a link and never claims approval or payment", () => {
    expect(linkLine({ fundedFrom: "2027-06-01", agreementRef: "AGR-1", funderReference: null, status: "ACTIVE", endedOn: null, endReason: null })).toBe("From 2027-06-01 · agreement AGR-1")
    expect(linkLine({ fundedFrom: "2027-06-01", agreementRef: null, funderReference: "F-9", status: "ENDED", endedOn: "2027-07-01", endReason: "Sponsor stopped" })).toMatch(/ended 2027-07-01/)
    expect(FUNDING_NOTE).toMatch(/does not mean any funding was approved or paid/)
    expect(FUNDING_NOTE.toLowerCase()).not.toContain("eligible")
  })
})
