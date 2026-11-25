import { describe, it, expect } from "vitest"
import { documentLine, emptyHandOver, handOverProblem, issuableTypes, standingWarning, voidProblem } from "./documentRules"

describe("document rules", () => {
  it("offers a letter only when a completed outcome stands and none is live", () => {
    expect(issuableTypes([], false)).toEqual(["PARTICIPATION_STATEMENT"])
    expect(issuableTypes([], true)).toEqual(["COMPLETION_LETTER", "PARTICIPATION_STATEMENT"])
    expect(issuableTypes([{ docType: "COMPLETION_LETTER", status: "ISSUED" }], true)).toEqual(["PARTICIPATION_STATEMENT"])
    expect(issuableTypes([{ docType: "COMPLETION_LETTER", status: "VOID" }], true)).toContain("COMPLETION_LETTER")
  })
  it("checks the hand-over", () => {
    const f = { ...emptyHandOver("2027-06-10"), handedOverTo: "The learner", method: "IN_PERSON" }
    expect(handOverProblem(f, "2027-06-01", "2027-06-10")).toBeNull()
    expect(handOverProblem({ ...f, handedOverOn: "2027-06-11" }, "2027-06-01", "2027-06-10")).toMatch(/future/)
    expect(handOverProblem({ ...f, handedOverOn: "2027-05-31" }, "2027-06-01", "2027-06-10")).toMatch(/before/)
    expect(handOverProblem({ ...f, handedOverTo: " " }, "2027-06-01", "2027-06-10")).toMatch(/who/)
    expect(handOverProblem({ ...f, method: "" }, "2027-06-01", "2027-06-10")).toMatch(/how/)
  })
  it("needs a written void reason", () => {
    expect(voidProblem("short")).not.toBeNull()
    expect(voidProblem("Issued to the wrong enrolment")).toBeNull()
  })
  it("describes where a document stands and warns when its completion was withdrawn", () => {
    const base = { status: "ISSUED", issuedOn: "2027-06-01", handedOverOn: null, handedOverTo: null, handedOverMethod: null, voidReason: null }
    expect(documentLine(base)).toMatch(/Not yet handed over/)
    expect(documentLine({ ...base, handedOverOn: "2027-06-02", handedOverTo: "Thandi", handedOverMethod: "EMAIL" })).toMatch(/Handed over to Thandi/)
    expect(documentLine({ ...base, status: "VOID", voidReason: "Wrong name" })).toMatch(/Voided/)
    expect(standingWarning({ docType: "COMPLETION_LETTER", status: "ISSUED", completionStillStands: false })).toMatch(/withdrawn/)
    expect(standingWarning({ docType: "PARTICIPATION_STATEMENT", status: "ISSUED", completionStillStands: false })).toBeNull()
  })
})
