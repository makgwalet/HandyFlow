import { describe, it, expect } from "vitest"
import { LEARNERSHIP_NOTE, agreementBody, agreementProblem, emptyAgreement, endProblem, latestSigned, registrationProblem, signProblem } from "./learnershipRules"

const today = "2027-06-10"

describe("learnership rules", () => {
  it("checks an agreement", () => {
    const f = { ...emptyAgreement, employerName: "Acme Stores", startDate: "2027-06-01" }
    expect(agreementProblem(f, "2027-06-01")).toBeNull()
    expect(agreementProblem({ ...f, employerName: "A" }, null)).toMatch(/employer/)
    expect(agreementProblem({ ...f, startDate: "" }, null)).toMatch(/start day/)
    expect(agreementProblem({ ...f, endDate: "2027-05-01" }, null)).toMatch(/end day/)
    expect(agreementProblem({ ...f, startDate: "2025-01-01" }, "2027-06-01")).toMatch(/more than a year/)
    expect(agreementBody(f)).toEqual({ clientId: null, employerName: "Acme Stores", employerContact: null, startDate: "2027-06-01", endDate: null })
  })
  it("needs all three signing days and none in the future", () => {
    expect(signProblem({ learnerSignedOn: "2027-06-01", employerSignedOn: "", providerSignedOn: "2027-06-01" }, today)).toMatch(/each of the learner/)
    expect(signProblem({ learnerSignedOn: "2027-06-01", employerSignedOn: "2027-06-01", providerSignedOn: "2027-06-11" }, today)).toMatch(/future/)
    expect(signProblem({ learnerSignedOn: "2027-06-01", employerSignedOn: "2027-06-02", providerSignedOn: "2027-06-03" }, today)).toBeNull()
  })
  it("takes a registration reference exactly as entered", () => {
    expect(registrationProblem("R", today, today)).toMatch(/exactly as it appears/)
    expect(registrationProblem("REG/123", "", today)).toMatch(/Enter the day/)
    expect(registrationProblem("REG/123", "2027-06-11", today)).toMatch(/future/)
    expect(registrationProblem("REG/123", today, today)).toBeNull()
  })
  it("needs a day and reason to end", () => {
    const signed = latestSigned({ learnerSignedOn: "2027-06-01", employerSignedOn: "2027-06-03", providerSignedOn: "2027-06-02" })
    expect(signed).toBe("2027-06-03")
    expect(endProblem("2027-06-02", signed, "Learner completed the term", today)).toMatch(/before it was signed/)
    expect(endProblem(today, signed, "short", today)).toMatch(/10 characters/)
    expect(endProblem(today, signed, "Learner completed the term", today)).toBeNull()
  })
  it("never claims registration or approval", () => {
    expect(LEARNERSHIP_NOTE).toMatch(/does not confirm that the learnership is registered or approved/)
  })
})
