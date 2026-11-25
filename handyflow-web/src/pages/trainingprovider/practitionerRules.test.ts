import { describe, it, expect } from "vitest"
import {
  appointmentBody, appointmentProblem, checkNote, emptyAppointment, emptyPerson, emptyVerify, personBody, personProblem, reasonProblem, standingTone, verifyBody, verifyProblem,
} from "./practitionerRules"

const today = "2026-11-25"
describe("person", () => {
  it("needs names and a sensible email", () => {
    expect(personProblem(emptyPerson)).toContain("names")
    expect(personProblem({ ...emptyPerson, firstNames: "Test", surname: "Person", email: "nope" })).toContain("email")
    expect(personProblem({ ...emptyPerson, firstNames: "Test", surname: "Person" })).toBeNull()
  })
  it("sends blanks as null", () => {
    expect(personBody({ ...emptyPerson, firstNames: " Test ", surname: "Person" })).toMatchObject({ firstNames: "Test", email: null, phone: null, notes: null })
  })
})
describe("appointment", () => {
  it("needs a programme and ordered dates", () => {
    expect(appointmentProblem(emptyAppointment)).toContain("programme")
    expect(appointmentProblem({ ...emptyAppointment, programmeId: "p", validFrom: "2026-06-01", validTo: "2026-01-01" })).toContain("end date")
    expect(appointmentProblem({ ...emptyAppointment, programmeId: "p" })).toBeNull()
  })
  it("sends empty optional fields as null", () => {
    expect(appointmentBody({ ...emptyAppointment, programmeId: "p" })).toMatchObject({ authorityId: null, credentialRef: null, validFrom: null })
  })
})
describe("check", () => {
  const v = { ...emptyVerify(today), source: "Authority register entry" }
  it("needs a source and a date that is not in the future", () => {
    expect(verifyProblem("FACILITATOR", null, { ...v, source: "abc" }, today)).toContain("what you checked")
    expect(verifyProblem("FACILITATOR", null, { ...v, on: "2026-12-01" }, today)).toContain("future")
    expect(verifyProblem("FACILITATOR", null, v, today)).toBeNull()
  })
  it("an assessor or moderator needs a credential reference", () => {
    expect(verifyProblem("ASSESSOR", " ", v, today)).toContain("credential reference")
    expect(verifyProblem("MODERATOR", "REF-1", v, today)).toBeNull()
  })
  it("doing it without a second person needs a written reason, which is only sent then", () => {
    const solo = { ...v, noSecondPerson: true }
    expect(verifyProblem("ASSESSOR", "REF-1", solo, today)).toContain("second person")
    expect(verifyBody({ ...solo, overrideReason: "Only person with register access" }).overrideReason).toBe("Only person with register access")
    expect(verifyBody({ ...v, overrideReason: "left over text" }).overrideReason).toBeNull()
  })
  it("never says a person is registered or competent", () => {
    expect(checkNote({ verifiedOn: null, verificationSource: null, credentialRef: "REF-1" })).toContain("Nobody has recorded")
    const n = checkNote({ verifiedOn: today, verificationSource: "Register entry", credentialRef: "REF-1" })
    expect(n).toContain("A person recorded a check")
    expect(n.toLowerCase()).not.toContain("competent")
  })
})
describe("misc", () => {
  it("reasons and tones", () => {
    expect(reasonProblem("short")).not.toBeNull()
    expect(reasonProblem("Left the company")).toBeNull()
    expect(standingTone("VERIFIED_IN_FORCE")).toBe("good")
    expect(standingTone("EXPIRED")).toBe("bad")
    expect(standingTone("NOT_VERIFIED")).toBe("warn")
  })
})
