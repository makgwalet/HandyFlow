import { describe, it, expect } from "vitest"
import { validSaId, partyProblem, ownershipNotes, holdsPercentage, roleLabel } from "./partyRules"

const draft = { fullName: "Thandi Nkosi", role: "DIRECTOR", idType: "SA_ID", idNumber: "", ownershipPercent: "", appointedOn: "", resignedOn: "" }

describe("validSaId", () => {
  it("accepts a correct number", () => expect(validSaId("8001015009087")).toBe(true))
  it("ignores spaces", () => expect(validSaId("800101 5009 087")).toBe(true))
  it("refuses a wrong check digit, date or length", () => {
    expect(validSaId("8001015009088")).toBe(false)
    expect(validSaId("8013015009087")).toBe(false)
    expect(validSaId("80010150")).toBe(false)
  })
})

describe("partyProblem", () => {
  it("accepts a plain director", () => expect(partyProblem(draft)).toBeNull())
  it("needs a name and a role", () => {
    expect(partyProblem({ ...draft, fullName: " " })).toMatch(/name/)
    expect(partyProblem({ ...draft, role: "" })).toMatch(/role/)
  })
  it("checks a South African ID only when one is entered", () => {
    expect(partyProblem({ ...draft, idNumber: "1234567890123" })).toMatch(/not valid/)
    expect(partyProblem({ ...draft, idNumber: "8001015009087" })).toBeNull()
    expect(partyProblem({ ...draft, idType: "PASSPORT", idNumber: "A1234567" })).toBeNull()
  })
  it("keeps percentages to owners and between 0 and 100", () => {
    expect(partyProblem({ ...draft, ownershipPercent: "10" })).toMatch(/Only owners/)
    expect(partyProblem({ ...draft, role: "SHAREHOLDER", ownershipPercent: "10" })).toBeNull()
    expect(partyProblem({ ...draft, role: "SHAREHOLDER", ownershipPercent: "120" })).toMatch(/0 and 100/)
    expect(partyProblem({ ...draft, role: "SHAREHOLDER", ownershipPercent: "abc" })).toMatch(/0 and 100/)
  })
  it("will not let resignation come before appointment", () => {
    expect(partyProblem({ ...draft, appointedOn: "2026-02-01", resignedOn: "2026-01-01" })).toMatch(/before/)
  })
})

describe("ownershipNotes", () => {
  const ok = { shareholdingDeclared: 100, fullyDeclared: true, hasDirector: true, hasBeneficialOwner: true, beneficialOwnerGap: false, directors: 1 }
  it("says nothing when all is in order", () => expect(ownershipNotes(ok)).toEqual([]))
  it("points out each gap", () => {
    const notes = ownershipNotes({ ...ok, hasDirector: false, shareholdingDeclared: 60, fullyDeclared: false, beneficialOwnerGap: true })
    expect(notes).toHaveLength(3)
    expect(notes[1]).toMatch(/60%/)
  })
  it("does not complain about shares when none are recorded", () => {
    expect(ownershipNotes({ ...ok, shareholdingDeclared: 0, fullyDeclared: false })).toEqual([])
  })
})

describe("labels", () => {
  it("knows which roles hold a percentage", () => {
    expect(holdsPercentage("SHAREHOLDER")).toBe(true)
    expect(holdsPercentage("DIRECTOR")).toBe(false)
    expect(roleLabel("BENEFICIAL_OWNER")).toBe("Beneficial owner")
  })
})
