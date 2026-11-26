import { describe, it, expect } from "vitest"
import { emptyNeed, needBody, needLine, needProblem, readinessTone, withdrawProblem } from "./programmeNeedsRules"

const ok = () => ({ ...emptyNeed(), resourceType: "EQUIPMENT", description: "Arc welding machine", sourceNote: "Listed in the programme's own curriculum" })

describe("needProblem", () => {
  it("accepts a complete need", () => expect(needProblem(ok())).toBeNull())
  it("checks each field", () => {
    expect(needProblem({ ...ok(), resourceType: "" })).toBe("Choose the type of resource")
    expect(needProblem({ ...ok(), description: "A" })).toBe("Say what is needed")
    expect(needProblem({ ...ok(), matchText: "w" })).toContain("at least two")
    expect(needProblem({ ...ok(), quantity: "0" })).toBe("The quantity must be at least 1")
    expect(needProblem({ ...ok(), perLearners: "0" })).toBe("Learners per set must be at least 1")
    expect(needProblem({ ...ok(), sourceNote: "short" })).toContain("at least 10")
  })
  it("sends blanks as null and numbers as numbers", () => {
    expect(needBody({ ...ok(), quantity: "2", perLearners: "4", matchText: " weld " })).toEqual({ resourceType: "EQUIPMENT", description: "Arc welding machine", matchText: "weld", quantity: 2, perLearners: 4, sourceNote: "Listed in the programme's own curriculum" })
    expect(needBody(ok()).perLearners).toBeNull()
    expect(needBody(ok()).matchText).toBeNull()
  })
})
describe("wording", () => {
  it("describes a need in plain words", () => {
    expect(needLine({ quantity: 2, perLearners: 4, description: "Arc welding machine", resourceType: "EQUIPMENT", matchText: null })).toBe("2 × Arc welding machine (Equipment) for every 4 learners")
    expect(needLine({ quantity: 1, perLearners: null, description: "Simulator", resourceType: "SIMULATOR", matchText: "sim" })).toContain("for the cohort, looking for \"sim\"")
  })
  it("needs a reason to withdraw and never calls a gap a failure", () => {
    expect(withdrawProblem("short")).toContain("at least 10")
    expect(withdrawProblem("Replaced by a new line")).toBeNull()
    expect(readinessTone("SHORT")).toBe("warn")
  })
})
