import { describe, it, expect } from "vitest"
import { COVER, emptyPermission, periodText, permissionBody, permissionProblem, stateText, withdrawProblem } from "./sitePermissionRules"

const ok = () => ({ ...emptyPermission("2020-03-01"), practitionerId: "p1", siteId: "s1", basis: "Placed at this site for the new intake" })

describe("permissionProblem", () => {
  it("accepts a complete permission", () => expect(permissionProblem(ok())).toBeNull())
  it("checks each field", () => {
    expect(permissionProblem({ ...ok(), practitionerId: "" })).toBe("Choose the person")
    expect(permissionProblem({ ...ok(), siteId: "" })).toBe("Choose the site")
    expect(permissionProblem({ ...ok(), validFrom: "" })).toBe("Give the day the permission starts")
    expect(permissionProblem({ ...ok(), validTo: "2020-02-01" })).toBe("The end date cannot be before the start date")
    expect(permissionProblem({ ...ok(), basis: "short" })).toContain("at least 10")
  })
  it("sends blanks as null", () => {
    const b = permissionBody(ok())
    expect(b.programmeId).toBeNull(); expect(b.validTo).toBeNull()
  })
})
describe("wording", () => {
  it("needs a reason to withdraw", () => {
    expect(withdrawProblem("short")).toContain("at least 10")
    expect(withdrawProblem("Moved to the northern branch")).toBeNull()
  })
  it("describes the period and the state", () => {
    expect(periodText({ validFrom: "2020-03-01", validTo: null })).toBe("from 2020-03-01, no end date")
    expect(periodText({ validFrom: "2020-03-01", validTo: "2020-09-01" })).toBe("2020-03-01 to 2020-09-01")
    expect(stateText({ status: "ACTIVE", inForceToday: true, validFrom: "2020-03-01" }, "2020-03-05")).toBe("In force")
    expect(stateText({ status: "ACTIVE", inForceToday: false, validFrom: "2020-04-01" }, "2020-03-05")).toBe("Not started")
    expect(stateText({ status: "ACTIVE", inForceToday: false, validFrom: "2020-01-01" }, "2020-03-05")).toBe("Ended")
    expect(stateText({ status: "WITHDRAWN", inForceToday: false, validFrom: "2020-01-01" }, "2020-03-05")).toBe("Withdrawn")
  })
  it("never calls a missing record a finding", () => expect(COVER.NOT_RECORDED).toBe("No site permission recorded for this person at this site"))
})
