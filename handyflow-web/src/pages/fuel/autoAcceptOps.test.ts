import { describe, it, expect } from "vitest"
import { asAutoAccept, autoAcceptProblem, autoAcceptSummary, AUTO_OFF } from "./autoAcceptOps"

describe("autoAcceptOps", () => {
  it("is off for anything that is not a real setting", () => {
    expect(asAutoAccept([])).toEqual(AUTO_OFF)
    expect(asAutoAccept(null)).toEqual(AUTO_OFF)
  })
  it("needs a limit above zero only when gauges are on", () => {
    expect(autoAcceptProblem(false, "")).toBeNull()
    expect(autoAcceptProblem(true, "")).not.toBeNull()
    expect(autoAcceptProblem(true, "0")).not.toBeNull()
    expect(autoAcceptProblem(true, "abc")).not.toBeNull()
    expect(autoAcceptProblem(true, "250")).toBeNull()
  })
  it("says in words what is on", () => {
    expect(autoAcceptSummary(AUTO_OFF)).toBe("Off: every import waits for a person")
    expect(autoAcceptSummary({ shiftsEnabled: true, gaugesEnabled: true, gaugeMaxDifferenceLitres: 300 })).toBe("Accepted automatically: shifts and gauge readings within 300 L of the book level")
  })
})
