import { describe, it, expect } from "vitest"
import { emptyMove, emptyResource, emptyStatus, historyLine, moveProblem, resourceBody, resourceProblem, statusProblem, type ResourceEvent } from "./resourceRules"

const ok = () => ({ ...emptyResource(), siteId: "s1", name: "Welding rig", resourceType: "EQUIPMENT" })

describe("resourceProblem", () => {
  it("accepts a complete resource", () => expect(resourceProblem(ok())).toBeNull())
  it("checks each field", () => {
    expect(resourceProblem({ ...ok(), siteId: "" })).toBe("Choose the site")
    expect(resourceProblem({ ...ok(), name: "W" })).toBe("Give the resource a name")
    expect(resourceProblem({ ...ok(), resourceType: "" })).toBe("Choose the type of resource")
    expect(resourceProblem({ ...ok(), quantity: "0" })).toContain("Quantity")
    expect(resourceProblem({ ...ok(), quantity: "1.5" })).toContain("Quantity")
  })
  it("sends blanks as null and the quantity as a number", () => {
    const b = resourceBody({ ...ok(), quantity: "3", identifier: " WR-1 " })
    expect(b.quantity).toBe(3); expect(b.identifier).toBe("WR-1"); expect(b.venueId).toBeNull(); expect(b.notes).toBeNull()
  })
})
describe("statusProblem and moveProblem", () => {
  it("needs a reason and a day that is not in the future", () => {
    expect(statusProblem(emptyStatus("UNDER_REPAIR", "2020-03-10"), "2020-03-10")).toContain("at least 10")
    expect(statusProblem({ ...emptyStatus("UNDER_REPAIR", "2020-03-11"), reason: "Hydraulic leak on the arm" }, "2020-03-10")).toBe("The change cannot be dated in the future")
    expect(statusProblem({ ...emptyStatus("UNDER_REPAIR", "2020-03-10"), reason: "Hydraulic leak on the arm" }, "2020-03-10")).toBeNull()
  })
  it("will not move to the same site", () => {
    expect(moveProblem({ ...emptyMove("2020-03-10"), siteId: "s1", reason: "Needed for the new cohort" }, { siteId: "s1" }, "2020-03-10")).toBe("It is already at that site")
    expect(moveProblem({ ...emptyMove("2020-03-10"), siteId: "s2", reason: "Needed for the new cohort" }, { siteId: "s1" }, "2020-03-10")).toBeNull()
  })
})
describe("history wording", () => {
  it("reads a state change and a move", () => {
    const base = { id: "e", createdAt: "", occurredOn: "2020-03-10", fromSiteName: null, toSiteName: null, fromStatus: null, toStatus: null, note: "Hydraulic leak" }
    expect(historyLine({ ...base, eventType: "STATUS", fromStatus: "IN_SERVICE", toStatus: "UNDER_REPAIR" } as ResourceEvent)).toBe("In service to Under repair: Hydraulic leak")
    expect(historyLine({ ...base, eventType: "MOVED", fromSiteName: "Main", toSiteName: "North" } as ResourceEvent)).toBe("Main to North: Hydraulic leak")
  })
})
