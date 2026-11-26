import { describe, expect, it } from "vitest"
import { linkBody, linkChangeProblem, mergeDecisionBody, mergeDecisionProblem, mergeProposalBody, mergeProposalProblem, reasonText } from "./matchRules"

describe("match wording", () => {
  it("turns reason codes into plain words", () => {
    expect(reasonText(["ID_NUMBER", "PHONE"])).toBe("same identity number, same phone number")
  })
})

describe("merge proposal", () => {
  const ok = { keepId: "a", reason: "Same passport on both records", conflictsAccepted: false }
  it("needs a record to keep and a reason", () => {
    expect(mergeProposalProblem({ ...ok, keepId: "" }, [])).toMatch(/which record/)
    expect(mergeProposalProblem({ ...ok, reason: "short" }, [])).toMatch(/Say why/)
    expect(mergeProposalProblem(ok, [])).toBeNull()
  })
  it("needs the disagreement accepted", () => {
    expect(mergeProposalProblem(ok, ["The dates of birth differ"])).toMatch(/disagree/)
    expect(mergeProposalProblem({ ...ok, conflictsAccepted: true }, ["The dates of birth differ"])).toBeNull()
  })
  it("merges the other record into the one kept", () => {
    expect(mergeProposalBody("a", "b", ok)).toMatchObject({ survivorId: "a", mergedId: "b" })
    expect(mergeProposalBody("a", "b", { ...ok, keepId: "b" })).toMatchObject({ survivorId: "b", mergedId: "a" })
  })
})

describe("merge decision", () => {
  const f = { reason: "Checked both records", alone: false, aloneReason: "" }
  it("lets a second person decide with a reason", () => {
    expect(mergeDecisionProblem(f, "p", "q")).toBeNull()
    expect(mergeDecisionProblem({ ...f, reason: "no" }, "p", "q")).toMatch(/Say why/)
  })
  it("makes the proposer say why they decide alone", () => {
    expect(mergeDecisionProblem(f, "p", "p")).toMatch(/second person/)
    expect(mergeDecisionProblem({ ...f, alone: true }, "p", "p")).toMatch(/no second person/)
    expect(mergeDecisionProblem({ ...f, alone: true, aloneReason: "Nobody else is in today" }, "p", "p")).toBeNull()
  })
  it("sends the lone reason only when deciding alone", () => {
    expect(mergeDecisionBody(f).singleReason).toBeNull()
    expect(mergeDecisionBody({ ...f, alone: true, aloneReason: " Nobody else is in " }).singleReason).toBe("Nobody else is in")
  })
})

describe("delegate link", () => {
  it("needs a delegate for a first link and a reason to change one", () => {
    expect(linkChangeProblem(false, "", "")).toMatch(/Choose a delegate/)
    expect(linkChangeProblem(false, "", "d1")).toBeNull()
    expect(linkChangeProblem(true, "short", "")).toMatch(/Say why/)
    expect(linkChangeProblem(true, "Linked to the wrong person", "")).toBeNull()
  })
  it("sends null to remove the link", () => {
    expect(linkBody("", " Wrong record entered ")).toEqual({ delegateId: null, reason: "Wrong record entered" })
  })
})
